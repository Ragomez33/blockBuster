package com.blockbuster.app.focus

import com.blockbuster.app.support.FakeFocusSessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionCoordinatorTest {

    private val repository = FakeFocusSessionRepository()
    private val coordinator = FocusSessionCoordinator(repository)

    @Test
    fun startTicksAndStopCancelsLoop() = runBlocking {
        coordinator.start()
        coordinator.start()

        withTimeout(3_000L) {
            while (repository.advancePhaseCalls == 0) {
                delay(50L)
            }
            assertTrue(repository.advancePhaseCalls >= 1)
        }

        coordinator.stop()
        val callsAfterStop = repository.advancePhaseCalls
        delay(1_200L)
        assertTrue(repository.advancePhaseCalls <= callsAfterStop + 1)
    }
}
