package com.blockbuster.core.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.core.common.event.MutableBlockEventBus
import com.blockbuster.core.common.event.MutableProtectionEventBus
import com.blockbuster.core.common.model.BlocklistEntry
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType
import com.blockbuster.core.common.model.DetectionEvent
import com.blockbuster.core.overlay.focus.FocusModeRuleProvider
import com.blockbuster.core.overlay.matching.BlocklistMatcher
import com.blockbuster.core.overlay.matching.DefaultBlockRuleMatcher
import com.blockbuster.core.overlay.premium.PremiumRuleProvider
import com.blockbuster.core.overlay.support.FakeBlockRulesRepository
import com.blockbuster.core.overlay.support.FakeBlocklistRepository
import com.blockbuster.core.overlay.support.FakeFocusSessionRepository
import com.blockbuster.core.overlay.support.FakeOverlayController
import com.blockbuster.core.overlay.support.FakePremiumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverlayCoordinatorBlocklistTest {

    private lateinit var context: Context
    private lateinit var repository: FakeBlockRulesRepository
    private lateinit var blocklistRepository: FakeBlocklistRepository
    private lateinit var overlay: FakeOverlayController
    private lateinit var eventBus: MutableBlockEventBus

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = FakeBlockRulesRepository()
        blocklistRepository = FakeBlocklistRepository()
        overlay = FakeOverlayController()
        eventBus = MutableBlockEventBus()
    }

    @Test
    fun whitelistPreventsRuleMatch() = runTest {
        repository.activeRules.value = listOf(rule(1L, "example.com"))
        blocklistRepository.entries.value = listOf(
            BlocklistEntry(
                id = 1L,
                value = "example.com",
                targetType = BlocklistTargetType.WEBSITE,
                listType = BlocklistType.WHITELIST,
                createdAt = 1_000L,
            ),
        )
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()

        eventBus.publish(detection("https://example.com"))
        advanceUntilIdle()

        assertEquals(0, overlay.showCount)
        assertEquals(OverlayState.Idle, coordinator.state.value)
    }

    @Test
    fun blacklistEntryBlocksWithoutPersistedRule() = runTest {
        blocklistRepository.entries.value = listOf(
            BlocklistEntry(
                id = 1L,
                value = "example.com",
                targetType = BlocklistTargetType.WEBSITE,
                listType = BlocklistType.BLACKLIST,
                createdAt = 1_000L,
            ),
        )
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()

        eventBus.publish(detection("https://example.com"))
        advanceUntilIdle()

        assertEquals(1, overlay.showCount)
        org.junit.Assert.assertTrue(overlay.isOverlayVisible.value)
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
        blocklistRepository = blocklistRepository,
        focusSessionRepository = FakeFocusSessionRepository(),
        premiumRepository = FakePremiumRepository(),
        matcher = DefaultBlockRuleMatcher(),
        blocklistMatcher = BlocklistMatcher(),
        focusModeRuleProvider = FocusModeRuleProvider(),
        premiumRuleProvider = PremiumRuleProvider(),
        scope = scope,
    )

    private fun detection(target: String) = DetectionEvent.ScreenDetected(
        packageName = "com.android.chrome",
        className = "android.webkit.WebView",
        timestampEpochMillis = 1_000L,
        target = target,
    )

    private fun rule(id: Long, pattern: String) = com.blockbuster.core.common.model.BlockRule(
        id = id,
        pattern = pattern,
        type = com.blockbuster.core.common.model.BlockRuleType.DOMAIN,
        isEnabled = true,
        createdAt = 1_000L,
    )
}
