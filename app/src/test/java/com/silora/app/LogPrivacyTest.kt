package com.silora.app

import org.junit.Assert.*
import org.junit.Test

class LogPrivacyTest {
    @Test fun messagesAndCausesNeverEnterLogs() {
        val cause = IllegalArgumentException("SYNTHETIC_RESPONSE_BODY")
        val error = IllegalStateException("SYNTHETIC_CREDENTIAL_AND_PROMPT", cause)
        val text = LogPrivacy.exception("test", error)
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("java.lang.IllegalArgumentException"))
        assertTrue(text.contains("at "))
        assertFalse(text.contains("SYNTHETIC_"))
    }
    @Test fun malformedActionCannotLeakSubmittedText() {
        assertEquals("unknown", LogPrivacy.action("SYNTHETIC_CREDENTIAL"))
        assertEquals("unknown", LogPrivacy.action("ai_chat\nforged log"))
        assertEquals("ai_chat", LogPrivacy.action("ai_chat"))
    }
    @Test fun circularAndDeepCausesAreBounded() {
        val first = RuntimeException("ignored"); val second = RuntimeException("ignored")
        first.initCause(second); second.initCause(first)
        assertEquals(2, Regex("java.lang.RuntimeException").findAll(LogPrivacy.exception("test", first)).count())
        var chain: Throwable = RuntimeException()
        repeat(20) { chain = RuntimeException(null, chain) }
        assertEquals(8, Regex("java.lang.RuntimeException").findAll(LogPrivacy.exception("test", chain)).count())
    }
}
