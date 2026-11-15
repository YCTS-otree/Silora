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
    private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(70,TimeUnit.SECONDS)
        .callTimeout(80,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    @Volatile private var call:Call?=null
    @Volatile private var cancelled=false
    private var image:ByteArray?=null
    fun begin(){cancelled=false}
    fun cancel(){cancelled=true;call?.cancel()}
    fun removeImage(){image=null}
    fun selectImage(uri:Uri): JSONObject {
        val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder,info,_ ->
            require(info.size.width.toLong()*info.size.height<=50_000_000) { "图片像素过大，请裁剪课表后重试" }
            val scale=minOf(1.0,2560.0/maxOf(info.size.width,info.size.height))
            decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1))
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            val bytes=ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG,100,this) }.toByteArray()
            require(bytes.size<=8_000_000) { "图片压缩后超过 8 MB，请裁剪后重试" }
            image=bytes
            val scale=minOf(1.0,320.0/maxOf(bitmap.width,bitmap.height))
            val thumb=Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true)
            val preview=ByteArrayOutputStream().apply { thumb.compress(Bitmap.CompressFormat.JPEG,80,this) }.toByteArray()
            if(thumb!==bitmap)thumb.recycle()
            return JSONObject().put("preview","data:image/jpeg;base64,"+Base64.encodeToString(preview,Base64.NO_WRAP))
        } finally { bitmap.recycle() }
    }
    private fun request(path:String,key:String,body:JSONObject?=null):JSONObject {
        check(!cancelled) { "请求已取消" }
        val builder=Request.Builder().url("https://api.deepseek.com$path").header("Authorization","Bearer $key")
        if(body!=null)builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        val active=client.newCall(builder.build());call=active
        if(cancelled)active.cancel()
        try {
            active.execute().use { response ->
                if(!response.isSuccessful)throw IllegalStateException(when(response.code) {
                    401->"API Key 无效（401）";402->"余额不足（402）";429->"请求过于频繁，请稍后重试（429）"
                    in 500..599->"DeepSeek 服务暂不可用（${response.code}）";else->"DeepSeek 请求失败（${response.code}）"
                })
                val source=response.body?.source() ?: error("API 返回空响应")
                require(!source.request(2_000_001)) { "API 响应过大，已拒绝处理" }
                return JSONObject(source.readUtf8())
            }
        } catch(e:IOException) { throw IllegalStateException(if(cancelled)"请求已取消" else "网络不可用或请求超时，请检查连接后重试") }
        finally { call=null }
    }
    private fun history():JSONArray = try { JSONArray(prefs.getString("history","[]")) } catch(e:Exception){JSONArray()}
    fun command(o:JSONObject):JSONObject {
        return when(o.getString("action")) {
            "ai_history" -> JSONObject().put("messages",history()).put("imageAttached",image!=null)
            "ai_key" -> {
                val key=o.getString("key").trim();require(key.length in 8..512 && key.none { it.isWhitespace() }) { "API Key 格式无效" }
                val balance=request("/user/balance",key);vault.save(key)
                JSONObject().put("configured",true).put("available",balance.getBoolean("is_available"))
            }
            "ai_test" -> request("/user/balance",vault.read()).let { JSONObject().put("available",it.getBoolean("is_available")) }
            "ai_delete" -> {vault.delete();JSONObject().put("configured",false)}
            "ai_clear" -> {prefs.edit().clear().commit();removeImage();JSONObject()}
            "ai_remove_image" -> {removeImage();JSONObject()}
            "ai_chat" -> chat(o.getString("text"))
            else -> throw IllegalArgumentException("未知 AI 操作")
        }
    }
    private fun chat(text:String):JSONObject {
        require(text.length<=8000 && (text.isNotBlank()||image!=null)) { "请输入安排或选择图片（文字最多 8000 字）" }
        val snapshot=Core.executor.submit<JSONObject> { Core.get(context).snapshot() }.get(5,TimeUnit.SECONDS)
        require(snapshot.getJSONArray("plans").length()<=200) { "当前计划过多，请先使用手动编辑" }
        val messages=JSONArray().put(JSONObject().put("role","system").put("content",context.assets.open("system-prompt.txt").bufferedReader().use { it.readText() }+"\n当前时间：${ZonedDateTime.now()}\n当前计划：${snapshot.getJSONArray("plans")}"))
        val old=history();for(i in maxOf(0,old.length()-10) until old.length())messages.put(old.getJSONObject(i))
        val blocks=JSONArray().put(JSONObject().put("type","text").put("text",text.ifBlank { "请读取图片中的安排，缺少信息请追问。" }))
        image?.let { blocks.put(JSONObject().put("type","image_url").put("image_url",JSONObject().put("url","data:image/png;base64,"+Base64.encodeToString(it,Base64.NO_WRAP)).put("detail","high"))) }
        messages.put(JSONObject().put("role","user").put("content",blocks))
        val response=request("/chat/completions",vault.read(),JSONObject().put("model","deepseek-flash").put("messages",messages)
            .put("thinking",JSONObject().put("type","disabled")).put("response_format",JSONObject().put("type","json_object")).put("max_tokens",4096).put("stream",false))
        val choice=response.getJSONArray("choices").getJSONObject(0)
        require(choice.getString("finish_reason")=="stop") { "AI 输出不完整，请缩小日期范围后重试" }
        val content=choice.getJSONObject("message").getString("content")
        StrictJson.validate(content)
        val document=try { JSONObject(content) } catch(e:Exception) { throw IllegalArgumentException("AI 未返回有效 JSON，未保存任何计划") }
        require(document.opt("schema_version")==1) { "AI 协议版本不支持" }
        when(document.getString("status")) {
            "clarification_required" -> require(document.getString("question").isNotBlank() && document.getJSONArray("missing_fields").length()>0) { "AI 追问格式无效" }
            "plan_ready" -> Core.executor.submit { PlanJson.candidate(document,Core.get(context).store.plans()) }.get(5,TimeUnit.SECONDS)
            else -> throw IllegalArgumentException("AI 响应状态无效")
        }
        check(!cancelled) { "请求已取消" }
        val saved=JSONArray();for(i in maxOf(0,old.length()-10) until old.length())saved.put(old.getJSONObject(i))
        saved.put(JSONObject().put("role","user").put("content",text)).put(JSONObject().put("role","assistant").put("content",content))
        prefs.edit().putString("history",saved.toString()).commit()
        return JSONObject().put("document",document).put("usage",response.optJSONObject("usage")?:JSONObject())
    }
    companion object {
        val executor=Executors.newSingleThreadExecutor()
        val busy=AtomicBoolean(false)
        @Volatile private var instance:DeepSeek?=null
        fun get(context:Context):DeepSeek=instance?:synchronized(this){instance?:DeepSeek(context.applicationContext).also { instance=it }}
    }
}
