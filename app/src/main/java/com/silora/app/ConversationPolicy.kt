package com.silora.app

import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object ConversationPolicy {
    fun calendar(now: ZonedDateTime, turn: Int): String {
        val monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return "当前时间：$now；今天：${now.toLocalDate()}；时区：${now.zone.id}。\n" +
            "本周/这周：$monday 至 ${monday.plusDays(6)}；下周：${monday.plusWeeks(1)} 至 ${monday.plusDays(13)}。\n" +
            "当前需求第 $turn 轮。优先首轮给候选，通常 3 轮内完成，第 4 轮不得继续泛泛追问；信息不足就整理可核对的结果和具体缺口。"
    }
}
