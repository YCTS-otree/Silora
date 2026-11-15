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
        when (d.getString("status")) {
            "plan_ready" -> candidate = PlanJson.candidate(d, store.plans())
            "recurrence_ready", "script_ready" -> {
                val key = if (d.getString("status") == "script_ready") "script" else "recurrence"
                val (rule, plans) = automation(d.getJSONObject(key))
                d.put(key, rule); candidate = plans
            }
            else -> throw IllegalArgumentException("此消息没有可确认的计划")
        }
        d.optJSONObject("period_table")?.let { d.put("period_table", AcademicJson.tableJson(AcademicJson.table(it))) }
        require(!d.has("restore") || d.getString("restore") in listOf("previous", "ring", "vibrate", "keep")) { "无效恢复策略" }
        val p = JSONObject().put("id", UUID.randomUUID().toString()).put("document", d)
            .put("revision", store.revision()).put("periodVersion", store.get("periodVersion", "0").toLong())
            .put("restoreBefore", store.get("restore", "previous")).put("candidate", PlanJson.array(candidate))
        store.put("proposal", p.toString())
        return p
    }
    fun apply(id: String) {
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
        if (d.getString("status") != "plan_ready") {
            val key = if (d.getString("status") == "script_ready") "script" else "recurrence"
            metadata["automation"] = d.getJSONObject(key).toString()
        }
        val a = p.getJSONArray("candidate")
        store.replace((0 until a.length()).map { PlanJson.read(a.getJSONObject(it)) }, p.getLong("revision"), metadata)
    }
}
