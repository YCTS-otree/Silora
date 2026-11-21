package com.silora.app

import org.junit.Assert.*
import org.junit.Test

class LocationEditsTest {
    private val a=Plan("mon",100,200,1,"高等数学",noticeMinutes=10)
    private val b=a.copy(id="wed",start=300,end=400,noticeMinutes=15)
    @Test fun sameCourseDifferentRoomsPreservesOtherFields() {
        val original=listOf(a,b)
        val edits=LocationEdits.resolve(original,listOf(LocationEdits.Edit(null,100,"南-326"),LocationEdits.Edit("wed",300,"北-411")))
        assertEquals(listOf(a.copy(location="南-326"),b.copy(location="北-411")),original.map { it.copy(location=edits.getValue(it.id)) })
    }
    @Test fun rejectsAmbiguousStaleAndConflictingRows() {
        assertThrows(IllegalStateException::class.java){ LocationEdits.resolve(listOf(a,a.copy(id="b")),listOf(LocationEdits.Edit(null,100,"南-326"))) }
        assertThrows(IllegalStateException::class.java){ LocationEdits.resolve(listOf(a,b),listOf(LocationEdits.Edit("wed",100,"南-326"))) }
        assertThrows(IllegalArgumentException::class.java){ LocationEdits.resolve(listOf(a),listOf(LocationEdits.Edit("mon",null,"南-326"),LocationEdits.Edit(null,100,"北-411"))) }
    }
}
