package com.silora.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test

class PrivateLogStoreTest {
    private fun withStore(test: (File, PrivateLogStore) -> Unit) {
        val temp = File("Temp/log-tests").apply { mkdirs() }
        val root = Files.createTempDirectory(temp.toPath(), "case-").toFile()
        try { test(root, PrivateLogStore(root)) } finally { root.deleteRecursively() }
    }
    @Test fun restartRotationAndArchiveContainOnlyRetainedSegments() = withStore { root, store ->
        File(root, "unrelated.txt").writeText("NOT_A_LOG")
        repeat(6) { i -> RollingLog(store, "20261116-00000$i-000-abcdef12").apply { start(); append("记录 $i\n") } }
        assertEquals(4, store.entries().size)
        val output = ByteArrayOutputStream(); store.export(output)
        val names = mutableListOf<String>(); val texts = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; names += entry.name; texts += zip.readBytes().toString(Charsets.UTF_8) }
        }
        assertEquals(4, names.size)
        assertTrue(names.all { it.startsWith("silora-log-") && !it.contains('/') })
        assertEquals(listOf("记录 2\n", "记录 3\n", "记录 4\n", "记录 5\n"), texts)
        assertTrue(File(root, "unrelated.txt").exists())
    }
    @Test fun traversalAndNonLogEntriesAreRejected() = withStore { _, store ->
        for (name in listOf("../other.txt", "credentials.key", "/other.log")) {
            try { store.create(name); fail("Invalid filename accepted") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(store.entries().isEmpty())
    }
    @Test fun writeFailureIsNotReportedAsSuccessfulArchive() = withStore { _, store ->
        RollingLog(store, "test").apply { start(); append("message") }
        val broken = object : java.io.OutputStream() { override fun write(b: Int) { throw java.io.IOException("synthetic failure") } }
        try { store.export(broken); fail("Broken destination accepted") } catch (_: java.io.IOException) { }
    }
}
