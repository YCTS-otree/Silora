package com.silora.app

import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONObject

/** Runs in a disposable installation without opening the activity or changing the hardware mode. */
class AdvanceChecks: Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            check(targetContext.packageName=="com.silora.app.devicecheck")
            val c=Core.get(targetContext)
            val manager=targetContext.getSystemService(NotificationManager::class.java)
            check(c.exact() && manager.areNotificationsEnabled()) { "需要在验证包授予精确闹钟和通知权限" }
            val start=System.currentTimeMillis()+68000
            Core.executor.submit {
                check(c.store.plans().isEmpty())
                val a=Plan("notice_test",start,start+60000,2,"测试课程",location="教学楼 302")
                val b=a.copy(id="off",start=start+120000,end=start+180000)
                c.store.replace(listOf(a,b),c.store.revision())
                val p=c.proposals().stage(JSONObject("""{"status":"notice_ready","scope":{"id":"notice_test"},"notice_minutes":1}"""))
                check(c.store.plans().none { it.noticeMinutes>0 })
                c.proposals().apply(p.getString("id"))
                check(c.store.plans().map { it.noticeMinutes }==listOf(1,0))
                c.reconcile("验证未来通知")
            }.get()
            val deadline=SystemClock.elapsedRealtime()+22000
            while(manager.activeNotifications.none { it.tag=="notice_test" } && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(200)
            val n=manager.activeNotifications.single { it.tag=="notice_test" }.notification
            check(n.extras.getString(Notification.EXTRA_TITLE)=="测试课程")
            val detail=n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            check(detail.contains("教学楼 302") && detail.contains("开始："))
            val importance=manager.getNotificationChannel(n.channelId).importance
            check(importance>=NotificationManager.IMPORTANCE_DEFAULT)
            manager.cancel("notice_test",711)
            Core.executor.submit {
                c.reconcile("验证去重")
                check(manager.activeNotifications.none { it.tag=="notice_test" })
                c.store.replace(emptyList(),c.store.revision()); c.reconcile("验证取消")
            }.get()
            finish(0,Bundle().apply { putString("stream","PASS: staged AI edit; real background alarm delivered notification (system importance=$importance) with title, start and location; dedup and cleanup.") })
        } catch(e:Throwable) { finish(1,Bundle().apply {putString("stream","FAIL: "+e.stackTraceToString())}) }
    }
}
