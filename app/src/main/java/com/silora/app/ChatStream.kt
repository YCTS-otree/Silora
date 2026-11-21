package com.silora.app

import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject

/** Only final answer text enters the regular validation/history path. */
internal object ChatStream {
    fun read(source: BufferedSource, preview: (String,String)->Unit): JSONObject {
        val content=StringBuilder()
        var tail=""
        var finish=""
        var usage:JSONObject?=null
        var last=0L
        var total=0L
        var lastView=Pair("","")
        fun paint(force:Boolean=false) {
            val now=System.nanoTime()
            if(!force && now-last<80_000_000L)return
            last=now
            val view=tail to summary(content)
            if(view!=lastView){preview(view.first,view.second);lastView=view}
        }
        while(!source.exhausted()) {
            val line=source.readUtf8LineStrict(1_000_000)
            total+=line.length
            require(total<=32_000_000) { "流式响应过大" }
            if(!line.startsWith("data:"))continue
            val data=line.substring(5).trim()
            if(data=="[DONE]") {
                require(finish=="stop" && content.isNotBlank()) { "回复未完整生成，未应用方案" }
                paint(true)
                return JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason",finish)
                    .put("message",JSONObject().put("content",content.toString())))).put("usage",usage)
            }
            val chunk=try { JSONObject(data) } catch(_:Exception){throw IllegalStateException("流式响应格式异常")}
            check(!chunk.has("error")) { "模型中断了回复，未应用方案" }
            chunk.optJSONObject("usage")?.let { usage=it }
            val choice=chunk.optJSONArray("choices")?.optJSONObject(0) ?: continue
            if(!choice.isNull("finish_reason"))finish=choice.getString("finish_reason")
            val delta=choice.optJSONObject("delta") ?: continue
            (delta.opt("reasoning_content") as? String)?.let { tail=(tail+it).takeLast(320) }
            (delta.opt("content") as? String)?.let { content.append(it) }
            require(content.length<=2_000_000) { "回复过长" }
            paint()
        }
        throw IllegalStateException("连接提前结束，未应用方案")
    }
    // The prompt requests summary first. Decode only its quoted prefix, never arbitrary JSON.
    private val first=Regex("""^\s*\{\s*"summary"\s*:\s*"""")
    private fun summary(content:CharSequence):String {
        val prefix=content.subSequence(0,minOf(content.length,4096)).toString()
        val start=first.find(prefix)?.range?.last?.plus(1) ?: return ""
        val out=StringBuilder()
        var i=start
        while(i<prefix.length && out.length<1200) {
            val c=prefix[i++]
            if(c=='"')break
            if(c!='\\'){out.append(c);continue}
            if(i>=prefix.length)break
            when(val e=prefix[i++]) {
                'n','r','t' -> out.append(' ')
                '"','\\','/' -> out.append(e)
                'u' -> {if(i+4>prefix.length)break; val v=prefix.substring(i,i+4).toIntOrNull(16) ?: break;out.append(v.toChar());i+=4}
                else -> break
            }
        }
        return out.toString()
    }
}
