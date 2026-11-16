package com.silora.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Independent, inexact notification alarm: never consumes the ringer's exact-idle scheduling path. */
class RenewalReminders(private val context: Context, private val store: PlanStore) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    init {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "计划到期提醒", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun enabled() = store.get("reminderEnabled", "true") == "true"
    fun allowed() = notifications.areNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    fun hours() = ReminderRules.hours(store.get("reminderHours", "9,14,18").split(',').map { it.toInt() })
    private fun pending(token: String) = PendingIntent.getBroadcast(context, 1,
        Intent(context, ScheduleReceiver::class.java).setAction(ACTION).setData(Uri.parse("silora://renewal/$token")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancelAlarm() {
        val old = store.get("reminderAlarm")
        if (old.isNotEmpty()) { val p = pending(old); alarms.cancel(p); p.cancel() }
        store.put("reminderAlarm", "")
    }
    fun received(token: String) {
        if (token != store.get("reminderAlarm")) return
        reschedule(store.plans())
    }
    fun reschedule(plans: List<Plan>, now: Long = System.currentTimeMillis()) {
        cancelAlarm()
        store.put("reminderNext", "")
        val horizon = plans.filter { it.enabled }.maxOfOrNull { it.end }
        if (store.get("reminderEnd") != horizon?.toString().orEmpty()) notifications.cancel(ID)
        store.put("reminderEnd", horizon?.toString().orEmpty())
        if (!enabled() || horizon == null || horizon <= now) { notifications.cancel(ID); return }
        if (!allowed()) return
        val checkpoint = if (store.get("reminderSentEnd") == horizon.toString()) store.get("reminderSentSlot", "0").toLong() else 0L
        val decision = ReminderRules.decide(horizon, now, ZoneId.systemDefault(), hours(), checkpoint)
        decision.deliverSlot?.let { slot ->
            val endText = Instant.ofEpochMilli(horizon).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))
            if (notify("当期计划即将结束", "已确认计划将在 $endText 结束。下一次如何安排？点击补充课表或调整计划。")) {
                store.put("reminderSentEnd", horizon.toString())
                store.put("reminderSentSlot", slot.toString())
            }
        }
        decision.nextSlot?.let { next ->
            val token = "${horizon}_$next"
            store.put("reminderAlarm", token)
            store.put("reminderNext", next.toString())
            alarms.setWindow(AlarmManager.RTC_WAKEUP, next, 3600000L, pending(token))
        }
    }
    private fun notify(title: String, text: String): Boolean {
        if (!allowed()) { AppLog.record("notification blocked"); return false }
        val open = PendingIntent.getActivity(context, 2,
            Intent(context, MainActivity::class.java).putExtra("renewal", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return try {
            notifications.notify(ID, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build())
            AppLog.record("notification delivered")
            true
        } catch (e: SecurityException) { AppLog.exception("notification", e); false }
    }
    fun test() { check(notify("静律通知测试", "通知已开启。正式到期提醒仅在白天、当期计划结束前 24 小时内发送。")) { "请先允许通知，并开启计划到期提醒通知渠道" } }
    fun snapshot() = JSONObject().put("enabled", enabled()).put("allowed", allowed()).put("hours", org.json.JSONArray(hours()))
        .put("end", store.get("reminderEnd")).put("next", store.get("reminderNext"))
    companion object {
        const val ACTION = "com.silora.app.REMINDER"
        const val CHANNEL = "plan_renewal"
        const val ID = 1200
    }
}
