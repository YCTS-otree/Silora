package com.silora.app

import android.app.Instrumentation
import android.os.Bundle
import org.json.JSONArray

class AiConfigChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            for (effort in listOf("low", "high", "max")) {
                val body = AiConfig.request(JSONArray(), effort)
                check(body.getJSONObject("thinking").getString("type") == "enabled")
                check(body.getString("reasoning_effort") == effort)
                check(body.getInt("max_tokens") >= 16384)
            }
            for (effort in listOf("none", "disabled", "medium", "")) {
                try { AiConfig.request(JSONArray(), effort); error("Invalid effort accepted") }
                catch (_: IllegalArgumentException) { }
            }
            finish(0, Bundle().apply { putString("stream", "PASS thinking enabled at all three levels; off and invalid levels rejected") })
        } catch (e: Throwable) { finish(1, Bundle().apply { putString("stream", "FAIL: ${e.message}") }) }
    }
}
