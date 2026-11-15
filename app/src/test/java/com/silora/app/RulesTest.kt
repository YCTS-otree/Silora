package com.silora.app

import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test fun rejectsLenientAndDuplicateJson() {
        listOf("{'x':1}","{x:1}","{\"x\":1,}","[1,]","{\"x\":1,\"x\":2}","{} trailing","[01]","/*comment*/{}").forEach { assertThrows(IllegalArgumentException::class.java){StrictJson.validate(it)} }
        StrictJson.validate("{\"a\":[1,-2.5e+3,true,false,null,\"中文\\n\"]}")
    }
    private fun p(id: String="a",start:Long=10,end:Long=20,mode:Int=1)=Plan(id,start,end,mode,"课程")
    @Test fun offsetsRepresentSameInstant() {
        assertEquals(PlanRules.instant("2026-11-15T08:00:00+08:00"),PlanRules.instant("2026-11-15T00:00:00Z"))
        assertEquals(PlanRules.instant("2026-11-14T19:00:00-05:00"),PlanRules.instant("2026-11-15T00:00:00Z"))
    }
    @Test fun daylightSavingOffsetsRemainDistinct() { assertEquals(3600000,PlanRules.instant("2026-11-01T01:30:00-05:00")-PlanRules.instant("2026-11-01T01:30:00-04:00")) }
    @Test fun invalidDatesAndMissingOffsetsRejected() { listOf("2026-02-30T10:00:00Z","2026-11-15T08:00:00","garbage").forEach { assertThrows(IllegalArgumentException::class.java) { PlanRules.instant(it) } } }
    @Test fun adjacentAndCrossMidnightAllowed() { val s=PlanRules.instant("2026-11-15T23:00:00+08:00");val e=PlanRules.instant("2026-11-16T01:00:00+08:00");assertEquals(2,PlanRules.validate(listOf(p(start=s,end=e),p("b",e,e+1))).size) }
    @Test fun overlapDuplicateAndInvalidModeRejected() {
        listOf(listOf(p(),p("b",19,22)),listOf(p(),p()),listOf(p(mode=9)),listOf(p(end=10))).forEach { assertThrows(IllegalArgumentException::class.java) { PlanRules.validate(it) } }
    }
    @Test fun halfOpenAndNextBoundary() { val list=listOf(p(),p("b",20,30,0));assertEquals("b",PlanRules.active(list,20)?.id);assertNull(PlanRules.active(list,30));assertEquals(20L,PlanRules.next(list,10)) }
    @Test fun emptyPlanAndDisabledPlan() { assertTrue(PlanRules.validate(emptyList()).isEmpty());assertNull(PlanRules.active(listOf(p().copy(enabled=false)),15)) }
    @Test fun separateSessionsAndBridgedContinuousIntervals() {
        assertFalse(PlanRules.connected(listOf(p(),p("b",30,40)),"a","b"))
        assertTrue(PlanRules.connected(listOf(p(),p("bridge",20,30),p("b",30,40)),"a","b"))
    }
    @Test fun ringToVibrateThenSilentThenRestore() {
        val first=ModeRules.decide(RunState(),p(),2,"previous","respect");assertEquals(1,first.target)
        val second=ModeRules.decide(first.state,p("b",20,30,0),1,"previous","respect");assertEquals(0,second.target);assertEquals(2,second.state.before)
        assertEquals(2,ModeRules.decide(second.state,null,0,"previous","respect").target)
    }
    @Test fun manualChangeRespectedThroughContinuousIntervals() {
        val old=RunState(true,2,1,false,"a")
        val changed=ModeRules.decide(old,p(),0,"previous","respect");assertNull(changed.target);assertTrue(changed.state.suppressed)
        assertNull(ModeRules.decide(changed.state,p("b"),0,"previous","respect").target)
        assertNull(ModeRules.decide(changed.state,null,0,"previous","respect").target)
    }
    @Test fun resumeAtNextNodeAndPriority() {
        val old=RunState(true,2,1,true,"a")
        assertNull(ModeRules.decide(old,p(),0,"previous","next").target)
        assertEquals(1,ModeRules.decide(old,p("b"),0,"previous","next").target)
        assertEquals(1,ModeRules.decide(old,p(),0,"previous","priority").target)
    }
    @Test fun restorePolicies() {
        val old=RunState(true,0,1,false,"a")
        assertEquals(2,ModeRules.decide(old,null,1,"ring","respect").target)
        assertEquals(1,ModeRules.decide(old,null,1,"vibrate","respect").target)
        assertNull(ModeRules.decide(old,null,1,"keep","respect").target)
    }
    @Test fun repeatedEvaluationIsIdempotentAndLateWakeRecomputes() {
        val plans=listOf(p(),p("b",20,30,0));val active=PlanRules.active(plans,25)
        val first=ModeRules.decide(RunState(),active,2,"previous","respect")
        assertEquals(0,first.target)
        val repeated=ModeRules.decide(first.state,active,0,"previous","respect");assertEquals(first,repeated)
        assertNull(PlanRules.active(plans,31))
    }
}
