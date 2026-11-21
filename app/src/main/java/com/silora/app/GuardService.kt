package com.silora.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import org.json.JSONObject

/** Optional visible process protection. AlarmManager remains the wake-up source. No wake lock or polling. */
class GuardService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable {
        Core.executor.execute {
            try { Core.get(this).reconcile("守护时间节点") }
            catch (e: Exception) { AppLog.exception("guard_boundary", e) }
        }
    }
    override fun onCreate() {
        super.onCreate()
        instance = this; starting = false
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "后台守护", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 31, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getActivity(this, 32, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("静律 · 后台守护已开启").setContentText("帮助计划在后台执行，点击打开静律")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(null, "管理守护", stop).build()).build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, notification)
            running = true; issue = ""
            AppLog.record("guard_started")
        } catch (e: Exception) {
            issue = "系统未允许启动守护，请检查后台限制后重试"; running = false
            AppLog.exception("guard_start", e); stopSelf()
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            // Legacy notification action must not bypass the UI confirmations.
            return START_STICKY
        }
        if (!enabled(this) || !running) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        Core.executor.execute {
            try { Core.get(this).reconcile("守护启动") }
            catch (e: Exception) { AppLog.exception("guard_reconcile", e) }
        }
        return START_STICKY
    }
    private fun schedule(time: Long?) {
        handler.removeCallbacks(tick)
        if (time != null) handler.postDelayed(tick, (time - System.currentTimeMillis()).coerceAtLeast(1))
    }
    override fun onTaskRemoved(rootIntent: Intent?) { AppLog.record("guard_task_removed"); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (instance === this) { instance = null; running = false }
        AppLog.record("guard_destroyed"); super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
    companion object {
        internal const val CHANNEL = "background_guard"
        private const val ID = 1300
        private const val STOP = "com.silora.app.STOP_GUARD"
        @Volatile private var instance: GuardService? = null
        @Volatile private var running = false
        @Volatile private var starting = false
        @Volatile private var issue = ""
        private fun prefs(context: Context) = context.getSharedPreferences("guard", Context.MODE_PRIVATE)
        fun isRunning() = running
        fun isStarting() = starting
        fun enabled(context: Context) = prefs(context).getBoolean("enabled", true)
        fun setEnabled(context: Context, value: Boolean) {
            check(prefs(context).edit().putBoolean("enabled", value).commit())
            if (value) ensureStarted(context) else {
                starting = false; issue = ""
                context.stopService(Intent(context, GuardService::class.java))
            }
            AppLog.record("guard_setting enabled=$value")
        }
        /** Only called by visible activity/user action or the boot receiver, never to evade user stopping. */
        @Synchronized fun ensureStarted(context: Context) {
            if (!enabled(context) || running || starting) return
            starting = true
            try { context.startForegroundService(Intent(context, GuardService::class.java)) }
            catch (e: Exception) {
                starting = false; issue = "启动受系统限制，请允许后台运行后重试"
                AppLog.exception("guard_start_request", e)
            }
        }
        fun boundary(time: Long?) { instance?.let { service -> service.handler.post { if (instance === service && running) service.schedule(time) } } }
        fun snapshot(context: Context): JSONObject {
            val power = context.getSystemService(PowerManager::class.java)
            val manager = context.getSystemService(NotificationManager::class.java)
            return JSONObject().put("enabled", enabled(context)).put("running", running).put("issue", issue)
                .put("batteryExempt", power.isIgnoringBatteryOptimizations(context.packageName))
                .put("restricted", context.getSystemService(ActivityManager::class.java).isBackgroundRestricted)
                .put("notificationAllowed", manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
        }
    }
}
