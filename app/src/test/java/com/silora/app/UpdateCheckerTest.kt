package com.silora.app

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {
    @Test fun versionsAreComparedNumerically() {
        assertTrue(UpdateChecker.newer("1.10.0", "1.9.9"))
        assertTrue(UpdateChecker.newer("2.0.0", "1.99.99"))
        assertFalse(UpdateChecker.newer("1.8.0", "1.8.0"))
        assertFalse(UpdateChecker.newer("1.7.1", "1.8.0"))
        for (bad in listOf("v1.8.0", "1.8", "1.8.0-beta", "999999999999999999999.0.0")) assertFalse(UpdateChecker.newer(bad, "1.0.0"))
    }
    @Test fun downloadsStayWithinSilora() {
        assertTrue(UpdateChecker.safePage("https://github.com/YCTS-otree/Silora/tree/main/Release/v1.8.0"))
        for (url in listOf("http://github.com/YCTS-otree/Silora/releases", "https://evil.com/YCTS-otree/Silora/", "https://github.com/YCTS-otree/Silora-fake/", "https://user@github.com/YCTS-otree/Silora/", "https://github.com/YCTS-otree/Silora/../other", "https://github.com/YCTS-otree/Silora/%2e%2e/other", "https://github.com/YCTS-otree/Silora/?redirect=evil", "javascript:alert(1)")) assertFalse(url,UpdateChecker.safePage(url))
    }
}
