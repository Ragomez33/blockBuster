package com.blockbuster.core.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.core.common.event.MutableBlockEventBus
import com.blockbuster.core.common.event.MutableProtectionEventBus
import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.model.DetectionEvent
import com.blockbuster.core.overlay.matching.DefaultBlockRuleMatcher
import com.blockbuster.core.overlay.support.FakeBlockRulesRepository
import com.blockbuster.core.overlay.support.FakeOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverlayCoordinatorUnlockTest {

    private lateinit var context: Context
    private lateinit var repository: FakeBlockRulesRepository
    private lateinit var overlay: FakeOverlayController
    private lateinit var eventBus: MutableBlockEventBus

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = FakeBlockRulesRepository()
        overlay = FakeOverlayController()
        eventBus = MutableBlockEventBus()
    }

    @Test
    fun unlockHidesOverlayReturnsToIdleAndDoesNotAuditAgain() = runTest {
        repository.activeRules.value = listOf(rule())
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()

        eventBus.publish(detection())
        advanceUntilIdle()
        assertTrue(coordinator.state.value is OverlayState.Blocking)
        assertEquals(1, repository.recordBlockCalls.size)

        overlay.emitUnlock(target = "https://example.com", timestamp = 2_000L)
        advanceUntilIdle()

        assertTrue(overlay.hideCount >= 1)
        assertEquals(OverlayState.Idle, coordinator.state.value)
        assertEquals(1, repository.recordBlockCalls.size)
    }

    private fun TestScope.createCoordinator(): OverlayCoordinator = OverlayCoordinator(
        context = context,
        eventBus = eventBus,
        protectionEventBus = MutableProtectionEventBus(),
        overlayController = overlay,
        repository = repository,
        matcher = DefaultBlockRuleMatcher(),
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )

    private fun detection() = DetectionEvent.ScreenDetected(
        packageName = "com.android.chrome",
        className = "android.webkit.WebView",
        timestampEpochMillis = 1_000L,
        target = "https://example.com",
    )

    private fun rule() = BlockRule(
        id = 1,
        pattern = "example.com",
        type = BlockRuleType.DOMAIN,
        isEnabled = true,
        createdAt = 1_000L,
    )
}
