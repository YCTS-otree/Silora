package com.silora.app

import java.time.OffsetDateTime
import java.time.ZoneId

data class Plan(val id: String, val start: Long, val end: Long, val mode: Int, val label: String, val enabled: Boolean = true)

object PlanRules {
    fun instant(text: String): Long = try {
        require(text.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?(Z|[+-]\\d{2}:\\d{2})"))) { "时间必须包含 UTC 偏移量" }
        OffsetDateTime.parse(text).toInstant().toEpochMilli()
    } catch (e: Exception) { throw IllegalArgumentException("无效时间或缺少 UTC 偏移：$text", e) }

    fun validate(plans: List<Plan>): List<Plan> {
        require(plans.size <= 2000) { "最多保存 2000 个时间段" }
        require(plans.map { it.id }.distinct().size == plans.size) { "计划 ID 重复" }
        require(plans.map { Triple(it.start,it.end,it.mode) }.distinct().size == plans.size) { "存在重复时间段" }
        plans.forEach {
            require(it.id.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "无效计划 ID" }
            require(it.start < it.end && it.end - it.start <= 366L * 86400000) { "时间范围无效或超过一年" }
            require(it.mode in 0..2) { "无效铃声模式" }
            require(it.label.isNotBlank() && it.label.length <= 120) { "名称不能为空或超过 120 字" }
        }
        val ordered = plans.sortedBy { it.start }
        ordered.filter { it.enabled }.zipWithNext().forEach { (a, b) ->
            require(a.end <= b.start) { "时间冲突：${a.label} / ${b.label}" }
        }
        return ordered
    }
    fun active(plans: List<Plan>, now: Long) = plans.firstOrNull { it.enabled && it.start <= now && now < it.end }
    fun next(plans: List<Plan>, now: Long) = plans.filter { it.enabled }.flatMap { listOf(it.start, it.end) }.filter { it > now }.minOrNull()
    fun connected(plans: List<Plan>, from: String, to: String): Boolean {
        val a=plans.find { it.id==from } ?: return true // Replacement within an active session retains its baseline.
        val b=plans.find { it.id==to } ?: return false
        if(b.start<=a.end)return true
        var edge=a.end
        for(p in plans.filter { it.enabled && it.start>=a.end }.sortedBy { it.start }) {
            if(p.start>edge)return false
            edge=maxOf(edge,p.end)
            if(p.id==to)return true
        }
        return false
    }
}

data class RunState(val session: Boolean = false, val before: Int = 2, val expected: Int? = null,
                    val suppressed: Boolean = false, val activeId: String? = null)
data class Decision(val target: Int?, val state: RunState, val reason: String)

/** Pure state transition; actual system writes and verification belong to Core. */
object ModeRules {
    fun decide(old: RunState, active: Plan?, actual: Int, restore: String, manual: String): Decision {
        val externalChange = old.session && old.expected != null && old.expected != actual
        var suppressed = old.suppressed || externalChange
        if (manual == "priority" || (manual == "next" && old.activeId != active?.id)) suppressed = false
        if (active != null) {
            val state = if (!old.session) RunState(true, actual, actual, false, active.id)
                else old.copy(suppressed = suppressed, activeId = active.id)
            if (state.suppressed) return Decision(null, state, "检测到外部模式变化，已尊重手动修改")
            return Decision(active.mode, state.copy(expected = active.mode), "执行计划")
        }
        if (!old.session) return Decision(null, RunState(), "没有生效中的计划")
        val target = if (suppressed || restore == "keep") null else when (restore) {
            "ring" -> 2
            "vibrate" -> 1
            else -> old.before
        }
        return Decision(target, RunState(), if (suppressed) "计划结束，保留外部修改" else "计划结束，恢复模式")
    }
}
