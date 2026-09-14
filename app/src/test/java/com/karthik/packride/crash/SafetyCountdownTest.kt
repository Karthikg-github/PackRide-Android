package com.karthik.packride.crash

import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyCountdownTest {
    @Test fun roundsUpSoAnAlertNeverFiresEarly() {
        assertEquals(30, SafetyCountdown.secondsRemaining(30_000, 1))
        assertEquals(1, SafetyCountdown.secondsRemaining(30_000, 29_999))
    }

    @Test fun expiredDeadlineReturnsZero() {
        assertEquals(0, SafetyCountdown.secondsRemaining(30_000, 30_000))
        assertEquals(0, SafetyCountdown.secondsRemaining(30_000, 45_000))
    }
}
