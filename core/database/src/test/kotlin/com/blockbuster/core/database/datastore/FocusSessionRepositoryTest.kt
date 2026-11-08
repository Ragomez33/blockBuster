package com.blockbuster.core.database.datastore

import com.blockbuster.core.common.model.FocusPhase
import com.blockbuster.core.common.model.FocusRunState
import com.blockbuster.core.common.result.AppResult
import com.blockbuster.core.database.RepositoryTestFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusSessionRepositoryTest {

    private lateinit var fixture: RepositoryTestFixture

    @Before
    fun setUp() {
        fixture = RepositoryTestFixture()
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun defaultsToIdle() = runTest {
        val session = fixture.focusRepository.observeSession().first()

        assertEquals(FocusRunState.IDLE, session.runState)
        assertEquals(25, session.workMinutes)
        assertEquals(5, session.breakMinutes)
    }

    @Test
    fun startRunsWorkPhase() = runTest {
        val result = fixture.focusRepository.start(workMinutes = 10, breakMinutes = 2)

        assertTrue(result is AppResult.Success)
        val session = fixture.focusRepository.observeSession().first()
        assertEquals(FocusRunState.RUNNING, session.runState)
        assertEquals(FocusPhase.WORK, session.phase)
        assertTrue(session.remainingMillis() > 0L)
    }

    @Test
    fun invalidDurationsAreRejected() = runTest {
        val result = fixture.focusRepository.start(workMinutes = 0, breakMinutes = 5)

        assertTrue(result is AppResult.Failure)
        assertEquals(FocusRunState.IDLE, fixture.focusRepository.observeSession().first().runState)
    }

    @Test
    fun pauseAndResumePreserveRemainingTime() = runTest {
        fixture.focusRepository.start(workMinutes = 10, breakMinutes = 2)
        assertTrue(fixture.focusRepository.pause() is AppResult.Success)

        val paused = fixture.focusRepository.observeSession().first()
        assertEquals(FocusRunState.PAUSED, paused.runState)
        assertTrue((paused.pausedRemainingMillis ?: 0L) > 0L)

        assertTrue(fixture.focusRepository.resume() is AppResult.Success)
        assertEquals(
            FocusRunState.RUNNING,
            fixture.focusRepository.observeSession().first().runState,
        )
    }

    @Test
    fun stopReturnsToIdle() = runTest {
        fixture.focusRepository.start(workMinutes = 10, breakMinutes = 2)

        fixture.focusRepository.stop()

        val session = fixture.focusRepository.observeSession().first()
        assertEquals(FocusRunState.IDLE, session.runState)
        assertEquals(0L, session.remainingMillis())
    }

    @Test
    fun advancePhaseMovesWorkToBreakAndCountsCycle() = runTest {
        fixture.focusRepository.start(workMinutes = 10, breakMinutes = 2)
        val running = fixture.focusRepository.observeSession().first()
        val now = (running.phaseEndsAtEpochMillis ?: 0L) + 1L

        fixture.focusRepository.advancePhase(now)

        val session = fixture.focusRepository.observeSession().first()
        assertEquals(FocusPhase.BREAK, session.phase)
        assertEquals(1, session.completedWorkCycles)
        assertEquals(FocusRunState.RUNNING, session.runState)
    }
}
