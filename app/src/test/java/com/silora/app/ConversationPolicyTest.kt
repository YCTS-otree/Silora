package com.silora.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class ConversationPolicyTest {
    @Test fun sundayAndNextWeekAreExplicit() {
        val text = ConversationPolicy.calendar(ZonedDateTime.parse("2026-11-15T22:00:00+08:00[Asia/Shanghai]"), 1)
        assertTrue(text.contains("本周/这周：2026-11-09 至 2026-11-15"))
        assertTrue(text.contains("下周：2026-11-16 至 2026-11-22"))
    }
    @Test fun weekCrossesYearAndFourthTurnIsBounded() {
        val text = ConversationPolicy.calendar(ZonedDateTime.parse("2026-12-31T23:00:00+08:00[Asia/Shanghai]"), 4)
        assertTrue(text.contains("本周/这周：2026-12-28 至 2027-01-03"))
        assertTrue(text.contains("下周：2027-01-04 至 2027-01-10"))
        assertTrue(text.contains("当前需求第 4 轮"))
    }
}
