package com.silora.app

import android.app.Instrumentation
import android.os.Bundle
import org.json.JSONObject

class AcademicChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        var count=0
        val directory=java.io.File(targetContext.cacheDir,"Temp").apply { mkdirs() }
        val path=java.io.File(directory,"academic-checks.db").absolutePath
        var store=PlanStore(targetContext,path)
        val table=JSONObject("""{"name":"测试课时表","time_zone":"Asia/Shanghai","periods":[{"number":1,"start":"08:00","end":"08:45"},{"number":2,"start":"08:55","end":"09:40"}]}""")
        try {
            store.saveTable(table,store.get("periodVersion","0").toLong());count++
            store.close();store=PlanStore(targetContext,path)
            check(JSONObject(store.get("periodTable")).getJSONArray("periods").length()==2);count++
            val version=store.get("periodVersion","0").toLong()
            try{store.saveTable(table,version-1);error("stale write accepted")}catch(e:IllegalArgumentException){count++}
            val script=JSONObject("""{"name":"脚本","anchor_monday":"2026-11-16","valid_from":"2026-11-16","valid_until":"2026-12-13","time_zone":"Asia/Shanghai","source":"when weekday == 1 and week % 2 == 1 -> vibrate(\"08:00\", \"09:00\", \"测试\")"}""")
            val(rule,plans)=AcademicJson.automation(script)
            check(plans.size==2);count++
            store.replace(plans,store.revision(),mapOf("automation" to rule.toString()))
            store.close();store=PlanStore(targetContext,path)
            check(store.plans().size==2 && JSONObject(store.get("automation")).getString("kind")=="script");count++
            val before=store.get("automation")
            try{store.replace(plans+plans.first(),store.revision(),mapOf("automation" to "bad"));error("conflict accepted")}catch(e:IllegalArgumentException){check(store.get("automation")==before);count++}
            store.saveTable(null,store.get("periodVersion","0").toLong());check(store.get("periodTable").isEmpty()&&store.plans().size==2);count++
            finish(0,Bundle().apply{putString("stream","PASS $count academic SQLite / JSON checks")})
        } catch(e:Throwable){finish(1,Bundle().apply{putString("stream","FAIL after $count: ${e.javaClass.simpleName}: ${e.message}")})}
        finally{store.close()}
    }
}
