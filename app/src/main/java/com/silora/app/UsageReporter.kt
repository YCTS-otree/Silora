package com.silora.app

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One foreground UI startup per process; background-only launches never reach this entry. */
internal object UsageReporter {
    private const val ENDPOINT = "https://license.ycts.top/api/v1/usage"
    private val gate = UsageProtocol()
    private val worker = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "silora-usage").apply { isDaemon = true } }
    private val client by lazy { OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).writeTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build() }

    fun onUiReady(context: Context) {
        if (BuildConfig.APPLICATION_ID != "com.silora.app" || !gate.claimStartup()) return
        val app = context.applicationContext
        worker.execute {
            runCatching {
                val machine = UsageProtocol.fingerprint(Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID))
                if (machine == null) { AppLog.record("usage skipped_identifier"); return@runCatching }
                send(UsageProtocol.payload(machine), SystemClock.elapsedRealtime(), 0)
            }.onFailure { AppLog.record("usage skipped_initialization") }
        }
    }
    private fun send(body: String, started: Long, attempt: Int) {
        if (SystemClock.elapsedRealtime() - started >= UsageProtocol.WINDOW_MS) return
        var code: Int? = null
        var retryAfter: String? = null
        try {
            val request = Request.Builder().url(ENDPOINT)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            client.newCall(request).execute().use { response -> code = response.code; retryAfter = response.header("Retry-After") }
        } catch (_: Exception) { /* Never log request, response, identifiers or exception messages. */ }
        AppLog.record("usage attempt=${attempt + 1} status=${code ?: "network_error"}")
        val delay = UsageProtocol.retryDelay(code, retryAfter, attempt, SystemClock.elapsedRealtime() - started, System.currentTimeMillis(), Math.random()) ?: return
        worker.schedule({ send(body, started, attempt + 1) }, delay, TimeUnit.MILLISECONDS)
    }
}
