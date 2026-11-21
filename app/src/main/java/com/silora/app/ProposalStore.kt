package com.silora.app

import org.json.JSONObject
import java.util.UUID

/** A confirmed proposal commits plans, optional timetable and restore policy in one transaction. */
class ProposalStore(private val store: PlanStore, private val automation: (JSONObject) -> Pair<JSONObject, List<Plan>>) {
    fun current(): JSONObject? = store.get("proposal").takeIf { it.isNotBlank() }?.let { JSONObject(it) }
    fun clear() = store.put("proposal", "")
    fun stage(document: JSONObject): JSONObject {
        val d = JSONObject(document.toString())
        // Some models put this global policy on each interval. Accept only a consistent value.
        val intervalRestores = d.optJSONArray("intervals")?.let { a -> (0 until a.length())
            .mapNotNull { a.getJSONObject(it).optString("restore").takeIf { value -> value.isNotBlank() } }.distinct() }.orEmpty()
        require(intervalRestores.size <= 1 && (intervalRestores.isEmpty() || !d.has("restore") || d.getString("restore") == intervalRestores.single())) { "结束恢复策略不一致，请核对方案" }
        if (!d.has("restore") && intervalRestores.isNotEmpty()) d.put("restore", intervalRestores.single())
        val candidate: List<Plan>
        var affected: List<String>
        var askNotice=false
        when (d.getString("status")) {
            "plan_ready" -> {
                val a=d.getJSONArray("intervals")
                val rows=(0 until a.length()).map { a.getJSONObject(it) }
                rows.forEach { if(!it.has("id")) it.put("id",UUID.randomUUID().toString()) }
                affected=rows.map { it.getString("id") }
                askNotice=rows.isNotEmpty() && !d.has("notice_minutes") && rows.none { it.has("notice_minutes") }
                candidate=PlanJson.candidate(d,store.plans())
            }
            "location_ready" -> {
                require(listOf("restore","period_table","intervals","notice_minutes","operation").none(d::has)) { "地点修订不能混入排课或提醒设置" }
                val rows=d.getJSONArray("updates")
                val current=store.plans()
                val shorts=mutableListOf<String>()
                val changes=LocationEdits.resolve(current,(0 until rows.length()).map { i ->
                    val row=rows.getJSONObject(i)
                    require(row.keys().asSequence().all { it in setOf("id","start","location","location_short","label","end") })
                    shorts.add(PlanJson.short(row))
                    LocationEdits.Edit(if(row.has("id"))row.getString("id") else null,
                        if(row.has("start"))PlanRules.instant(row.getString("start")) else null, PlanJson.location(row),
                        if(row.has("label"))row.getString("label") else null, if(row.has("end"))PlanRules.instant(row.getString("end")) else null)
                })
                affected=changes.keys.toList()
                val aliases=affected.zip(shorts).toMap()
                candidate=current.map { p -> changes[p.id]?.let { p.copy(location=it,locationShort=aliases.getValue(p.id).ifBlank { if(p.location==it)p.locationShort else "" }) } ?: p }
            }
            "notice_ready" -> {
                require(!d.has("restore") && !d.has("period_table")) { "仅修改提醒时不可更改作息或恢复策略" }
                require(d.has("notice_minutes"))
                val mins=PlanJson.minutes(d)
                val scope=d.getJSONObject("scope")
                require(scope.keys().asSequence().all { it in setOf("all","label","start","end","id") })
                require(scope.opt("all")==true || listOf("label","start","id").any { scope.has(it) }) { "提醒范围不能为空" }
                require(scope.has("start")==scope.has("end"))
                val start=if(scope.has("start")) PlanRules.instant(scope.getString("start")) else Long.MIN_VALUE
                val end=if(scope.has("end")) PlanRules.instant(scope.getString("end")) else Long.MAX_VALUE
                val now=System.currentTimeMillis()
                val current=store.plans()
                val selected=current.filter { it.start>now && it.start>=start && it.start<end &&
                    (!scope.has("id") || it.id==scope.getString("id")) && (!scope.has("label") || it.label==scope.getString("label")) }
                require(selected.isNotEmpty()) { "没有符合范围的未来安排" }
                affected=selected.map { it.id }
                val ids=affected.toSet()
                candidate=current.map { if(it.id in ids) it.copy(noticeMinutes=mins) else it }
            }
            "recurrence_ready", "script_ready" -> {
                val key = if (d.getString("status") == "script_ready") "script" else "recurrence"
                val input=d.getJSONObject(key)
                if(d.has("notice_minutes") && !input.has("notice_minutes")) input.put("notice_minutes",PlanJson.minutes(d))
                val entries=input.optJSONArray("entries")
                askNotice=!input.has("notice_minutes") && (entries==null || (0 until entries.length()).none { entries.getJSONObject(it).has("notice_minutes") })
                val (rule, plans) = automation(input)
                affected=plans.filter { it.id.startsWith("cycle_"+rule.getString("id")+"_") }.map { it.id }
                d.put(key, rule); candidate = plans
            }
            else -> throw IllegalArgumentException("此消息没有可确认的计划")
        }
        d.optJSONObject("period_table")?.let { d.put("period_table", AcademicJson.tableJson(AcademicJson.table(it))) }
        require(!d.has("restore") || d.getString("restore") in listOf("previous", "ring", "vibrate", "keep")) { "无效恢复策略" }
        val p = JSONObject().put("id", UUID.randomUUID().toString()).put("document", d)
            .put("affected",org.json.JSONArray(affected)).put("askNotice",askNotice).put("revision", store.revision()).put("periodVersion", store.get("periodVersion", "0").toLong())
            .put("restoreBefore", store.get("restore", "previous")).put("candidate", PlanJson.array(candidate))
        store.put("proposal", p.toString())
        return p
    }
    fun apply(id: String, noticeOverride: Int? = null) {
        val p = current() ?: error("没有待确认方案，请先发送安排")
        require(p.getString("id") == id) { "这份方案已更新，请确认最新方案" }
        require(p.getLong("periodVersion") == store.get("periodVersion", "0").toLong() &&
            p.getString("restoreBefore") == store.get("restore", "previous")) { "作息或执行策略已变化，请重新生成方案" }
        val d = p.getJSONObject("document")
        val metadata = mutableMapOf("proposal" to "")
        d.optJSONObject("period_table")?.let {
            metadata["periodTable"] = AcademicJson.tableJson(AcademicJson.table(it)).toString()
            metadata["periodVersion"] = (p.getLong("periodVersion") + 1).toString()
        }
        if (d.has("restore")) metadata["restore"] = d.getString("restore")
        if (d.getString("status") in listOf("recurrence_ready","script_ready")) {
            val key = if (d.getString("status") == "script_ready") "script" else "recurrence"
            val rule=d.getJSONObject(key)
            if(noticeOverride!=null && p.optBoolean("askNotice")) {
                rule.put("notice_minutes",noticeOverride)
                rule.optJSONArray("entries")?.let { entries -> for(i in 0 until entries.length()) entries.getJSONObject(i).put("notice_minutes",noticeOverride) }
            }
            metadata["automation"] = rule.toString()
        }
        val a = p.getJSONArray("candidate")
        val ids=p.optJSONArray("affected")?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }.orEmpty()
        store.replace((0 until a.length()).map { PlanJson.read(a.getJSONObject(it)).let { row ->
            if(noticeOverride!=null && p.optBoolean("askNotice") && row.id in ids) row.copy(noticeMinutes=noticeOverride) else row
        } }, p.getLong("revision"), metadata)
    }
}
