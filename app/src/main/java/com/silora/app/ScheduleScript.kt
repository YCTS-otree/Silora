package com.silora.app

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ScriptRule(val matches: (LocalDate, Long) -> Boolean, val start: LocalTime,
                      val end: LocalTime, val mode: Int, val label: String)
data class ScheduleProgram(val id: String, val name: String, val anchor: LocalDate,
                           val from: LocalDate, val until: LocalDate, val zone: String,
                           val source: String)

/** A bounded scheduling DSL, not JavaScript evaluation. There is no host API, I/O or loop syntax. */
object ScheduleScript {
    private val quoted = "\"(?:[^\"\\\\]|\\\\.)*\""
    private val statement = Regex("when (.+?) -> (ring|vibrate|silent)\\(\\s*($quoted)\\s*,\\s*($quoted)\\s*,\\s*($quoted)\\s*\\)")
    private val numberTest = Regex("(week|weekday|month|day)(?: % ([0-9]+))? (==|!=|>=|<=|>|<) ([0-9]+)")
    private val dateTest = Regex("date (==|!=|>=|<=|>|<) \"([0-9]{4}-[0-9]{2}-[0-9]{2})\"")

    private fun compare(a: Long, op: String, b: Long) = when (op) {
        "==" -> a == b; "!=" -> a != b; ">=" -> a >= b; "<=" -> a <= b; ">" -> a > b; else -> a < b
    }
    private fun condition(text: String): (LocalDate, Long) -> Boolean {
        if (text == "always") return { _, _ -> true }
        val groups = text.split(" or ").map { group -> group.split(" and ").map { atom ->
            val numeric = numberTest.matchEntire(atom)
            val date = dateTest.matchEntire(atom)
            when {
                numeric != null -> {
                    val (variable, modulo, op, operand) = numeric.destructured
                    val divisor = modulo.takeIf { it.isNotEmpty() }?.toLong()
                    require(divisor == null || divisor in 1..366) { "取模数须为 1–366" }
                    val expected = operand.toLong()
                    val predicate: (LocalDate, Long) -> Boolean = { d, week ->
                        var value = when (variable) { "week" -> week; "weekday" -> d.dayOfWeek.value.toLong(); "month" -> d.monthValue.toLong(); else -> d.dayOfMonth.toLong() }
                        if (divisor != null) value %= divisor
                        compare(value, op, expected)
                    }
                    predicate
                }
                date != null -> {
                    val (op, literal) = date.destructured
                    val expected = LocalDate.parse(literal).toEpochDay()
                    val predicate: (LocalDate, Long) -> Boolean = { d, _ -> compare(d.toEpochDay(), op, expected) }
                    predicate
                }
                else -> throw IllegalArgumentException("不支持的脚本条件：$atom")
            }
        } }
        require(groups.sumOf { it.size } <= 20) { "每条规则最多 20 个条件" }
        return { date, week -> groups.any { and -> and.all { it(date, week) } } }
    }

    // JSON string decoding callback keeps the evaluator independent of Android for JVM tests.
    fun parse(source: String, decode: (String) -> String): List<ScriptRule> {
        require(source.length in 1..20000) { "脚本长度需为 1–20000 字符" }
        val lines = source.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
        require(lines.size in 1..100) { "脚本需包含 1–100 条规则" }
        return lines.mapIndexed { index, line ->
            val match = statement.matchEntire(line) ?: throw IllegalArgumentException("脚本第 ${index + 1} 条语法错误")
            val (guard, mode, startText, endText, labelText) = match.destructured
            val decodedStart = decode(startText); val decodedEnd = decode(endText)
            require(decodedStart.matches(Regex("[0-9]{2}:[0-9]{2}")) && decodedEnd.matches(Regex("[0-9]{2}:[0-9]{2}"))) { "脚本时间必须为 HH:mm" }
            val start = LocalTime.parse(decodedStart); val end = LocalTime.parse(decodedEnd)
            val label = decode(labelText)
            require(start < end && start.second == 0 && end.second == 0 && label.isNotBlank() && label.length <= 120) { "脚本时段或名称无效" }
            ScriptRule(condition(guard), start, end, when (mode) { "ring" -> 2; "vibrate" -> 1; else -> 0 }, label)
        }
    }
    fun expand(program: ScheduleProgram, rules: List<ScriptRule>): List<Plan> {
        require(program.id.matches(Regex("[a-zA-Z0-9_-]{1,40}")) && program.name.isNotBlank() && program.name.length <= 80) { "脚本名称或 ID 无效" }
        require(program.from >= program.anchor && program.until >= program.from && ChronoUnit.DAYS.between(program.anchor, program.until) <= 366) { "脚本日期范围无效或超过一年" }
        AcademicRules.week(program.anchor, program.from)
        val zone = ZoneId.of(program.zone)
        val result = mutableListOf<Plan>()
        var date = program.from
        while (date <= program.until) {
            val week = AcademicRules.week(program.anchor, date)
            rules.forEachIndexed { index, rule ->
                if (rule.matches(date, week)) {
                    val start = date.atTime(rule.start); val end = date.atTime(rule.end)
                    require(zone.rules.getValidOffsets(start).size == 1 && zone.rules.getValidOffsets(end).size == 1) { "$date 存在夏令时歧义" }
                    result.add(Plan("cycle_${program.id}_${date.toString().replace("-", "")}_$index",
                        start.atZone(zone).toInstant().toEpochMilli(), end.atZone(zone).toInstant().toEpochMilli(), rule.mode, rule.label))
                    require(result.size <= 2000) { "脚本生成的计划超过 2000 条" }
                }
            }
            date = date.plusDays(1)
        }
        require(result.isNotEmpty()) { "脚本在此日期范围没有生成计划" }
        return PlanRules.validate(result)
    }
}
