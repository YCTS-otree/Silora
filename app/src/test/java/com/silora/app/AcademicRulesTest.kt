package com.silora.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AcademicRulesTest {
    private fun date(s: String) = LocalDate.parse(s)
    private fun time(s: String) = LocalTime.parse(s)
    private fun entry(weeks: String = "all", day: Int = 1, start: String = "08:00", end: String = "09:00") =
        WeekEntry(day, weeks, time(start), time(end), 1, "课程")
    private fun cycle(entries: List<WeekEntry>) = WeekCycle("test", "学期", date("2026-11-16"), date("2026-11-16"), date("2026-12-13"), "Asia/Shanghai", entries)
    private fun program(source: String) = ScheduleProgram("test", "脚本", date("2026-11-16"), date("2026-11-16"), date("2026-12-13"), "Asia/Shanghai", source)
    private fun script(source: String) = ScheduleScript.expand(program(source), ScheduleScript.parse(source) { it.substring(1, it.length - 1) })
    private fun dates(plans: List<Plan>) = plans.map { Instant.ofEpochMilli(it.start).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString() }

    @Test fun tableValidatedAndOrdered() {
        val table = AcademicRules.table(PeriodTable("作息", "Asia/Shanghai", listOf(Period(2,time("09:00"),time("09:45")),Period(1,time("08:00"),time("08:45")))))
        assertEquals(listOf(1,2),table.periods.map { it.number })
    }
    @Test fun tableRejectsOverlapsAndDuplicateNumbers() {
        val a=Period(1,time("08:00"),time("08:45"))
        listOf(listOf(a,a),listOf(a,Period(2,time("08:30"),time("09:00")))).forEach {
            assertThrows(IllegalArgumentException::class.java){AcademicRules.table(PeriodTable("作息","Asia/Shanghai",it))}
        }
    }
    @Test fun oddEvenWeeksAreAnchoredToSchoolMonday() {
        assertEquals(listOf("2026-11-16","2026-11-30"),dates(AcademicRules.expand(cycle(listOf(entry("odd"))))))
        assertEquals(listOf("2026-11-23","2026-12-07"),dates(AcademicRules.expand(cycle(listOf(entry("even"))))))
    }
    @Test fun anchorCrossesYearWithoutIsoWeekReset() {
        assertEquals(2L,AcademicRules.week(date("2026-12-28"),date("2027-01-04")))
        assertEquals(1L,AcademicRules.week(date("2026-12-28"),date("2027-01-03")))
    }
    @Test fun rangeBoundsAndMondayRequired() {
        val base=cycle(listOf(entry()))
        listOf(base.copy(anchor=date("2026-11-17")),base.copy(until=date("2028-01-01")),base.copy(from=date("2026-11-15"))).forEach {
            assertThrows(IllegalArgumentException::class.java){AcademicRules.expand(it)}
        }
    }
    @Test fun separateOddEvenRulesDoNotConflictButSameWeekDoes() {
        assertEquals(4,AcademicRules.expand(cycle(listOf(entry("odd"),entry("even")))).size)
        assertThrows(IllegalArgumentException::class.java){AcademicRules.expand(cycle(listOf(entry(),entry("odd"))))}
    }
    @Test fun dstGapsAndFoldsAreRejected() {
        listOf("2026-03-29","2026-10-25").forEach { sunday ->
            val d=date(sunday)
            val c=WeekCycle("dst","DST",d.minusDays(6),d,d,"Europe/Berlin",listOf(entry(day=7,start="02:15",end="02:45")))
            assertThrows(IllegalArgumentException::class.java){AcademicRules.expand(c)}
        }
    }
    @Test fun scriptMatchesOddWeekAndExcludesDate() {
        val source="when weekday == 1 and week % 2 == 1 and date != \"2026-11-30\" -> vibrate(\"08:00\", \"09:00\", \"课程\")"
        assertEquals(listOf("2026-11-16"),dates(script(source)))
    }
    @Test fun scriptAndHasPrecedenceOverOr() {
        assertEquals(6,script("when weekday == 1 or weekday == 2 and week % 2 == 0 -> silent(\"08:00\", \"09:00\", \"实验\")").size)
    }
    @Test fun scriptRejectsHostCodeLoopsAndUnboundedInput() {
        listOf("while(true){}","fetch(\"https://example.com\")","when system == 1 -> ring(\"08:00\", \"09:00\", \"x\")","when week % 0 == 1 -> ring(\"08:00\", \"09:00\", \"x\")").forEach {
            assertThrows(IllegalArgumentException::class.java){script(it)}
        }
        assertThrows(IllegalArgumentException::class.java){script("when always -> ring(\"08:00\", \"09:00\", \"x\")\n".repeat(101))}
    }
    @Test fun scriptConflictsRejectedBeforeExecution() {
        assertThrows(IllegalArgumentException::class.java){script("when always -> ring(\"08:00\", \"09:00\", \"x\")\nwhen weekday == 1 -> silent(\"08:30\", \"09:30\", \"y\")")}
    }
    private val zone=ZoneId.of("Asia/Shanghai")
    private fun instant(text:String)=java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli()
    @Test fun remindersDeliverThreeDistinctDaytimeSlots() {
        val end=instant("2026-11-17T08:00:00+08:00")
        var sent=0L
        for(hour in listOf("09","14","18")) {
            val now=instant("2026-11-16T${hour}:00:00+08:00")
            val d=ReminderRules.decide(end,now,zone,listOf(9,14,18),sent)
            assertEquals(now,d.deliverSlot);sent=now
            assertNull(ReminderRules.decide(end,now,zone,listOf(9,14,18),sent).deliverSlot)
        }
    }
    @Test fun remindersNeverCatchUpAtNightOrAfterExpiry() {
        val end=instant("2026-11-17T08:00:00+08:00")
        assertNull(ReminderRules.decide(end,instant("2026-11-16T21:00:00+08:00"),zone,listOf(9,14,18),0).deliverSlot)
        assertNull(ReminderRules.decide(end,end,zone,listOf(9,14,18),0).nextSlot)
        assertNull(ReminderRules.decide(null,end,zone,listOf(9,14,18),0).deliverSlot)
    }
    @Test fun missedSlotsCollapseToOneAndNeverExceed24Hours() {
        val end=instant("2026-11-17T12:00:00+08:00")
        val d=ReminderRules.decide(end,instant("2026-11-16T15:00:00+08:00"),zone,listOf(9,14,18),0)
        assertEquals(instant("2026-11-16T14:00:00+08:00"),d.deliverSlot)
        assertNull(ReminderRules.decide(end,instant("2026-11-16T09:00:00+08:00"),zone,listOf(9,14,18),0).deliverSlot)
    }
    @Test fun reminderHoursRejectNightAndDuplicates() {
        listOf(listOf(9),listOf(9,9),listOf(9,23)).forEach { assertThrows(IllegalArgumentException::class.java){ReminderRules.hours(it)} }
    }
}
