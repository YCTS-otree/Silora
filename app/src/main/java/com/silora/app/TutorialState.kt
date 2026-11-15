package com.silora.app

import android.content.Context
import org.json.JSONObject

class TutorialState(context: Context) {
    private val prefs = context.getSharedPreferences("tutorial", Context.MODE_PRIVATE)
    fun snapshot() = JSONObject().put("step", prefs.getInt("step", 0))
        .put("active", prefs.getBoolean("active", true)).put("completed", prefs.getBoolean("completed", false))
    fun save(o: JSONObject) {
        val step = o.get("step"); val active = o.get("active"); val completed = o.get("completed")
        require(step is Int && step in 0..7 && active is Boolean && completed is Boolean) { "无效的教学进度" }
        require(!completed || (!active && step == 7)) { "教学完成状态无效" }
        check(prefs.edit().putInt("step", step).putBoolean("active", active).putBoolean("completed", completed).commit()) { "教学进度未保存，请重试" }
    }
}
