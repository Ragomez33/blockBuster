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
import com.blockbuster.core.overlay.focus.FocusModeRuleProvider
import com.blockbuster.core.overlay.matching.BlocklistMatcher
import com.blockbuster.core.overlay.premium.PremiumRuleProvider
import com.blockbuster.core.overlay.support.FakeBlocklistRepository
import com.blockbuster.core.overlay.support.FakeFocusSessionRepository
import com.blockbuster.core.overlay.support.FakePremiumRepository
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
class OverlayCoordinatorLifecycleTest {

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
    fun manualDismissalDoesNotReopenForSameDetection() = runTest {
        repository.activeRules.value = listOf(rule())
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()
        eventBus.publish(detection(timestamp = 1_000L))
        advanceUntilIdle()

        overlay.simulateUserDismiss()
        advanceUntilIdle()

        assertEquals(1, overlay.showCount)
        assertTrue(coordinator.state.value is OverlayState.Dismissed)

        advanceUntilIdle()
        assertEquals(1, overlay.showCount)
    }

    @Test
    fun newDetectionReactivatesAfterDismissal() = runTest {
        repository.activeRules.value = listOf(rule())
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()
        eventBus.publish(detection(timestamp = 1_000L))
        advanceUntilIdle()
        overlay.simulateUserDismiss()
        advanceUntilIdle()

        eventBus.publish(detection(timestamp = 2_000L))
        advanceUntilIdle()

        assertEquals(2, overlay.showCount)
        assertTrue(overlay.isOverlayVisible.value)
        assertTrue(coordinator.state.value is OverlayState.Blocking)
    }

    @Test
    fun targetChangeReplacesOverlayInsteadOfDuplicating() = runTest {
        repository.activeRules.value = listOf(
            rule(id = 1, pattern = "one.test"),
            rule(id = 2, pattern = "two.test"),
        )
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()
        eventBus.publish(detection(target = "https://one.test", timestamp = 1_000L))
        advanceUntilIdle()
        eventBus.publish(detection(target = "https://two.test", timestamp = 2_000L))
        advanceUntilIdle()

        assertEquals(2, overlay.showCount)
        assertEquals("two.test", overlay.shownConfig?.appLabel)
        assertTrue(overlay.isOverlayVisible.value)
    }

    private fun TestScope.createCoordinator(): OverlayCoordinator = coordinator(
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )

    private fun coordinator(scope: CoroutineScope) = OverlayCoordinator(
        context = context,
        eventBus = eventBus,
        protectionEventBus = MutableProtectionEventBus(),
        overlayController = overlay,
        repository = repository,
        blocklistRepository = FakeBlocklistRepository(),
        focusSessionRepository = FakeFocusSessionRepository(),
        premiumRepository = FakePremiumRepository(),
        matcher = DefaultBlockRuleMatcher(),
        blocklistMatcher = BlocklistMatcher(),
        focusModeRuleProvider = FocusModeRuleProvider(),
        premiumRuleProvider = PremiumRuleProvider(),
        scope = scope,
    )

    private fun detection(
        target: String = "https://example.com",
        timestamp: Long,
    ) = DetectionEvent.ScreenDetected(
        packageName = "com.android.chrome",
        className = "android.webkit.WebView",
        timestampEpochMillis = timestamp,
        target = target,
    )

    private fun rule(
        id: Long = 1,
        pattern: String = "example.com",
    ) = BlockRule(
        id = id,
        pattern = pattern,
        type = BlockRuleType.DOMAIN,
        isEnabled = true,
        createdAt = 1_000L,
    )
}
