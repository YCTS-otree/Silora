package com.silora.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build

class SiloraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.initialize(this)
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                getSystemService(android.app.ActivityManager::class.java).getHistoricalProcessExitReasons(null, 0, 3).forEach {
                    AppLog.record("previous_exit time=${it.timestamp} reason=${it.reason} importance=${it.importance}")
                }
            }.onFailure { AppLog.exception("exit_history", it) }
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { AppLog.exception("uncaught", error); AppLog.flush() }
            if (previous != null) previous.uncaughtException(thread, error)
            else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(1) }
        }
        val receiver=object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Core.executor.execute { try { Core.get(context).reconcile("系统铃声变化") } catch(e: Exception) { AppLog.exception("ringer_broadcast", e) } }
            }
        }
        val filter=IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,filter)
    }
}
