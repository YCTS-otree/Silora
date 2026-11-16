package com.silora.app

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class UsageProtocolTest {
    @Test fun stableScopedFingerprint() {
        val id = UsageProtocol.fingerprint("0123456789abcdef")!!
        assertEquals(64, id.length)
        assertEquals(id, UsageProtocol.fingerprint("0123456789ABCDEF"))
        assertNotEquals(id, UsageProtocol.fingerprint("1123456789abcdef"))
        assertNull(UsageProtocol.fingerprint(null))
        assertNull(UsageProtocol.fingerprint("0000000000000000"))
        assertNull(UsageProtocol.fingerprint("9774d56d682e549c"))
        assertNull(UsageProtocol.fingerprint("invalid"))
    }
    @Test fun onlyOneStartupPerProcessSession() {
        val session = UsageProtocol()
        assertTrue(session.claimStartup())
        repeat(20) { assertFalse(session.claimStartup()) }
        assertTrue(UsageProtocol().claimStartup())
    }
    @Test fun payloadIsStableForRetryAndNewForNewStartup() {
        val machine = "a".repeat(64)
        val event = UUID.randomUUID().toString()
        val body = UsageProtocol.payload(machine, event)
        assertEquals(body, UsageProtocol.payload(machine, event))
        assertNotEquals(body, UsageProtocol.payload(machine))
        assertEquals(4, Regex(":").findAll(body).count())
        assertTrue(body.toByteArray().size <= 2048)
    }
    @Test fun retryPolicy() {
        for (code in listOf(204, 200, 301, 400, 401, 404, 413, 422))
            assertNull(UsageProtocol.retryDelay(code, null, 0, 0, 0, 0.0))
        for (code in listOf(null, 408, 429, 500, 503))
            assertEquals(5000L, UsageProtocol.retryDelay(code, null, 0, 0, 0, 0.0))
        assertEquals(30000L, UsageProtocol.retryDelay(503, null, 1, 0, 0, 0.0))
        assertEquals(120000L, UsageProtocol.retryDelay(503, null, 2, 0, 0, 0.0))
        assertNull(UsageProtocol.retryDelay(503, null, 3, 0, 0, 0.0))
        assertNull(UsageProtocol.retryDelay(503, null, 0, 595000, 0, 0.0))
        assertEquals(60000L, UsageProtocol.retryDelay(429, "60", 0, 0, 0, 0.0))
        assertEquals(60000L, UsageProtocol.retryDelay(429, "Thu, 01 Jan 1970 00:01:00 GMT", 0, 0, 0, 0.0))
        assertNull(UsageProtocol.retryDelay(429, "999999999999", 0, 0, 0, 0.0))
    }
}
