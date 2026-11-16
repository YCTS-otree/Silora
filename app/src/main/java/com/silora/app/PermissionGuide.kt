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
    private var leftActivity = false
    private val labels = mapOf("policy" to "通知策略访问：切换响铃、振动和静音", "exact" to "精确闹钟：按计划时间执行", "notifications" to "通知权限：接收到期提醒")
    private val notifications get() = activity.getSystemService(NotificationManager::class.java)
    private fun granted(kind: String): Boolean = when (kind) {
        "policy" -> notifications.isNotificationPolicyAccessGranted
        "exact" -> Build.VERSION.SDK_INT < 31 || activity.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        "notifications" -> notifications.areNotificationsEnabled() && notifications.getNotificationChannel(RenewalReminders.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        else -> false
    }
    private fun missing() = labels.keys.filterNot(::granted)
    fun onStart() { if (awaiting == null && dialog == null) checked = false }
    fun onPause() { if (awaiting != null) leftActivity = true }
    fun onResume() {
        if (awaiting != null) { if (leftActivity) finishStep(); return }
        if (checked || dialog != null || activity.isFinishing) return
        checked = true
        val needed = missing()
        AppLog.record("permission_check missing=${needed.joinToString(",")}")
        if (needed.isEmpty()) return
        dialog = AlertDialog.Builder(activity).setTitle("先完成权限设置")
            .setMessage("静律还需要以下权限：\n\n" + needed.joinToString("\n\n") { labels.getValue(it) } + "\n\n点击开始后将逐项打开系统授权页面，返回静律会自动检查结果。")
            .setPositiveButton("开始授权") { _, _ -> dialog = null; advance() }
            .setNegativeButton("稍后") { _, _ -> dialog = null }
            .setOnCancelListener { dialog = null }.show()
    }
    private fun advance() {
        val next = missing().firstOrNull()
        if (next == null) { Toast.makeText(activity, "权限已就绪", Toast.LENGTH_SHORT).show(); return }
        awaiting = next; leftActivity = false
        try { open(next) } catch (_: android.content.ActivityNotFoundException) {
            awaiting = null
            showIncomplete("系统未提供此授权入口，请在手机设置中为静律开启对应权限。")
        }
    }
    private fun finishStep() {
        val kind = awaiting ?: return
        AppLog.record("permission_return kind=$kind granted=${granted(kind)}")
        awaiting = null; leftActivity = false
        if (granted(kind)) advance()
        else showIncomplete("尚未获得${labels.getValue(kind)}。对应功能暂不可用；可稍后在静律的设置中继续授权。")
    }
    private fun showIncomplete(message: String) {
        dialog = AlertDialog.Builder(activity).setTitle("授权尚未完成").setMessage(message)
            .setPositiveButton("知道了") { _, _ -> dialog = null }.setOnCancelListener { dialog = null }.show()
    }
    fun onPermissionResult() { if (awaiting == "notifications") finishStep() }
    fun open(kind: String) {
        AppLog.record("permission_open kind=${kind.takeIf { it in labels } ?: "unknown"}")
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
            "notifications" -> if (notifications.areNotificationsEnabled() && notifications.getNotificationChannel(RenewalReminders.CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, RenewalReminders.CHANNEL)
                else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            else -> throw IllegalArgumentException("未知权限")
        }
        activity.startActivity(intent)
    }
    fun destroy() { dialog?.dismiss(); dialog = null }
}
