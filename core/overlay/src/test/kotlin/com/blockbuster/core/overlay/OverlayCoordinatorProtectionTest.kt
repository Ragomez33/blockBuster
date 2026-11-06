package com.blockbuster.core.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.core.common.contract.FrictionMode
import com.blockbuster.core.common.event.MutableBlockEventBus
import com.blockbuster.core.common.event.MutableProtectionEventBus
import com.blockbuster.core.common.model.ProtectionThreat
import com.blockbuster.core.common.model.ProtectionThreatType
import com.blockbuster.core.common.model.SensitiveScreenCategory
import com.blockbuster.core.common.model.SensitiveSystemScreen
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
class OverlayCoordinatorProtectionTest {

    private lateinit var context: Context
    private lateinit var repository: FakeBlockRulesRepository
    private lateinit var overlay: FakeOverlayController
    private lateinit var eventBus: MutableBlockEventBus
    private lateinit var protectionEventBus: MutableProtectionEventBus

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = FakeBlockRulesRepository()
        overlay = FakeOverlayController()
        eventBus = MutableBlockEventBus()
        protectionEventBus = MutableProtectionEventBus()
    }

    @Test
    fun sensitiveScreenShowsProtectionModeAndClearsOnExit() = runTest {
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()

        protectionEventBus.publish(
            ProtectionThreat(
                type = ProtectionThreatType.SENSITIVE_SCREEN,
                screen = screen(),
                timestampEpochMillis = 1_000L,
            ),
        )
        advanceUntilIdle()

        assertEquals(1, overlay.showCount)
        assertEquals(FrictionMode.PROTECTION, overlay.shownConfig?.mode)
        assertTrue(coordinator.state.value is OverlayState.ProtectionBlocking)

        protectionEventBus.clear()
        advanceUntilIdle()

        assertTrue(overlay.hideCount >= 1)
        assertEquals(OverlayState.Idle, coordinator.state.value)
    }

    @Test
    fun adminThreatAlsoUsesProtectionMode() = runTest {
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()

        protectionEventBus.publish(
            ProtectionThreat(
                type = ProtectionThreatType.ADMIN_DISABLE_REQUESTED,
                screen = null,
                timestampEpochMillis = 2_000L,
            ),
        )
        advanceUntilIdle()

        assertEquals(FrictionMode.PROTECTION, overlay.shownConfig?.mode)
        assertTrue(coordinator.state.value is OverlayState.ProtectionBlocking)
    }

    private fun TestScope.createCoordinator(): OverlayCoordinator = OverlayCoordinator(
        context = context,
        eventBus = eventBus,
        protectionEventBus = protectionEventBus,
        overlayController = overlay,
        repository = repository,
        matcher = DefaultBlockRuleMatcher(),
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )

    private fun screen() = SensitiveSystemScreen(
        category = SensitiveScreenCategory.ACCESSIBILITY_SETTINGS,
        packageName = "com.android.settings",
        className = "com.android.settings.accessibility.AccessibilitySettings",
        detectedAtEpochMillis = 1_000L,
    )
}
