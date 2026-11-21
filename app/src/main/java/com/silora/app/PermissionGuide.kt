package com.silora.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/** Check each foreground launch; special access must be granted in Android settings. */
class PermissionGuide(private val activity: Activity) {
    private var checked = false
    private var dialog: AlertDialog? = null
    private var awaiting: String? = null
    private var resumed = false
    private var active = false
    private var returned: String? = null
    private val skipped = mutableSetOf<String>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var waitUntil = 0L
    private val refresh = Runnable { advance() }
    private val titles = linkedMapOf(
        "exact" to "精确闹钟", "notifications" to "允许通知", "policy" to "通知策略访问",
        "battery" to "电池优化豁免", "guard" to "后台守护"
    )
    private val purposes = mapOf(
        "exact" to "让静律在上课和下课的指定时间切换铃声。请在系统页面允许静律设置闹钟和提醒。",
        "notifications" to "显示课程或会议开始提醒、计划到期提醒和后台守护通知。请允许静律发送通知，并开启对应的通知类别。",
        "policy" to "让静律按计划切换响铃、振动和静音。请在系统的通知策略访问列表中允许静律。",
        "battery" to "减少熄屏后省电策略对计划执行的限制。若系统询问是否允许后台运行，请选择允许；这可能增加耗电。",
        "guard" to "守护开关已开启，但尚未检测到服务运行。它会在后台协助执行计划。请在应用信息中检查后台运行限制，返回后静律会重试。"
    )
    private val notifications get() = activity.getSystemService(NotificationManager::class.java)
    private fun blockedChannel() = listOfNotNull(RenewalReminders.CHANNEL, AdvanceNotices.CHANNEL, GuardService.CHANNEL.takeIf { GuardService.enabled(activity) })
        .firstOrNull { notifications.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE }
    private fun granted(kind: String): Boolean = when (kind) {
        "policy" -> notifications.isNotificationPolicyAccessGranted
        "exact" -> Build.VERSION.SDK_INT < 31 || activity.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        "notifications" -> notifications.areNotificationsEnabled() && blockedChannel() == null
        "battery" -> activity.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(activity.packageName)
        "guard" -> !GuardService.enabled(activity) || GuardService.isRunning()
        else -> false
    }
    fun onStart() { if (awaiting == null && !active) checked = false }
    fun onPause() { resumed = false; handler.removeCallbacks(refresh) }
    fun onResume() {
        resumed = true
        waitUntil = android.os.SystemClock.elapsedRealtime() + 3000
        if (!checked) {
            checked = true
            active = true
            skipped.clear()
            AppLog.record("startup_check missing=" + titles.keys.filterNot(::granted).joinToString(","))
        }
        advance()
    }
    private fun advance() {
        if (!resumed || !active || awaiting != null || dialog != null || activity.isFinishing || activity.isDestroyed) return
        val kind = titles.keys.firstOrNull { it !in skipped && !granted(it) }
        if (kind == null) {
            active = false
            if (returned != null || skipped.isNotEmpty()) Toast.makeText(activity,
                if (skipped.isEmpty()) "启动检查已完成" else "检查已结束，尚未完成：" + skipped.filterNot(::granted).map { titles[it] }.joinToString("、"),
                Toast.LENGTH_SHORT).show()
            returned = null
            return
        }
        if (kind == "guard" && GuardService.isStarting() && android.os.SystemClock.elapsedRealtime() < waitUntil) {
            handler.removeCallbacks(refresh); handler.postDelayed(refresh, 150); return
        }
        val incomplete = returned == kind
        returned = null
        dialog = AlertDialog.Builder(activity)
            .setTitle("启动检查 ${titles.keys.indexOf(kind)+1}/${titles.size} · ${titles.getValue(kind)}")
            .setMessage((if (incomplete) "此项尚未完成，可重试或先跳过。\n\n" else "") + purposes.getValue(kind))
            .setPositiveButton(if (incomplete) "重新设置" else "去设置") { _, _ ->
                dialog = null; awaiting = kind
                try { open(kind) } catch (e: RuntimeException) {
                    AppLog.exception("permission_launch", e)
                    awaiting = null
                    Toast.makeText(activity, "系统未提供该入口，请在手机设置中查找静律。", Toast.LENGTH_LONG).show()
                    returned = kind; advance()
                }
            }
            .setNegativeButton("跳过此项") { _, _ -> dialog = null; skipped.add(kind); advance() }
            .setNeutralButton("稍后再说") { _, _ -> dialog = null; active = false }
            .setOnCancelListener { dialog = null; active = false }.show()
    }
    fun onSettingsResult() {
        val kind = awaiting ?: return
        AppLog.record("permission_return kind=$kind granted=${granted(kind)}")
        returned = kind; awaiting = null
        advance()
    }
    fun onPermissionResult() { if (awaiting == "notifications") onSettingsResult() }
    fun open(kind: String) {
        AppLog.record("permission_open kind=${kind.takeIf { it in titles } ?: "unknown"}")
        if (kind == "notifications" && Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!activity.getPreferences(Activity.MODE_PRIVATE).getBoolean("notificationAsked", false) || activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            activity.getPreferences(Activity.MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5002)
            return
        }
        val intent = when (kind) {
            "policy" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "exact" -> if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${activity.packageName}")) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
            "notifications" -> if (notifications.areNotificationsEnabled() && blockedChannel() != null)
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, blockedChannel())
                else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            "course_channel" -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, AdvanceNotices.CHANNEL)
            "battery" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}"))
            "guard" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
            else -> throw IllegalArgumentException("未知权限")
        }
        fun launch(target: Intent) {
            if (awaiting == kind) activity.startActivityForResult(target, 5010) else activity.startActivity(target)
        }
        try { launch(intent) } catch (e: android.content.ActivityNotFoundException) {
            if (kind != "battery") throw e
            launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
    fun destroy() { handler.removeCallbacks(refresh); dialog?.dismiss(); dialog = null }
}
