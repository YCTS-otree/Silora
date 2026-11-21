package com.silora.app

import android.app.Instrumentation
import android.os.Bundle
import okio.Buffer
import org.json.JSONObject
import org.json.JSONArray

class ChatStreamChecks: Instrumentation(){
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){
        try {
            fun frame(key:String,value:String,stop:Boolean=false):String {
                val c=JSONObject().put("delta",JSONObject().put(key,value))
                if(stop)c.put("finish_reason","stop")
                return "data: "+JSONObject().put("choices",JSONArray().put(c))+"\n\n"
            }
            val wire=frame("reasoning_content","检查时间")+frame("content","{\"summary\":\"体育")+
                frame("content","课\\\"提醒\\\"\",\"status\":\"notice_ready\"}",true)
            var thought="";var final=""
            val result=ChatStream.read(Buffer().writeUtf8(wire+"data: [DONE]\n\n")){t,s->thought=t;final=s}
            check(thought=="检查时间" && final=="体育课\"提醒\"")
            check(!result.toString().contains(thought))
            check(runCatching{ChatStream.read(Buffer().writeUtf8(wire)){_,_->}}.isFailure)
            check(runCatching{ChatStream.read(Buffer().writeUtf8(frame("content","{}")+"data: [DONE]\n")){_,_->}}.isFailure)
            finish(0,Bundle().apply{putString("stream","PASS preview, escaped summary, complete answer, truncated and unfinished streams")})
        }catch(e:Throwable){finish(1,Bundle().apply{putString("stream","FAIL "+e.stackTraceToString())})}
    }
}
