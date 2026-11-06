package com.blockbuster.core.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrictionSessionTest {

    private val session = FrictionSession(
        target = "example.com",
        requiredText = "texto exacto",
        startedAtElapsedRealtime = 1_000L,
    )

    @Test
    fun startsAtSixtySeconds() {
        assertEquals(60_000L, session.remainingMillis(nowElapsedRealtime = 1_000L))
        assertEquals(60, session.remainingSeconds(nowElapsedRealtime = 1_000L))
    }

    @Test
    fun countsDownFromRemainingTime() {
        assertEquals(30_000L, session.remainingMillis(nowElapsedRealtime = 31_000L))
        assertEquals(30, session.remainingSeconds(nowElapsedRealtime = 31_000L))
        assertEquals(31, session.remainingSeconds(nowElapsedRealtime = 30_500L))
    }

    @Test
    fun canSubmitOnlyAfterTimerAndExactText() {
        assertFalse(
            session.copy(input = "texto exacto")
                .canSubmit(nowElapsedRealtime = 60_999L),
        )
        assertFalse(
            session.copy(input = "texto incorrecto")
                .canSubmit(nowElapsedRealtime = 61_000L),
        )
        assertTrue(
            session.copy(input = "texto exacto")
                .canSubmit(nowElapsedRealtime = 61_000L),
        )
    }

    @Test
    fun neverReportsNegativeRemainingTime() {
        assertEquals(0L, session.remainingMillis(nowElapsedRealtime = 90_000L))
        assertEquals(0, session.remainingSeconds(nowElapsedRealtime = 90_000L))
    }
}
