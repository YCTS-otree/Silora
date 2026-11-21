package com.silora.app

import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

class ChatPagingChecks:Instrumentation(){
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){
        try {
            check(targetContext.packageName.endsWith(".devicecheck"))
            fun u(i:Int)=JSONObject().put("role","user").put("content","课程$i")
            fun a(i:Int)=JSONObject().put("role","assistant").put("content",JSONObject().put("summary","教室$i").toString())
            val legacy=JSONArray().put(u(1)).put(a(1))
            targetContext.getSharedPreferences("conversation",Context.MODE_PRIVATE).edit().putString("history",legacy.toString()).commit()
            val db=ChatHistory(targetContext)
            for(i in 2..5)db.append(u(i),a(i),499999)
            check(db.window(0).messages.length()==10)
            db.append(u(6),a(6),500000)
            check(db.window(0).messages.length()==4 && db.window(0).digest.contains("教室1"))
            var cursor=Long.MAX_VALUE
            for(n in listOf(5,3,1)){
                val page=db.page(cursor);val rows=page.getJSONArray("turns")
                check(rows.length()==2 && rows.getJSONObject(0).getString("user")=="课程$n")
                check(page.getBoolean("more")==(n!=1))
                cursor=rows.getJSONObject(0).getLong("id")
            }
            db.clear();check(db.page(Long.MAX_VALUE).getJSONArray("turns").length()==0 && db.window(0).digest.isEmpty())
            finish(0,Bundle().apply{putString("stream","PASS migration, two-turn pages, half-window boundary, digest, archive, clear")})
        }catch(e:Throwable){finish(1,Bundle().apply{putString("stream","FAIL "+e.stackTraceToString())})}
    }
}
