package com.silora.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result=goAsync()
        Core.executor.execute {
            try {
                val core=Core.get(context)
                AppLog.record("schedule_broadcast kind=" + when (intent.action) {
                    "com.silora.app.ALARM" -> "alarm"
                    RenewalReminders.ACTION -> "reminder"
                    Intent.ACTION_BOOT_COMPLETED -> "boot"
                    Intent.ACTION_MY_PACKAGE_REPLACED -> "upgrade"
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> "clock"
                    else -> "permission_or_other"
                })
                if(intent.action=="com.silora.app.ALARM")core.reconcile("定时事件",intent.data?.lastPathSegment ?: "invalid")
                else if(intent.action==RenewalReminders.ACTION)core.reminders.received(intent.data?.lastPathSegment ?: "invalid")
                else {
                    // Reboot/clock changes discard any cached scheduling identity and rebuild absolute instants.
                    core.invalidateAlarm()
                    core.reconcile(intent.action.orEmpty())
                }
            } catch(e: Exception) { AppLog.exception("schedule_broadcast", e) }
            finally { result.finish() }
        }
    }
}
