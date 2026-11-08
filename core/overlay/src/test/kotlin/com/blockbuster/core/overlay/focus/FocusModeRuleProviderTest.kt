package com.blockbuster.core.overlay.focus

import com.blockbuster.core.common.model.FocusPhase
import com.blockbuster.core.common.model.FocusRunState
import com.blockbuster.core.common.model.FocusSession
import com.blockbuster.core.common.model.LeisureAppCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModeRuleProviderTest {

    private val provider = FocusModeRuleProvider()

    @Test
    fun generatesPackageRulesDuringWork() {
        val session = FocusSession(
            runState = FocusRunState.RUNNING,
            phase = FocusPhase.WORK,
            phaseEndsAtEpochMillis = Long.MAX_VALUE,
        )

        val rules = provider.rules(session)

        assertEquals(LeisureAppCatalog.packages.size, rules.size)
        assertTrue(rules.all { rule -> rule.id < 0L })
        assertTrue(rules.any { rule -> rule.pattern == "com.zhiliaoapp.musically" })
    }

    @Test
    fun noRulesDuringBreak() {
        val session = FocusSession(
            runState = FocusRunState.RUNNING,
            phase = FocusPhase.BREAK,
            phaseEndsAtEpochMillis = Long.MAX_VALUE,
        )

        assertTrue(provider.rules(session).isEmpty())
    }

    @Test
    fun noRulesWhenPausedOrIdle() {
        assertTrue(
            provider.rules(
                FocusSession(
                    runState = FocusRunState.PAUSED,
                    phase = FocusPhase.WORK,
                    pausedRemainingMillis = 60_000L,
                ),
            ).isEmpty(),
        )
        assertTrue(provider.rules(FocusSession()).isEmpty())
    }

    @Test
    fun noRulesAfterWorkPhaseExpires() {
        val session = FocusSession(
            runState = FocusRunState.RUNNING,
            phase = FocusPhase.WORK,
            phaseEndsAtEpochMillis = 1_000L,
        )

        assertTrue(provider.rules(session, nowEpochMillis = 2_000L).isEmpty())
    }
}
