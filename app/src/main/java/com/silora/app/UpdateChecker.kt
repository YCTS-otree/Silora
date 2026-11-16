package com.silora.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Desktop-compatible manifest; network I/O never runs on the UI or media worker. */
object UpdateChecker {
    const val MANIFEST_URL = "https://raw.githubusercontent.com/YCTS-otree/Silora/main/update.json"
    data class Release(val latest: String, val published: String, val notes: List<String>, val pages: List<String>)
    val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var cached: JSONObject? = null
    var prompted = false
    @Synchronized fun check(force: Boolean): JSONObject {
        if (!force) cached?.let { return JSONObject(it.toString()).put("prompt", false) }
        val result = try {
            val release = parse(fetch(MANIFEST_URL), BuildConfig.VERSION_NAME)
            JSONObject().put("status", if (newer(release.latest, BuildConfig.VERSION_NAME)) "available" else "current")
                .put("current", BuildConfig.VERSION_NAME).put("latest", release.latest).put("published", release.published)
                .put("notes", org.json.JSONArray(release.notes)).put("pages", org.json.JSONArray(release.pages))
        } catch (_: Exception) { JSONObject().put("status", "failed").put("current", BuildConfig.VERSION_NAME) }
        cached = result
        AppLog.record("update_check status=${result.getString("status")}")
        return JSONObject(result.toString()).put("prompt", force || !prompted).also {
            if (result.optString("status") == "available") prompted = true
        }
    }
    private fun fetch(manifestUrl: String): String {
        val connection = URL(manifestUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            connection.instanceFollowRedirects = false; connection.useCaches = false
            connection.setRequestProperty("User-Agent", "Silora-Updater/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Cache-Control", "no-cache")
            check(connection.responseCode == 200) { "更新服务暂不可用" }
            return connection.inputStream.use { input ->
                val bytes = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                val deadline = System.nanoTime() + 10_000_000_000L
                while (true) {
                    check(System.nanoTime() < deadline) { "更新检查超时" }
                    val count = input.read(buffer); if (count < 0) break
                    require(bytes.size() + count <= 128 * 1024) { "更新清单过大" }
                    bytes.write(buffer, 0, count)
                }
                bytes.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }

    internal fun parse(text: String, current: String): Release {
        val data = JSONObject(text)
        require(data.optString("app") == "Silora") { "更新清单属于其他应用" }
        val latest = data.getString("latest").trim()
        require(version(latest) != null && version(current) != null) { "更新版本无效" }
        val raw = data.opt("download_pages") ?: data.opt("download_page")
        val pages = when (raw) {
            is org.json.JSONArray -> (0 until raw.length()).map { raw.getString(it) }
            is String -> listOf(raw)
            else -> emptyList()
        }.map(String::trim).distinct().filter(::safePage)
        require(pages.isNotEmpty() && pages.size <= 2) { "缺少有效下载页" }
        val notes = data.optJSONArray("notes")?.let { array ->
            require(array.length() <= 100)
            (0 until array.length()).map { array.getString(it).also { line -> require(line.length <= 2000) } }
        } ?: emptyList()
        return Release(latest, data.optString("published").take(32), notes, pages)
    }

    internal fun safePage(text: String): Boolean = runCatching {
        val uri = URI(text)
        uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
            uri.path.split('/').none { it == "." || it == ".." || it.contains('\\') } &&
            (uri.host == "github.com" && uri.path.startsWith("/YCTS-otree/Silora/") && uri.rawQuery == null && uri.rawFragment == null && !uri.rawPath.contains("%"))
    }.getOrDefault(false)
    private fun version(value: String): List<Long>? {
        if (!Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(value)) return null
        return value.split('.').map { it.toLongOrNull() ?: return null }
    }
    internal fun newer(latest: String, current: String): Boolean {
        val a = version(latest) ?: return false; val b = version(current) ?: return false
        for (i in a.indices) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }
}
