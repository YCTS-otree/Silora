package com.silora.app

import org.junit.Assert.*
import org.junit.Test

class RollingLogTest {
    private class Memory: RollingLog.Store {
        val data = linkedMapOf<RollingLog.Entry,ByteArray>()
        override fun entries() = data.keys.toList()
        override fun create(name: String) = RollingLog.Entry(name,name).also { check(it !in data);data[it]=byteArrayOf() }
        override fun append(entry: RollingLog.Entry,bytes: ByteArray) { data[entry]=data.getValue(entry)+bytes }
        override fun delete(entry: RollingLog.Entry) { data.remove(entry) }
        override fun read(entry: RollingLog.Entry) = data.getValue(entry).toString(Charsets.UTF_8)
    }
    @Test fun eachStartupCreatesNewFileAndRetainsOnlyFour() {
        val store=Memory()
        repeat(6) { RollingLog(store,"20261010-${it.toString().padStart(4,'0')}").apply { start();append("hello") } }
        assertEquals(4,store.data.size)
        assertTrue(store.data.keys.none { it.name.contains("20261010-0000") || it.name.contains("20261010-0001") })
    }
    @Test fun utf8BytesRollBeforeLimitAndKeepCharactersWhole() {
        val store=Memory();val log=RollingLog(store,"test")
        log.configure(4,1024*1024)
        log.append("x".repeat(1024*1024-1));log.append("中")
        assertEquals(2,store.data.size)
        assertTrue(store.data.values.all { it.size<=1024*1024 })
        assertEquals("中",log.text())
    }
    @Test fun exactBoundaryDoesNotRollUntilNextEntry() {
        val store=Memory();val log=RollingLog(store,"test")
        log.configure(4,1024*1024);log.append("x".repeat(1024*1024))
        assertEquals(1,store.data.size);log.append("next");assertEquals(2,store.data.size)
    }
    @Test fun oneFileModeRemovesOldSegmentWithoutLosingNewEntry() {
        val store=Memory();val log=RollingLog(store,"test")
        log.configure(1,1024*1024);log.append("x".repeat(1024*1024));log.append("next")
        assertEquals(1,store.data.size);assertEquals("next",log.text())
    }
    @Test fun reducingRetentionPrunesOldestAndKeepsCurrent() {
        val store=Memory();val log=RollingLog(store,"test")
        log.configure(4,1024*1024);repeat(4) { log.append("x".repeat(1024*1024)) }
        log.configure(2,1024*1024)
        assertEquals(2,store.data.size);assertEquals(1024*1024,log.text().length)
    }
    @Test fun oversizedEntryCannotGrowFilePastLimit() {
        val store=Memory();val log=RollingLog(store,"test");log.configure(4,1024*1024)
        try { log.append("x".repeat(1024*1024+1));fail() } catch(_: IllegalArgumentException) {}
        assertTrue(store.data.isEmpty())
    }
}
