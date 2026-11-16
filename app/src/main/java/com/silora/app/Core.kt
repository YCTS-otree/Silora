package com.silora.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.time.ZoneId
import java.util.concurrent.Executors

/** All database, state-machine and alarm operations are serialized on this executor. */
class Core private constructor(private val context: Context) {
    val store=PlanStore(context)
    val reminders = RenewalReminders(context, store)
    private val audio=context.getSystemService(AudioManager::class.java)
    private val alarms=context.getSystemService(AlarmManager::class.java)
    private val notifications=context.getSystemService(NotificationManager::class.java)
    fun exact()=Build.VERSION.SDK_INT<31 || alarms.canScheduleExactAlarms()
    fun policy()=notifications.isNotificationPolicyAccessGranted
    private fun pending(token: String)=PendingIntent.getBroadcast(context,0,
        Intent(context,ScheduleReceiver::class.java).setAction("com.silora.app.ALARM").setData(Uri.parse("silora://schedule/$token")),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun invalidateAlarm() {
        val token=store.get("alarm")
        if(token.isNotEmpty()) { val p=pending(token);alarms.cancel(p);p.cancel() }
        store.put("alarm","")
    }

    fun reconcile(reason: String, alarmToken: String? = null) {
        if(alarmToken!=null && alarmToken!=store.get("alarm")) {
            store.log(null,"ignored","过期或重复事件",audio.ringerMode)
            return
        }
        if(alarmToken!=null) store.put("alarm","")
        val plans=store.plans(); val now=System.currentTimeMillis(); val active=PlanRules.active(plans,now)
        val old=store.state(); val actual=audio.ringerMode
        val restore=store.get("restore","previous");val manual=store.get("manual","respect")
        val inputState=if(old.session && old.activeId!=null && active!=null && !PlanRules.connected(plans,old.activeId,active.id)) {
            val endDecision=ModeRules.decide(old,null,actual,restore,manual)
            RunState(true,endDecision.target?:actual,actual,false,active.id)
        } else old
        val decision=ModeRules.decide(inputState,active,actual,restore,manual)
        val key="${store.revision()}:${active?.id.orEmpty()}:${decision.state.suppressed}"
        var succeeded=true
        if(active!=null && !exact()) {
            store.put("issue","精确定时不可用，请授权后重新同步")
            succeeded=false
        } else if(decision.target!=null && decision.target!=actual) {
            if(!policy()) {
                store.put("issue","未获得通知策略访问权限，无法保证三态切换")
                succeeded=false
            } else {
                // Write recovery intent before touching hardware. A process death still preserves baseline.
                store.state(if(active!=null)decision.state else old.copy(expected=decision.target))
                try {
                    audio.ringerMode=decision.target
                    succeeded=audio.ringerMode==decision.target
                    store.put("issue",if(succeeded) "" else "系统未接受铃声模式修改")
                } catch(e: SecurityException) { AppLog.exception("ringer_set", e);succeeded=false;store.put("issue","系统拒绝铃声修改，请检查通知策略访问权限") }
                catch(e: RuntimeException) { AppLog.exception("ringer_set", e);succeeded=false;store.put("issue","当前设备无法修改铃声模式") }
            }
        } else store.put("issue","")
        if(succeeded) {
            store.state(decision.state)
            if(key!=store.get("lastState") || old!=decision.state) {
                if(old.activeId!=null && old.activeId!=active?.id) store.log(old.activeId,"ended",decision.reason,audio.ringerMode)
                if(active!=null)store.log(active.id,if(decision.state.suppressed)"manual" else "applied",decision.reason,audio.ringerMode)
                else if(old.session)store.log(null,if(decision.target==null)"preserved" else "restored",decision.reason,audio.ringerMode)
                store.put("lastState",key)
            }
        } else {
            // Preserve baseline and retry intent, but never report a failed write as executed.
            if(!old.session && active!=null)store.state(RunState(true,actual,actual,false,null))
            store.log(active?.id,"failed",store.get("issue"),audio.ringerMode)
        }
        schedule(plans,now)
        reminders.reschedule(plans, now)
        store.put("lastSync",reason)
        AppLog.record("reconcile revision=${store.revision()} plans=${plans.size} active=${active != null} before=$actual target=${decision.target} actual=${audio.ringerMode} success=$succeeded exact=${exact()} policy=${policy()} scheduled=${store.get("alarm").isNotEmpty()}")
    }

    private fun schedule(plans: List<Plan>, now: Long) {
        val next=PlanRules.next(plans,now)
        val planId=next?.let { time -> plans.first { it.enabled && (it.start==time || it.end==time) }.id }
        val token=if(next!=null && exact()) "${store.revision()}_${next}_$planId" else ""
        val old=store.get("alarm")
        if(old.isNotEmpty()) { val p=pending(old);alarms.cancel(p);p.cancel() }
        store.put("alarm","");store.put("next",next?.toString().orEmpty())
        if(token.isEmpty())return
        try {
            // Persist first so a delivered event always has an identity to validate.
            store.put("alarm",token)
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next!!,pending(token))
            if (token != old) AppLog.record("alarm_scheduled next=$next revision=${store.revision()}")
        } catch(e: SecurityException) { AppLog.exception("alarm_schedule", e);store.put("alarm","");store.put("issue","精确闹钟权限已撤销，请重新授权") }
    }

    fun snapshot(): JSONObject = JSONObject().put("revision",store.revision()).put("plans",PlanJson.array(store.plans()))
        .put("mode",PlanJson.name(audio.ringerMode)).put("exact",exact()).put("policy",policy())
        .put("next",store.get("next")).put("scheduled",store.get("alarm").isNotEmpty())
        .put("activeId",store.state().activeId).put("suppressed",store.state().suppressed)
        .put("issue",store.get("issue")).put("restore",store.get("restore","previous"))
        .put("manual",store.get("manual","respect")).put("zone",ZoneId.systemDefault().id)
        .put("events",store.events()).put("version",BuildConfig.VERSION_NAME).put("apiConfigured",KeyVault(context).configured())
        .put("logging", AppLog.snapshot())
        .put("tutorial", TutorialState(context).snapshot())
        .put("reasoningEffort", AiConfig.effort(context))
        .put("periodTable", store.get("periodTable").takeIf { it.isNotBlank() }?.let { JSONObject(it) })
        .put("periodVersion", store.get("periodVersion", "0").toLong())
        .put("automation", store.get("automation").takeIf { it.isNotBlank() }?.let { JSONObject(it) })
        .put("reminder", reminders.snapshot())
        .put("proposal", proposals().current())

    fun proposals() = ProposalStore(store, ::prepareAutomation)

    fun prepareAutomation(document: JSONObject): Pair<JSONObject, List<Plan>> {
        val previous = store.get("automation").takeIf { it.isNotBlank() }?.let { JSONObject(it) }
        val (normalized, generated) = AcademicJson.automation(document, previous?.getString("id"))
        val prefix = previous?.let { "cycle_${it.getString("id")}_" }
        val retained = store.plans().filterNot { prefix != null && it.id.startsWith(prefix) }
        return normalized to PlanRules.validate(retained + generated)
    }

    fun acceptExtractedTable(table: JSONObject): String {
        val normalized = AcademicJson.tableJson(AcademicJson.table(table))
        val old = store.get("periodTable")
        if (old.isBlank()) {
            store.saveTable(normalized, store.get("periodVersion", "0").toLong())
            return "saved"
        }
        return if (old == normalized.toString()) "unchanged" else "review"
    }

    fun command(o: JSONObject): JSONObject {
        when(o.getString("action")) {
            "tutorial_save" -> TutorialState(context).save(o)
            "proposal_apply" -> {
                proposals().apply(o.getString("proposalId"))
                reconcile("确认 AI 方案")
            }
            "proposal_clear" -> proposals().clear()
            "period_save" -> store.saveTable(o.getJSONObject("table"), o.getLong("periodVersion"))
            "period_clear" -> store.saveTable(null, o.getLong("periodVersion"))
            "automation_preview" -> {
                val (rule, candidate) = prepareAutomation(o.getJSONObject("rule"))
                return JSONObject().put("rule", rule).put("candidate", PlanJson.array(candidate)).put("revision", store.revision())
            }
            "automation_save" -> {
                val (rule, candidate) = prepareAutomation(o.getJSONObject("rule"))
                store.replace(candidate, o.getLong("revision"), mapOf("automation" to rule.toString()))
                reconcile("确认周期或脚本计划")
            }
            "automation_clear" -> {
                val previous = store.get("automation").takeIf { it.isNotBlank() }?.let { JSONObject(it) }
                val prefix = previous?.let { "cycle_${it.getString("id")}_" }
                store.replace(store.plans().filterNot { prefix != null && it.id.startsWith(prefix) }, o.getLong("revision"), mapOf("automation" to ""))
                reconcile("移除周期或脚本计划")
            }
            "reminder_settings" -> {
                val values = o.getJSONArray("hours")
                val hours = ReminderRules.hours((0 until values.length()).map { i ->
                    val hour = values.get(i)
                    require(hour is Int) { "提醒时点必须是整数小时" }
                    hour
                })
                store.put("reminderHours", hours.joinToString(","))
                store.put("reminderEnabled", o.getBoolean("enabled").toString())
                reminders.reschedule(store.plans())
            }
            "reminder_test" -> reminders.test()
            "status" -> reconcile("界面同步")
            "save" -> {
                val p=PlanJson.read(o.getJSONObject("plan")); val old=store.plans()
                val edit=o.optBoolean("edit")
                require(!edit || old.any { it.id==p.id }) { "要编辑的计划不存在" }
                require(edit || old.none { it.id==p.id }) { "计划 ID 已存在" }
                store.replace(old.filterNot { it.id==p.id }+p,o.getLong("revision"));reconcile("保存计划")
            }
            "delete" -> {
                val old=store.plans();val id=o.getString("planId")
                require(old.any { it.id==id }) { "要删除的计划不存在" }
                store.replace(old.filterNot { it.id==id },o.getLong("revision"));reconcile("删除计划")
            }
            "preview" -> {
                val candidate=PlanJson.candidate(o.getJSONObject("document"),store.plans())
                return JSONObject().put("candidate",PlanJson.array(candidate)).put("revision",store.revision())
            }
            "import" -> {
                store.replace(PlanJson.candidate(o.getJSONObject("document"),store.plans()),o.getLong("revision"));reconcile("确认导入")
            }
            "settings" -> {
                val restore=o.getString("restore");val manual=o.getString("manual")
                require(restore in listOf("previous","ring","vibrate","keep") && manual in listOf("respect","next","priority")) { "无效策略" }
                store.put("restore",restore);store.put("manual",manual);reconcile("更新策略")
            }
            else -> throw IllegalArgumentException("不支持的操作")
        }
        return snapshot()
    }

    companion object {
        val executor=Executors.newSingleThreadExecutor()
        @Volatile private var instance: Core?=null
        fun get(context: Context): Core = instance ?: synchronized(this) { instance ?: Core(context.applicationContext).also { instance=it } }
    }
}
