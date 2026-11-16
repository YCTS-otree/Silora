package com.silora.app

import android.app.Instrumentation
import android.net.Uri
import android.os.Bundle
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises only logging; does not touch plans, keys, conversation or permission state. */
class LoggingChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            AppLog.flush()
            check(AppLog.snapshot().getBoolean("ready"))
            AppLog.exception("instrumented_test", IllegalArgumentException("SYNTHETIC_PRIVATE_PAYLOAD_MUST_BE_ABSENT"))
            AppLog.operation("SYNTHETIC_UNKNOWN_ACTION_MUST_BE_ABSENT", false)
            val done = CountDownLatch(1)
            var succeeded = true
            AppLog.export(targetContext, Uri.parse("content://com.silora.invalid-test-provider/destination")) { succeeded = it; done.countDown() }
            check(done.await(10, TimeUnit.SECONDS) && !succeeded)
            AppLog.flush()
            check(AppLog.snapshot().getString("exportState") == "failed")
            check(!AppLog.snapshot().getBoolean("writeFailed"))
            val logs = PrivateLogStore(File(targetContext.filesDir, "logs"))
            check(logs.entries().size in 1..4)
            val text = logs.entries().joinToString("\n") { logs.read(it) }
            check(text.contains("stage=instrumented_test") && text.contains("java.lang.IllegalArgumentException"))
            check(text.contains("operation=unknown result=failed") && text.contains("log_export result=failed"))
            check(!text.contains("SYNTHETIC_PRIVATE_PAYLOAD") && !text.contains("SYNTHETIC_UNKNOWN_ACTION"))
            finish(0, Bundle().apply { putString("stream", "PASS private logging, bounded files, exception/input privacy, failed export result and recovery") })
        } catch (e: Throwable) { finish(1, Bundle().apply { putString("stream", "FAIL logging check: ${e.javaClass.simpleName}") }) }
    }
}
