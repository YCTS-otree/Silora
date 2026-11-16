package com.silora.app

import android.app.Instrumentation
import android.os.Bundle

class UpdateChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            val text = """{"app":"Silora","latest":"1.8.0","published":"2026-11-16","notes":["自动检查更新"],"download_pages":["https://github.com/YCTS-otree/Silora/tree/main/Release/v1.8.0"]}"""
            val release = UpdateChecker.parse(text, "1.7.1")
            check(release.latest == "1.8.0" && release.notes.size == 1 && release.pages.size == 1)
            for (bad in listOf(text.replace("\"Silora\"", "\"Other\""), text.replace("1.8.0", "invalid"), text.replace("github.com", "evil.com"))) {
                check(runCatching { UpdateChecker.parse(bad, "1.7.1") }.isFailure)
            }
            finish(0, Bundle().apply { putString("result", "PASS: valid manifest and 3 rejected manifests") })
        } catch (e: Exception) { finish(1, Bundle().apply { putString("error", e.javaClass.simpleName) }) }
    }
}
