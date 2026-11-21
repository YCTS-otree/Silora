package com.silora.app

import android.app.*
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A separate, user-visible alarm clock does not consume the ringer alarm's idle quota. */
class AdvanceNotices(private val context: Context, private val store: PlanStore) {
    companion object {
        const val ACTION = "com.silora.app.ADVANCE_NOTICE"
        const val CHANNEL = "plan_start_notices"
    }
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val open = PendingIntent.getActivity(context, 711, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private val pending = PendingIntent.getBroadcast(context, 711, Intent(context, ScheduleReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private var registered: Long? = null
    private var initialized = false
    init { manager.createNotificationChannel(NotificationChannel(CHANNEL,"课程和会议开始提醒",NotificationManager.IMPORTANCE_HIGH)) }

    fun sync(plans: List<Plan>, now: Long, exact: Boolean) {
        val old = store.get("advanceDelivered","{}")
        val sent = JSONObject(old)
        val retained = JSONObject()
        val allowed = manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        var next: Long? = null
        for (p in plans) {
            if (!p.enabled || p.noticeMinutes == 0 || p.start <= now) continue
            if (sent.optLong(p.id,-1) == p.start) { retained.put(p.id,p.start); continue }
            if (!allowed) continue
            val due = p.start - p.noticeMinutes * 60000L
            if (due > now) { next = minOf(next ?: due, due); continue }
            val time = Instant.ofEpochMilli(p.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
            val body = listOfNotNull("开始：$time", p.location.takeIf { it.isNotBlank() }?.let { "地点：$it" }).joinToString(" · ")
            val n = Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(p.label).setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER).setAutoCancel(true).setOnlyAlertOnce(true)
                .setTimeoutAfter((p.end-now).coerceAtLeast(1)).setContentIntent(open).build()
            try { manager.notify(p.id,711,n); retained.put(p.id,p.start) }
            catch (e: SecurityException) { AppLog.exception("advance_notify",e) }
        }
        if (retained.toString() != old) store.put("advanceDelivered",retained.toString())
        val target = next.takeIf { exact }
        if (initialized && target == registered) return
        initialized = true
        if (target == null) alarms.cancel(pending)
        else try { alarms.setAlarmClock(AlarmManager.AlarmClockInfo(target,open),pending) }
        catch (e: SecurityException) { registered=null; initialized=false; AppLog.exception("advance_schedule",e); return }
        registered=target
    }
}
