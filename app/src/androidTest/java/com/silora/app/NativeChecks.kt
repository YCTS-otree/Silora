package com.silora.app

import android.app.Instrumentation
import android.os.Bundle
import org.json.JSONObject

/** Framework instrumentation: exercises real Android JSON and SQLite, no mock database. */
class NativeChecks: Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments);start() }
    override fun onStart() {
        var count=0
        fun checkCase(test: ()->Unit) { test();count++ }
        fun invalid(test:()->Unit) { var failed=false;try{test()}catch(e:IllegalArgumentException){failed=true}catch(e:org.json.JSONException){failed=true};check(failed) }
        val interval=JSONObject("""{"id":"test","start":"2026-12-01T09:00:00+08:00","end":"2026-12-01T10:00:00+08:00","mode":"vibrate","label":"test"}""")
        fun doc()=JSONObject().put("schema_version",1).put("status","plan_ready").put("operation","add").put("time_zone","Asia/Shanghai").put("intervals",org.json.JSONArray().put(interval))
        try {
            checkCase { check(PlanJson.candidate(doc(),emptyList()).size==1) }
            checkCase { invalid { PlanJson.candidate(doc().put("schema_version",2),emptyList()) } }
            checkCase { invalid { PlanJson.candidate(doc().put("status","clarification_required"),emptyList()) } }
            checkCase { invalid { PlanJson.read(JSONObject(interval.toString()).put("mode","unknown")) } }
            checkCase { invalid { PlanJson.read(JSONObject(interval.toString()).also { it.remove("start") }) } }
            checkCase { invalid { PlanJson.candidate(doc(),listOf(PlanJson.read(interval))) } }
            checkCase { check(PlanJson.candidate(doc().put("intervals",org.json.JSONArray()),emptyList()).isEmpty()) }
            checkCase { invalid { JSONObject("{") } }
            checkCase {
                val p=PlanJson.read(interval)
                val replace=doc().put("operation","replace").put("scope",JSONObject().put("start",interval.getString("start")).put("end",interval.getString("end"))).put("intervals",org.json.JSONArray())
                check(PlanJson.candidate(replace,listOf(p)).isEmpty())
            }
            val directory=java.io.File(targetContext.cacheDir,"Temp").apply { mkdirs() }
            val store=PlanStore(targetContext,java.io.File(directory,"checks.db").absolutePath)
            checkCase { store.replace(listOf(PlanJson.read(interval)),store.revision());check(store.plans().size==1) }
            checkCase { invalid { store.replace(emptyList(),store.revision()-1) };check(store.plans().size==1) }
            checkCase { invalid { store.replace(listOf(PlanJson.read(interval),PlanJson.read(interval)),store.revision()) };check(store.plans().size==1) }
            store.close()
            Core.executor.submit {
                val core=Core.get(targetContext)
                check(core.store.plans().isEmpty()) { "Native integration checks require an empty plan list" }
                val audio=targetContext.getSystemService(android.media.AudioManager::class.java)
                val before=audio.ringerMode
                val restore=core.store.get("restore","previous");val manual=core.store.get("manual","respect")
                try {
                    core.store.put("restore","previous");core.store.put("manual","respect")
                    checkCase {
                        val now=System.currentTimeMillis()
                        core.store.replace(listOf(Plan("guard",now+60000,now+120000,1,"guard")),core.store.revision());core.reconcile("测试安排")
                        val token=core.store.get("alarm")
                        core.store.replace(emptyList(),core.store.revision());core.reconcile("测试删除")
                        core.reconcile("旧事件",token);core.reconcile("重复事件",token)
                        check(audio.ringerMode==before && core.store.plans().isEmpty())
                    }
                    checkCase {
                        val now=System.currentTimeMillis()
                        core.store.replace(listOf(Plan("manual",now-1000,now+120000,1,"manual")),core.store.revision());core.reconcile("测试正在区间内")
                        check(audio.ringerMode==1)
                        audio.ringerMode=0;core.reconcile("模拟外部手动修改")
                        check(audio.ringerMode==0 && core.store.state().suppressed)
                        core.store.replace(emptyList(),core.store.revision());core.reconcile("测试结束")
                        check(audio.ringerMode==0)
                    }
                } finally {
                    core.store.replace(emptyList(),core.store.revision());core.reconcile("测试清理")
                    core.store.put("restore",restore);core.store.put("manual",manual);audio.ringerMode=before
                }
            }.get()
            finish(0,Bundle().apply { putString("stream","PASS $count Android JSON / SQLite checks") })
        } catch(e:Throwable) { finish(1,Bundle().apply { putString("stream","FAIL after $count: ${e.javaClass.simpleName}: ${e.message}") }) }
    }
}
