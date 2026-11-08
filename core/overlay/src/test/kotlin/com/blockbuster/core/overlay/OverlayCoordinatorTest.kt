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
class OverlayCoordinatorTest {

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
    fun showsOverlayWhenTargetMatchesActiveDomain() = runTest {
        repository.activeRules.value = listOf(rule(1, "example.com"))
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()

        eventBus.publish(detection("https://example.com/watch?v=1"))
        advanceUntilIdle()

        assertEquals(1, overlay.showCount)
        assertTrue(overlay.isOverlayVisible.value)
        assertTrue(coordinator.state.value is OverlayState.Blocking)
        assertEquals("example.com", overlay.shownConfig?.appLabel)
    }

    @Test
    fun showsOverlayWhenPackageSignatureMatches() = runTest {
        repository.activeRules.value = listOf(
            BlockRule(
                id = -1L,
                pattern = "com.zhiliaoapp.musically",
                type = BlockRuleType.PACKAGE,
                isEnabled = true,
                createdAt = 0L,
            ),
        )
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()

        eventBus.publish(
            DetectionEvent.ScreenDetected(
                packageName = "com.zhiliaoapp.musically",
                className = "com.ss.android.ugc.aweme.main.MainActivity",
                timestampEpochMillis = 1_000L,
                target = null,
            ),
        )
        advanceUntilIdle()

        assertEquals(1, overlay.showCount)
        assertTrue(overlay.isOverlayVisible.value)
        assertTrue(coordinator.state.value is OverlayState.Blocking)
    }

    @Test
    fun doesNotShowOverlayWithoutMatch() = runTest {
        repository.activeRules.value = listOf(rule(1, "example.com"))
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()

        eventBus.publish(detection("https://other.test"))
        advanceUntilIdle()

        assertEquals(0, overlay.showCount)
        assertEquals(OverlayState.Idle, coordinator.state.value)
    }

    @Test
    fun hidesOverlayWhenTargetChangesToUnmatchedValue() = runTest {
        repository.activeRules.value = listOf(rule(1, "example.com"))
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()
        eventBus.publish(detection("https://example.com"))
        advanceUntilIdle()

        eventBus.publish(detection("https://other.test", timestamp = 2_000L))
        advanceUntilIdle()

        assertTrue(overlay.hideCount >= 1)
        assertEquals(OverlayState.Idle, coordinator.state.value)
    }

    @Test
    fun hidesOverlayWhenRulesAreDisabled() = runTest {
        repository.activeRules.value = listOf(rule(1, "example.com"))
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()
        eventBus.publish(detection("https://example.com"))
        advanceUntilIdle()

        repository.activeRules.value = emptyList()
        advanceUntilIdle()

        assertTrue(overlay.hideCount >= 1)
        assertEquals(OverlayState.Idle, coordinator.state.value)
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
        target: String,
        timestamp: Long = 1_000L,
    ) = DetectionEvent.ScreenDetected(
        packageName = "com.android.chrome",
        className = "android.webkit.WebView",
        timestampEpochMillis = timestamp,
        target = target,
    )

    private fun rule(id: Long, pattern: String) = BlockRule(
        id = id,
        pattern = pattern,
        type = BlockRuleType.DOMAIN,
        isEnabled = true,
        createdAt = 1_000L,
    )
}
