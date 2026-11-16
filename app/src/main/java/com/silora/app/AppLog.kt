package com.silora.app

import android.content.Context
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** CPVC's single-worker/session/rotation logging, adapted to private storage and explicit SAF export. */
object AppLog {
    private val worker = Executors.newSingleThreadExecutor()
    private var engine: RollingLog? = null
    private var store: PrivateLogStore? = null
    private val session = stamp("yyyyMMdd-HHmmss-SSS") + "-" + UUID.randomUUID().toString().take(8)
    @Volatile private var ready = false
    @Volatile private var writeFailed = false
    @Volatile private var files = 0
    @Volatile private var bytes = 0L
    private val exporting = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var exportState = "idle"
    private fun stamp(pattern: String) = SimpleDateFormat(pattern, Locale.ROOT).format(Date())
    fun initialize(context: Context) {
        val app = context.applicationContext
        enqueue {
            if (engine == null) {
                store = PrivateLogStore(File(app.filesDir, "logs"))
                updateSize()
                engine = RollingLog(store!!, session).apply { start() }
                ready = true
                updateSize()
            }
        }
        record("startup Silora=${BuildConfig.VERSION_NAME} Android=${Build.VERSION.RELEASE} API=${Build.VERSION.SDK_INT}")
    }
    private fun updateSize() { files = store?.entries()?.size ?: 0; bytes = store?.size() ?: 0 }
    private fun enqueue(block: () -> Unit) { worker.execute {
        try { block(); writeFailed = false } catch (e: Exception) {
            writeFailed = true
            android.util.Log.w("Silora-Log", "Log write failed (${e.javaClass.simpleName})")
        }
    } }
    internal fun record(event: String) {
        val time = stamp("yyyy-MM-dd HH:mm:ss.SSS")
        enqueue { checkNotNull(engine).append("$time ${event.take(12000)}\n"); updateSize() }
    }
    fun operation(action: String, success: Boolean) = record("operation=${LogPrivacy.action(action)} result=${if (success) "ok" else "failed"}")
    fun exception(stage: String, error: Throwable) = record(LogPrivacy.exception(stage, error))
    fun flush() { runCatching { worker.submit {}.get(3, TimeUnit.SECONDS) } }
    fun snapshot() = JSONObject().put("ready", ready).put("writeFailed", writeFailed)
        .put("files", files).put("bytes", bytes).put("maxFiles", 4).put("maxMiB", 8).put("exportState", exportState)
    fun isExporting() = exporting.get()
    fun exportName() = "Silora-logs-${stamp("yyyyMMdd-HHmmss")}.zip"
    fun export(context: Context, uri: Uri, result: (Boolean) -> Unit) {
        check(exporting.compareAndSet(false, true)) { "正在导出日志，请稍候" }
        exportState = "writing"
        val resolver = context.applicationContext.contentResolver
        record("log_export started")
        worker.execute {
            val success = try {
                val source = checkNotNull(store)
                resolver.openOutputStream(uri, "wt")?.use { source.export(it) } ?: error("No export stream")
                true
            } catch (e: Exception) { exception("log_export", e); false }
            record("log_export result=${if (success) "ok" else "failed"}")
            exportState = if (success) "done" else "failed"
            exporting.set(false)
            result(success)
        }
    }
}
