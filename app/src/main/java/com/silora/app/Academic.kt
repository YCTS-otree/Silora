package com.silora.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class Period(val number: Int, val start: LocalTime, val end: LocalTime)
data class PeriodTable(val name: String, val zone: String, val periods: List<Period>)
data class WeekEntry(val weekday: Int, val weeks: String, val start: LocalTime,
                     val end: LocalTime, val mode: Int, val label: String, val location: String = "", val noticeMinutes: Int = 0, val locationShort: String = "")
data class WeekCycle(val id: String, val name: String, val anchor: LocalDate,
                     val from: LocalDate, val until: LocalDate, val zone: String,
                     val entries: List<WeekEntry>)

object AcademicRules {
    fun table(table: PeriodTable): PeriodTable {
        require(table.name.isNotBlank() && table.name.length <= 80) { "课时表名称需为 1–80 字" }
        ZoneId.of(table.zone)
        require(table.periods.size in 1..32) { "课时表需包含 1–32 节课" }
        require(table.periods.map { it.number }.distinct().size == table.periods.size) { "课节编号重复" }
        table.periods.forEach {
            require(it.number in 1..32 && it.start < it.end) { "课节编号或起止时间无效" }
        }
        table.periods.sortedBy { it.start }.zipWithNext().forEach { (a, b) ->
            require(a.end <= b.start) { "课时表中的上下课时间重叠" }
        }
        return table.copy(periods = table.periods.sortedBy { it.number })
    }

    fun week(anchor: LocalDate, date: LocalDate): Long {
        require(anchor.dayOfWeek == DayOfWeek.MONDAY && date >= anchor) { "第 1 周起点必须是周一，且不能晚于生效日期" }
        return ChronoUnit.WEEKS.between(anchor, date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) + 1
    }

    fun expand(cycle: WeekCycle): List<Plan> {
        require(cycle.id.matches(Regex("[a-zA-Z0-9_-]{1,40}"))) { "周期 ID 无效" }
        require(cycle.name.isNotBlank() && cycle.name.length <= 80) { "请填写周期名称" }
        require(cycle.anchor.dayOfWeek == DayOfWeek.MONDAY && cycle.from >= cycle.anchor) { "请准确填写第 1 周的周一" }
        require(cycle.until >= cycle.from && ChronoUnit.DAYS.between(cycle.anchor, cycle.until) <= 366) { "学期范围无效或超过一年" }
        require(cycle.entries.size in 1..100) { "周期规则需为 1–100 条" }
        val zone = ZoneId.of(cycle.zone)
        cycle.entries.forEach {
            require(it.weekday in 1..7 && it.weeks in setOf("all", "odd", "even")) { "星期或单双周类型无效" }
            require(it.start < it.end && it.mode in 0..2 && it.label.isNotBlank() && it.label.length <= 120) { "周期课程时间、名称或模式无效（暂不支持跨日规则）" }
        }
        val plans = mutableListOf<Plan>()
        var date = cycle.from
        while (date <= cycle.until) {
            val odd = week(cycle.anchor, date) % 2L == 1L
            cycle.entries.forEachIndexed { index, entry ->
                if (entry.weekday == date.dayOfWeek.value &&
                    (entry.weeks == "all" || entry.weeks == if (odd) "odd" else "even")) {
                    val start = date.atTime(entry.start)
                    val end = date.atTime(entry.end)
                    require(zone.rules.getValidOffsets(start).size == 1 && zone.rules.getValidOffsets(end).size == 1) {
                        "$date 存在夏令时歧义，请缩小范围并手动安排该日"
                    }
                    plans.add(Plan("cycle_${cycle.id}_${date.toString().replace("-", "")}_$index",
                        start.atZone(zone).toInstant().toEpochMilli(), end.atZone(zone).toInstant().toEpochMilli(),
                        entry.mode, entry.label, location=entry.location, noticeMinutes=entry.noticeMinutes,locationShort=entry.locationShort))
                }
            }
            date = date.plusDays(1)
        }
        require(plans.isNotEmpty()) { "此日期范围没有匹配的单双周课程" }
        return PlanRules.validate(plans)
    }
}
