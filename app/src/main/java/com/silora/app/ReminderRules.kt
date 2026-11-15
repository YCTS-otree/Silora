package com.silora.app

import java.time.Instant
import java.time.ZoneId

data class ReminderDecision(val deliverSlot: Long?, val nextSlot: Long?)

object ReminderRules {
    fun hours(values: List<Int>): List<Int> {
        require(values.size in 2..6 && values.distinct().size == values.size && values.all { it in 8..18 }) {
            "请设置 2–6 个不同的白天整点（08–18 点）"
        }
        return values.sorted()
    }
    fun decide(end: Long?, now: Long, zone: ZoneId, hours: List<Int>, deliveredThrough: Long): ReminderDecision {
        if (end == null || now >= end) return ReminderDecision(null, null)
        val start = end - 24 * 3600000L
        var date = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        val slots = mutableListOf<Long>()
        while (date <= last) {
            for (hour in hours(hours)) {
                val local = date.atTime(hour, 0)
                if (zone.rules.getValidOffsets(local).isEmpty()) continue
                val time = local.atZone(zone).toInstant().toEpochMilli()
                if (time in start until end) slots.add(time)
            }
            date = date.plusDays(1)
        }
        val latestDue = slots.filter { it <= now }.maxOrNull()
        val daytime = Instant.ofEpochMilli(now).atZone(zone).hour in 8..18
        val deliver = latestDue?.takeIf { daytime && it > deliveredThrough }
        return ReminderDecision(deliver, slots.filter { it > now }.minOrNull())
    }
}
