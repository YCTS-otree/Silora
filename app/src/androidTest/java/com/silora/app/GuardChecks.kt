package com.silora.app

import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock

/** Uses the opt-in .devicecheck installation only; never touches the normal installation. */
class GuardChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        var count = 0
        fun awaitState(expected: Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 5000
            while (GuardService.snapshot(targetContext).getBoolean("running") != expected && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            check(GuardService.snapshot(targetContext).getBoolean("running") == expected)
        }
        if (targetContext.packageName != "com.silora.app.devicecheck") {
            finish(1, Bundle().apply { putString("stream", "Requires isolated -PdeviceCheckBuild=true installation") }); return
        }
        var activity: android.app.Activity? = null
        var resultCode = 0
        var message: String
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            val before = Core.executor.submit<Long> { Core.get(targetContext).store.revision() }.get()
            val manager = targetContext.getSystemService(NotificationManager::class.java)
            runOnMainSync { GuardService.setEnabled(targetContext, true) }
            awaitState(true); check(GuardService.enabled(targetContext)); count++
            val notification = manager.activeNotifications.single { it.id == 1300 }.notification
            check(notification.actions.size == 1); count++
            notification.actions.single().actionIntent.send()
            awaitState(false); check(!GuardService.enabled(targetContext)); count++
            check(manager.activeNotifications.none { it.id == 1300 }); count++
            runOnMainSync { GuardService.ensureStarted(targetContext) }
            SystemClock.sleep(300); check(!GuardService.snapshot(targetContext).getBoolean("running")); count++
            runOnMainSync { GuardService.setEnabled(targetContext, true) }
            awaitState(true)
            runOnMainSync { GuardService.ensureStarted(targetContext) }
            check(manager.activeNotifications.count { it.id == 1300 } == 1); count++
            check(Core.executor.submit<Long> { Core.get(targetContext).store.revision() }.get() == before); count++
            message = "PASS $count foreground guard / notification stop checks"
        } catch (e: Throwable) {
            resultCode = 1
            message = "FAIL after $count: ${e.javaClass.simpleName}"
        } finally {
            runOnMainSync { GuardService.setEnabled(targetContext, false); activity?.finish() }
        }
        finish(resultCode, Bundle().apply { putString("stream", message) })
    }
}
