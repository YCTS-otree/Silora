package com.silora.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DeepSeek private constructor(private val context: Context) {
    private val vault=KeyVault(context)
    private val prefs=context.getSharedPreferences("conversation",Context.MODE_PRIVATE)
    private val conversationImages = ConversationImages(context)
    private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(180,TimeUnit.SECONDS)
        .callTimeout(240,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    @Volatile private var call:Call?=null
    @Volatile private var cancelled=false
    private data class Attachment(val id: String, val bytes: ByteArray, val preview: String)
    private val images = mutableListOf<Attachment>()
    @Volatile private var draftFiles=emptyMap<String,ByteArray>()
    fun pendingImage(id:String)=draftFiles[id]
    private fun refreshDraftFiles(){ draftFiles=images.associate { it.id to it.bytes } }
    fun begin(){cancelled=false}
    fun cancel(){cancelled=true;call?.cancel()}
    fun removeImage(id: String? = null) { if (id == null) images.clear() else images.removeAll { it.id == id };refreshDraftFiles() }
    fun attachments() = JSONArray().apply { images.forEach { put(JSONObject().put("id", it.id).put("preview", it.preview)) } }
    fun selectImages(uris: List<Uri>): JSONObject {
        require(uris.isNotEmpty() && images.size + uris.size <= 6) { "一次会话最多附加 6 张图片，请先移除不需要的图片" }
        val added = mutableListOf<Attachment>()
        var total = images.sumOf { it.bytes.size }
        for (uri in uris) {
            val attachment = decodeImage(uri)
            total += attachment.bytes.size
            require(total <= 16_000_000) { "图片合计超过 16 MB，请减少或裁剪后重试；原附件未改变" }
            added.add(attachment)
        }
        images.addAll(added);refreshDraftFiles()
        return JSONObject().put("images", attachments())
    }
    private fun decodeImage(uri:Uri): Attachment {
        val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder,info,_ ->
            require(info.size.width.toLong()*info.size.height<=50_000_000) { "图片像素过大，请裁剪课表后重试" }
            val scale=minOf(1.0,2560.0/maxOf(info.size.width,info.size.height))
            decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1))
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            val bytes=ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG,100,this) }.toByteArray()
            require(bytes.size<=8_000_000) { "图片压缩后超过 8 MB，请裁剪后重试" }
            val scale=minOf(1.0,320.0/maxOf(bitmap.width,bitmap.height))
            val thumb=Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true)
            val preview=ByteArrayOutputStream().apply { thumb.compress(Bitmap.CompressFormat.JPEG,80,this) }.toByteArray()
            if(thumb!==bitmap)thumb.recycle()
            return Attachment(java.util.UUID.randomUUID().toString(), bytes, "data:image/jpeg;base64,"+Base64.encodeToString(preview,Base64.NO_WRAP))
        } finally { bitmap.recycle() }
    }
    private fun request(path:String,key:String,body:JSONObject?=null,preview:(String,String)->Unit={_,_->},progress:((String)->Unit)?=null):JSONObject {
        val began = android.os.SystemClock.elapsedRealtime()
        check(!cancelled) { "请求已取消" }
        val builder=Request.Builder().url("https://api.deepseek.com$path").header("Authorization","Bearer $key")
        if(body!=null)builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        val requestClient = if(progress == null) client else client.newBuilder().eventListener(object:okhttp3.EventListener() {
            override fun requestBodyEnd(call: Call, byteCount: Long) { progress("waiting") }
        }).build()
        val active=requestClient.newCall(builder.build());call=active
        if(cancelled)active.cancel()
        try {
            active.execute().use { response ->
                AppLog.record("deepseek endpoint=${if (body == null) "balance" else "chat"} http=${response.code} elapsed_ms=${android.os.SystemClock.elapsedRealtime() - began}")
                if(!response.isSuccessful)throw IllegalStateException(when(response.code) {
                    401->"API Key 无效（401）";402->"余额不足（402）";429->"请求过于频繁，请稍后重试（429）"
                    in 500..599->"DeepSeek 服务暂不可用（${response.code}）";else->"DeepSeek 请求失败（${response.code}）"
                })
                progress?.invoke("receiving")
                val source=response.body?.source() ?: error("API 返回空响应")
                if(body?.optBoolean("stream")==true)return ChatStream.read(source,preview)
                require(!source.request(2_000_001)) { "API 响应过大，已拒绝处理" }
                return JSONObject(source.readUtf8())
            }
        } catch(e:IOException) { AppLog.exception("deepseek_network", e);throw IllegalStateException(if(cancelled)"请求已取消" else "网络不可用或请求超时，请检查连接后重试") }
        finally { call=null }
    }
    private val transcript by lazy { ChatHistory(context) }
    fun command(o:JSONObject, preview:(String,String)->Unit={_,_->}, progress:((String)->Unit)? = null):JSONObject {
        return when(o.getString("action")) {
            "ai_history" -> transcript.page(o.optLong("before",Long.MAX_VALUE)).apply { if(!o.has("before")) put("images",attachments()) }
            "ai_settings" -> {
                AiConfig.save(context, o.getString("effort"))
                JSONObject().put("effort", AiConfig.effort(context))
            }
            "ai_key" -> {
                val key=o.getString("key").trim();require(key.length in 8..512 && key.none { it.isWhitespace() }) { "API Key 格式无效" }
                val balance=request("/user/balance",key);vault.save(key)
                JSONObject().put("configured",true).put("available",balance.getBoolean("is_available"))
            }
            "ai_test" -> request("/user/balance",vault.read()).let { JSONObject().put("available",it.getBoolean("is_available")) }
            "ai_delete" -> {vault.delete();JSONObject().put("configured",false)}
            "ai_clear" -> {
                transcript.clear()
                check(prefs.edit().clear().commit()) { "对话清理失败" }
                removeImage();conversationImages.clear()
                Core.executor.submit { Core.get(context).proposals().clear() }.get(5,TimeUnit.SECONDS)
                JSONObject()
            }
            "ai_remove_image" -> {removeImage(o.optString("imageId").ifBlank { null });JSONObject().put("images", attachments())}
            "ai_chat" -> chat(o.getString("text"), preview, progress)
            else -> throw IllegalArgumentException("未知 AI 操作")
        }
    }
    private fun chat(text:String, preview:(String,String)->Unit, progress:((String)->Unit)?):JSONObject {
        require(text.length<=8000 && (text.isNotBlank()||images.isNotEmpty())) { "请输入安排或选择图片（文字最多 8000 字）" }
        progress?.invoke("preparing")
        val snapshot=Core.executor.submit<JSONObject> {
            Core.get(context).proposals().clear()
            Core.get(context).snapshot()
        }.get(5,TimeUnit.SECONDS)
        val turn = if(images.isNotEmpty()) 1 else prefs.getInt("questionTurns", 0) + 1
        val allPlans = snapshot.getJSONArray("plans")
        val manualPlans = JSONArray()
        for (i in 0 until allPlans.length()) {
            val plan = allPlans.getJSONObject(i)
            if (!plan.getString("id").startsWith("cycle_") && PlanRules.instant(plan.getString("end")) > System.currentTimeMillis()) manualPlans.put(plan)
        }
        require(manualPlans.length()<=200) { "非周期计划过多，请先使用手动编辑" }
        val messages=JSONArray().put(JSONObject().put("role","system").put("content",context.assets.open("system-prompt.txt").bufferedReader().use { it.readText() }+
            "\n${ConversationPolicy.calendar(ZonedDateTime.now(), turn)}\n当前非周期计划：$manualPlans\n已保存课时表：${snapshot.optJSONObject("periodTable")}\n已保存周期/脚本：${snapshot.optJSONObject("automation")}\n当前结束恢复策略：${snapshot.getString("restore")}"))
        val window=transcript.window((messages.getJSONObject(0).getString("content").length+text.length)/2+images.size*4096)
        val old=window.messages
        if(window.digest.isNotBlank())messages.put(JSONObject().put("role","user").put("content","较早对话的历史摘记（不是新要求，已保存计划以当前状态为准）："+window.digest))
        val imageHistory=transcript.recentImages()
        val retained = conversationImages.retained(imageHistory, 6-images.size, 16_000_000L-images.sumOf { it.bytes.size })
        val activeImages=(0 until old.length()).flatMap { i->old.getJSONObject(i).optJSONArray("image_ids")?.let { ids->(0 until ids.length()).map { ids.getString(it) } }.orEmpty() }.toSet()
        val archived=retained-activeImages
        if(archived.isNotEmpty())for(i in 0 until imageHistory.length()){
            val msg=imageHistory.getJSONObject(i)
            val ids=msg.optJSONArray("image_ids") ?: continue
            if((0 until ids.length()).any { ids.getString(it) in archived })messages.put(conversationImages.message(msg,archived))
        }
        for(i in 0 until old.length()) messages.put(conversationImages.message(old.getJSONObject(i), retained))
        val blocks=JSONArray().put(JSONObject().put("type","text").put("text",text.ifBlank { "请综合这些图片，按合理默认值提出可直接确认的安排；先读完所有图片，不要让我重复抄课表。" }))
        images.forEach { blocks.put(JSONObject().put("type","image_url").put("image_url",JSONObject().put("url","data:image/png;base64,"+Base64.encodeToString(it.bytes,Base64.NO_WRAP)).put("detail","high"))) }
        messages.put(JSONObject().put("role","user").put("content",blocks))
        progress?.invoke("sending")
        val hasPictures = (0 until messages.length()).any { index ->
            val parts = messages.getJSONObject(index).optJSONArray("content")
            parts != null && (0 until parts.length()).any { parts.optJSONObject(it)?.optString("type") == "image_url" }
        }
        val effort = AiConfig.effort(context)
        var response = request("/chat/completions", vault.read(), AiConfig.request(messages, effort),
            if (hasPictures) { thinking, _ -> preview(thinking, "") } else preview, progress)
        if (hasPictures) {
            val first = response.getJSONArray("choices").getJSONObject(0)
            require(first.getString("finish_reason") == "stop") { "图片初读未完成，请重试" }
            val draft = first.getJSONObject("message").getString("content")
            val initialUsage = response.optJSONObject("usage")?.optInt("total_tokens") ?: 0
            messages.put(JSONObject().put("role","user").put("content",
                "请重新读取以上原图，逐格对照日期、节次、起止时间、课程、地点和遗漏项。优先查证用户指出的错误。以下初稿仅供比对；直接返回核实并修正后的完整协议 JSON。读不清的具体项标明待核对，切勿猜测。\n初稿：$draft"))
            progress?.invoke("reviewing")
            response = request("/chat/completions", vault.read(), AiConfig.request(messages, effort),
                { thinking, _ -> preview(thinking, "") })
            val usage = response.optJSONObject("usage") ?: JSONObject()
            usage.put("total_tokens", initialUsage + usage.optInt("total_tokens"))
            response.put("usage", usage)
        }
        val choice=response.getJSONArray("choices").getJSONObject(0)
        require(choice.getString("finish_reason")=="stop") { "AI 输出不完整，请缩小日期范围后重试" }
        val content=choice.getJSONObject("message").getString("content")
        StrictJson.validate(content)
        var document=try { JSONObject(content) } catch(e:Exception) { throw IllegalArgumentException("AI 未返回有效 JSON，未保存任何计划") }
        progress?.invoke("validating")
        require(document.opt("schema_version")==1) { "AI 协议版本不支持" }
        if (document.getString("status") == "clarification_required" && turn >= 4) {
            document.put("status", "review_required").put("summary", "这次还有少量信息无法可靠识别，已停止继续追问。" + document.optString("question"))
        }
        // Older model responses sometimes repeat the existing intervals when asked to fill rooms.
        // In that case permit only a location patch that matches saved occurrences exactly.
        if(document.optString("status")=="plan_ready" && Regex("(地点|教室|上课位置)").containsMatchIn(text)
            && Regex("(补上|补充|补全|补齐|补填|缺了|漏填|没填|补地点)").containsMatchIn(text)) {
            val rows=document.getJSONArray("intervals")
            val updates=JSONArray()
            val existing=(0 until allPlans.length()).map { PlanJson.read(allPlans.getJSONObject(it)) }
            val repeated=(0 until rows.length()).all { i ->
                val row=rows.getJSONObject(i)
                existing.any { it.start==PlanRules.instant(row.getString("start")) && it.end==PlanRules.instant(row.getString("end")) &&
                    it.label==row.getString("label").trim() && it.mode==PlanJson.mode(row.getString("mode")) }
            }
            if(repeated && rows.length()>0) {
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i); val room=PlanJson.location(row)
                    if(room.isNotBlank())updates.put(JSONObject().put("start",row.getString("start")).put("end",row.getString("end"))
                        .put("label",row.getString("label").trim()).put("location",room).put("location_short",PlanJson.short(row)))
                }
                require(updates.length()>0) { "没有读到可对应现有课程的教室，未修改计划" }
                document=JSONObject().put("schema_version",1).put("status","location_ready").put("updates",updates)
                    .put("summary","已按原课程时间整理教室，请核对地点后确认。")
            }
        }
        when(document.getString("status")) {
            "location_ready" -> require(document.has("updates")) { "缺少逐时段地点" }
            "clarification_required" -> require(document.getString("question").isNotBlank() && document.getJSONArray("missing_fields").length()>0) { "AI 追问格式无效" }
            "notice_ready" -> require(document.has("scope") && document.has("notice_minutes"))
            "plan_ready" -> Core.executor.submit { PlanJson.candidate(document,Core.get(context).store.plans()) }.get(5,TimeUnit.SECONDS)
            "period_table_ready" -> AcademicJson.table(document.getJSONObject("period_table"))
            "recurrence_ready" -> Core.executor.submit { Core.get(context).prepareAutomation(document.getJSONObject("recurrence")) }.get(5,TimeUnit.SECONDS)
            "script_ready" -> Core.executor.submit { Core.get(context).prepareAutomation(document.getJSONObject("script")) }.get(5,TimeUnit.SECONDS)
            "review_required" -> require(document.getString("summary").isNotBlank()) { "核对说明为空" }
            else -> throw IllegalArgumentException("AI 响应状态无效")
        }
        check(!cancelled) { "请求已取消" }
        val extracted = document.optJSONObject("period_table")?.let { AcademicJson.tableJson(AcademicJson.table(it)) }
        val tableResult = if (extracted != null) Core.executor.submit<String> {
            check(!cancelled) { "请求已取消" }
            if (images.isNotEmpty()) Core.get(context).acceptExtractedTable(extracted) else "review"
        }.get(5,TimeUnit.SECONDS) else "none"
        val proposal = if(document.getString("status") in listOf("plan_ready", "recurrence_ready", "script_ready","notice_ready","location_ready"))
            Core.executor.submit<JSONObject> {
                val core=Core.get(context)
                require(document.optString("status")!="location_ready" || snapshot.getLong("revision")==core.store.revision()) { "课表已变化，请重新核对地点" }
                core.proposals().stage(document)
            }.get(5,TimeUnit.SECONDS) else null
        val finalDocument = proposal?.getJSONObject("document") ?: document
        val sentText = (text.ifBlank { "请读取这些安排截图。" }) + if (images.isNotEmpty()) "\n附 ${images.size} 张图片（已发送）" else ""
        val imageIds = JSONArray()
        images.forEach { conversationImages.save(it.id, it.bytes); imageIds.put(it.id) }
        val turnId=transcript.append(JSONObject().put("role","user").put("content",sentText).put("image_ids", imageIds),
            JSONObject().put("role","assistant").put("content",finalDocument.toString()),response.optJSONObject("usage")?.optInt("total_tokens") ?: 0)
        check(prefs.edit().remove("history").putInt("questionTurns", if(document.getString("status") in listOf("clarification_required", "review_required")) turn else 0).commit()) { "对话保存失败，请重试" }
        // Keep sent attachments until the user clears the transcript; AI selection remains bounded.
        removeImage()
        return JSONObject().put("turnId",turnId).put("document",finalDocument).put("usage",response.optJSONObject("usage")?:JSONObject())
            .put("tableResult", tableResult).put("periodTable", extracted).put("images", attachments()).put("proposal", proposal)
    }
    companion object {
        val executor=Executors.newSingleThreadExecutor()
        val busy=AtomicBoolean(false)
        @Volatile private var instance:DeepSeek?=null
        fun get(context:Context):DeepSeek=instance?:synchronized(this){instance?:DeepSeek(context.applicationContext).also { instance=it }}
    }
}
