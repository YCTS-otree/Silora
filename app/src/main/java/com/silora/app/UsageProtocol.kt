package com.silora.app

import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Android extension of YCTS usage v1; never falls back to a random device ID. */
internal class UsageProtocol {
    private val started = AtomicBoolean(false)
    fun claimStartup() = started.compareAndSet(false, true)

    companion object {
        const val WINDOW_MS = 600_000L
        fun fingerprint(raw: String?): String? {
            val id = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
            if (!id.matches(Regex("[0-9a-f]{16}")) || id == "0000000000000000" || id == "9774d56d682e549c") return null
            return MessageDigest.getInstance("SHA-256").digest("YCTS_ANDROID_V1\n$id".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        fun payload(machine: String, event: String = UUID.randomUUID().toString()): String {
            require(machine.matches(Regex("[0-9a-f]{64}")))
            require(UUID.fromString(event).toString() == event)
            return "{\"software_id\":\"silora\",\"software_name\":\"Silora\",\"machine_id\":\"$machine\",\"event_id\":\"$event\"}"
                .also { require(it.toByteArray(Charsets.UTF_8).size <= 2048) }
        }
        fun retryDelay(code: Int?, retryAfter: String?, attempt: Int, elapsed: Long, now: Long, jitter: Double): Long? {
            if (attempt >= 3 || elapsed >= WINDOW_MS || !(code == null || code == 408 || code == 429 || code in 500..599)) return null
            val server = if (code == 429) retryAfter?.let { value ->
                value.toLongOrNull()?.takeIf { it >= 0 }?.let { if (it > 600) WINDOW_MS else it * 1000 }
                    ?: runCatching { (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now).coerceAtLeast(0) }.getOrNull()
            } else null
            val delay = maxOf(server ?: 0, (longArrayOf(5000, 30000, 120000)[attempt] * (1 + jitter.coerceIn(0.0, 1.0) * 0.2)).toLong())
            return delay.takeIf { elapsed + it + 5000 < WINDOW_MS }
        }
    }
}
