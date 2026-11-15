package com.silora.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

object AcademicJson {
    private fun text(o: JSONObject, key: String): String {
        val value = o.get(key)
        require(value is String) { "$key 必须是字符串" }
        return value.trim()
    }
    private fun integer(o: JSONObject, key: String): Int {
        val value = o.get(key)
        require(value is Int) { "$key 必须是整数" }
        return value
    }
    private fun time(o: JSONObject, key: String): LocalTime {
        val value = text(o, key)
        require(value.matches(Regex("[0-9]{2}:[0-9]{2}"))) { "时间须为 HH:mm" }
        return LocalTime.parse(value)
    }
    fun table(o: JSONObject): PeriodTable {
        val array = o.getJSONArray("periods")
        require(array.length() in 1..32) { "课时表需包含 1–32 节课" }
        return AcademicRules.table(PeriodTable(text(o, "name"), text(o, "time_zone"),
            (0 until array.length()).map { i -> array.getJSONObject(i).let {
                Period(integer(it, "number"), time(it, "start"), time(it, "end"))
            } }))
    }
    fun tableJson(table: PeriodTable): JSONObject = JSONObject().put("name", table.name).put("time_zone", table.zone)
        .put("periods", JSONArray().apply { table.periods.forEach {
            put(JSONObject().put("number", it.number).put("start", it.start.toString()).put("end", it.end.toString()))
        } })

    fun cycle(o: JSONObject, previousId: String? = null): WeekCycle {
        val array = o.getJSONArray("entries")
        require(array.length() in 1..100) { "周期规则需为 1–100 条" }
        return WeekCycle(previousId ?: o.optString("id").ifBlank { UUID.randomUUID().toString() },
            text(o, "name"), LocalDate.parse(text(o, "anchor_monday")), LocalDate.parse(text(o, "valid_from")),
            LocalDate.parse(text(o, "valid_until")), text(o, "time_zone"),
            (0 until array.length()).map { i -> array.getJSONObject(i).let {
                WeekEntry(integer(it, "weekday"), text(it, "weeks"), time(it, "start"), time(it, "end"),
                    PlanJson.mode(text(it, "mode")), text(it, "label"))
            } })
    }
    fun cycleJson(cycle: WeekCycle): JSONObject = JSONObject().put("id", cycle.id).put("name", cycle.name)
        .put("anchor_monday", cycle.anchor.toString()).put("valid_from", cycle.from.toString())
        .put("valid_until", cycle.until.toString()).put("time_zone", cycle.zone)
        .put("entries", JSONArray().apply { cycle.entries.forEach {
            put(JSONObject().put("weekday", it.weekday).put("weeks", it.weeks).put("start", it.start.toString())
                .put("end", it.end.toString()).put("mode", PlanJson.name(it.mode)).put("label", it.label))
        } })
    fun automation(o: JSONObject, previousId: String? = null): Pair<JSONObject, List<Plan>> {
        if (!o.has("source")) {
            val cycle = cycle(o, previousId)
            return cycleJson(cycle).put("kind", "cycle") to AcademicRules.expand(cycle)
        }
        val program = ScheduleProgram(previousId ?: o.optString("id").ifBlank { UUID.randomUUID().toString() },
            text(o, "name"), LocalDate.parse(text(o, "anchor_monday")), LocalDate.parse(text(o, "valid_from")),
            LocalDate.parse(text(o, "valid_until")), text(o, "time_zone"), text(o, "source"))
        val rules = ScheduleScript.parse(program.source) { literal ->
            StrictJson.validate(literal)
            JSONArray("[$literal]").getString(0)
        }
        val normalized = JSONObject().put("kind", "script").put("id", program.id).put("name", program.name)
            .put("anchor_monday", program.anchor.toString()).put("valid_from", program.from.toString())
            .put("valid_until", program.until.toString()).put("time_zone", program.zone).put("source", program.source)
        return normalized to ScheduleScript.expand(program, rules)
    }
}
