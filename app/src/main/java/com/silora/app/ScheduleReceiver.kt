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
                if(intent.action=="com.silora.app.ALARM")core.reconcile("定时事件",intent.data?.lastPathSegment ?: "invalid")
                else if(intent.action==RenewalReminders.ACTION)core.reminders.received(intent.data?.lastPathSegment ?: "invalid")
                else {
                    // Reboot/clock changes discard any cached scheduling identity and rebuild absolute instants.
                    core.invalidateAlarm()
                    core.reconcile(intent.action.orEmpty())
                }
            } catch(e: Exception) { android.util.Log.e("Silora", "原生调度恢复失败", e) }
            finally { result.finish() }
        }
    }
}
