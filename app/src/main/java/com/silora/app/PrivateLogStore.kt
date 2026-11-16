package com.silora.app

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** CPVC's Store adapter backed exclusively by our own private log directory. */
internal class PrivateLogStore(private val directory: File) : RollingLog.Store {
    private val pattern = Regex("silora-log-[A-Za-z0-9-]+-[0-9]{4}\\.log")
    init { check(directory.isDirectory || directory.mkdirs()) { "Cannot create log directory" } }
    private fun file(entry: RollingLog.Entry): File {
        require(entry.id == entry.name && pattern.matches(entry.name))
        return File(directory, entry.name).also { require(it.canonicalFile.parentFile == directory.canonicalFile) }
    }
    override fun entries() = directory.listFiles()?.filter {
        it.isFile && pattern.matches(it.name) && it.canonicalFile.parentFile == directory.canonicalFile
    }?.map { RollingLog.Entry(it.name, it.name) } ?: emptyList()
    override fun create(name: String) = RollingLog.Entry(name, name).also { check(file(it).createNewFile()) }
    override fun append(entry: RollingLog.Entry, bytes: ByteArray) = file(entry).appendBytes(bytes)
    override fun delete(entry: RollingLog.Entry) { check(file(entry).delete()) }
    override fun read(entry: RollingLog.Entry) = file(entry).readText(Charsets.UTF_8)
    fun size() = entries().sumOf { file(it).length() }
    /** Called on the same serial worker as writes/rotation, so exported segments are consistent. */
    fun export(output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            for (entry in entries().sortedBy { it.name }) {
                zip.putNextEntry(ZipEntry(entry.name))
                file(entry).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
