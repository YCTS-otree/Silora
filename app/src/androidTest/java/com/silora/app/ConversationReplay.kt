package com.silora.app

import android.app.Instrumentation
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject
import java.io.File

/** Explicit, paid device replay. Requires the two user-authorized fixtures in the app's cache/Temp. */
class ConversationReplay : Instrumentation() {
    private var nextWeek = false
    override fun onCreate(arguments: Bundle?) { nextWeek = arguments?.getString("nextWeek") == "true"; super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            val ai = DeepSeek.get(targetContext)
            ai.begin()
            if (!nextWeek) ai.selectImages(listOf("replay-winter.jpg", "replay-courses.jpg").map { Uri.fromFile(File(targetContext.cacheDir, "Temp/$it")) })
            val phases = mutableListOf<String>()
            val r = ai.command(JSONObject().put("action", "ai_chat").put("text", if(nextWeek) "改为下周，其他不变。" else "请重新结合这两张图片，只按图片标注的这一周生成候选：上课振动，平时响铃。")) { phases.add(it) }
            val d = r.getJSONObject("document")
            check(d.getString("status") == "plan_ready") { "Expected first-turn proposal: ${d.optString("question")}" }
            check(d.getString("restore") == "ring")
            check(d.getJSONArray("intervals").length() >= 13)
            check(d.getJSONObject("period_table").getJSONArray("periods").getJSONObject(4).getString("start") == "14:00")
            check(ai.attachments().length() == 0)
            finish(0, Bundle().apply { putString("stream", "PASS real conversation replay; phases=$phases; intervals=${d.getJSONArray("intervals").length()}; restore=${d.getString("restore")}; first=${d.getJSONArray("intervals").getJSONObject(0).getString("start")}") })
        } catch(e: Throwable) { finish(1, Bundle().apply { putString("stream", "FAIL: ${e.message}") }) }
    }
}
