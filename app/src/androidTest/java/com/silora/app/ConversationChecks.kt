package com.silora.app

import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ConversationChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val root = File(targetContext.cacheDir, "Temp/conversation-checks").apply { mkdirs() }
        val store = PlanStore(targetContext, File(root, "checks.db").absolutePath)
        try {
            store.replace(emptyList(), store.revision())
            val proposals = ProposalStore(store) { error("Unexpected cycle") }
            val table = JSONObject("""{"name":"冬季测试","time_zone":"Asia/Shanghai","periods":[{"number":1,"start":"08:00","end":"08:50"}]}""")
            val document = JSONObject("""{"schema_version":1,"status":"plan_ready","operation":"add","time_zone":"Asia/Shanghai","restore":"ring","intervals":[{"start":"2030-11-18T08:00:00+08:00","end":"2030-11-18T08:50:00+08:00","mode":"vibrate","label":"测试"}]}""").put("period_table", table)
            val p = proposals.stage(document)
            check(store.plans().isEmpty())
            proposals.apply(p.getString("id"))
            check(store.plans().size == 1 && store.get("restore") == "ring" && JSONObject(store.get("periodTable")).getString("name") == "冬季测试")
            check(proposals.current() == null)
            val nested = JSONObject(document.toString())
            nested.remove("restore"); nested.getJSONArray("intervals").getJSONObject(0).put("restore", "ring")
            try { proposals.apply(p.getString("id")); error("Duplicate confirmation accepted") } catch (_: IllegalStateException) { }
            store.replace(emptyList(), store.revision())
            val stale = proposals.stage(nested)
            check(stale.getJSONObject("document").getString("restore") == "ring")
            store.replace(emptyList(), store.revision())
            try { proposals.apply(stale.getString("id")); error("Stale plan accepted") } catch (_: IllegalArgumentException) { }
            check(store.plans().isEmpty())
            val changedTable = proposals.stage(document)
            store.saveTable(null, store.get("periodVersion", "0").toLong())
            try { proposals.apply(changedTable.getString("id")); error("Stale timetable accepted") } catch (_: IllegalArgumentException) { }
            check(store.plans().isEmpty() && store.get("periodTable").isBlank())
            val isolated = object: ContextWrapper(targetContext) { override fun getFilesDir() = root }
            var images = ConversationImages(isolated)
            images.save("fixture", byteArrayOf(1,2,3))
            val message = JSONObject().put("role", "user").put("content", "图片").put("image_ids", JSONArray().put("fixture"))
            val history = JSONArray().put(message)
            images = ConversationImages(isolated)
            check(images.message(message, images.retained(history)).getJSONArray("content").length() == 2)
            check(images.retained(history, 0).isEmpty())
            images.prune(JSONArray())
            check(images.retained(history).isEmpty())
            finish(0, Bundle().apply { putString("stream", "PASS proposal preview/atomic apply/duplicate/stale checks; image context reload/bounds/prune") })
        } catch(e: Throwable) { finish(1, Bundle().apply { putString("stream", "FAIL: ${e.message}") }) }
        finally { store.close() }
    }
}
