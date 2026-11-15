package com.silora.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Private conversation attachments, independent from the composer. No public storage paths. */
class ConversationImages(context: Context) {
    private val directory = File(context.filesDir, "conversation_images").apply { mkdirs() }
    private fun file(id: String): File {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) { "无效图片标识" }
        return File(directory, "$id.png")
    }
    fun save(id: String, bytes: ByteArray) { file(id).writeBytes(bytes) }
    fun retained(history: JSONArray, maxCount: Int = 6, maxBytes: Long = 16_000_000L): Set<String> {
        val ids = mutableListOf<String>()
        for (i in 0 until history.length()) {
            val a = history.getJSONObject(i).optJSONArray("image_ids") ?: continue
            for (j in 0 until a.length()) ids.add(a.getString(j))
        }
        var size = 0L
        return ids.distinct().asReversed().filter { id ->
            val f = file(id)
            if (!f.exists() || size + f.length() > maxBytes) false else { size += f.length(); true }
        }.take(maxCount).toSet()
    }
    fun message(message: JSONObject, retained: Set<String>): JSONObject {
        val result = JSONObject().put("role", message.getString("role")).put("content", message.getString("content"))
        val ids = message.optJSONArray("image_ids") ?: return result
        val blocks = JSONArray().put(JSONObject().put("type", "text").put("text", message.getString("content")))
        for (i in 0 until ids.length()) {
            val id = ids.getString(i)
            if (id in retained && file(id).exists()) blocks.put(JSONObject().put("type", "image_url")
                .put("image_url", JSONObject().put("url", "data:image/png;base64," + Base64.encodeToString(file(id).readBytes(), Base64.NO_WRAP)).put("detail", "high")))
        }
        return result.put("content", blocks)
    }
    fun prune(history: JSONArray) {
        val keep = retained(history).map { "$it.png" }.toSet()
        directory.listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() }
    }
    fun clear() { directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() } }
}
