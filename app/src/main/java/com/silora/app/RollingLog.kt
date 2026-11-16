package com.silora.app

// Adapted from CPVC_for_Android RollingLog; same session/size/retention algorithm.

internal class RollingLog(private val store: Store, private val session: String) {
    data class Entry(val id: String, val name: String)
    interface Store {
        fun entries(): List<Entry>
        fun create(name: String): Entry
        fun append(entry: Entry, bytes: ByteArray)
        fun delete(entry: Entry)
        fun read(entry: Entry): String
    }
    var maxFiles = 4
    var maxBytes = 8L * 1024 * 1024
    private var current: Entry? = null
    private var size = 0L
    private var segment = 0
    fun configure(files: Int, bytes: Long) {
        maxFiles = files.coerceIn(1,100); maxBytes = bytes.coerceIn(1024*1024,64L*1024*1024)
        prune(maxFiles)
        if(size > maxBytes) { current = null; size = 0 }
    }
    fun start() { if(current == null) newFile() }
    private fun prune(keep: Int) {
        val entries = store.entries().sortedBy { it.name }
        for(entry in entries.take((entries.size-keep).coerceAtLeast(0))) {
            store.delete(entry)
            if(entry.id == current?.id) { current = null; size = 0 }
        }
    }
    private fun newFile() {
        prune(maxFiles-1)
        current = store.create("silora-log-$session-${(++segment).toString().padStart(4,'0')}.log")
        size = 0
    }
    fun append(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size.toLong() <= maxBytes)
        if(current == null || size+bytes.size > maxBytes) newFile()
        store.append(current!!,bytes); size += bytes.size
    }
    fun text(): String = current?.let(store::read) ?: "暂无日志。\n"
}
