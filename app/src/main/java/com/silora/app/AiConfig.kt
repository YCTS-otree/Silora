package com.silora.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AiConfig {
    private val efforts = setOf("low", "high", "max")
    private fun prefs(context: Context) = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    fun effort(context: Context): String = prefs(context).getString("effort", "high")?.takeIf { it in efforts } ?: "high"
    fun save(context: Context, effort: String) {
        require(effort in efforts) { "思考强度只能为 low、high 或 max，不能关闭思考" }
        check(prefs(context).edit().putString("effort", effort).commit()) { "思考设置保存失败，请重试" }
    }
    fun request(messages: JSONArray, effort: String): JSONObject {
        require(effort in efforts) { "无效的思考强度" }
        return JSONObject().put("model", "deepseek-flash").put("messages", messages)
            .put("thinking", JSONObject().put("type", "enabled")).put("reasoning_effort", effort)
            .put("response_format", JSONObject().put("type", "json_object")).put("max_tokens", 32768).put("stream", false)
    }
}
