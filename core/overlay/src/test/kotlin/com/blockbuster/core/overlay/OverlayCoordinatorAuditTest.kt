package com.blockbuster.core.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.core.common.event.MutableBlockEventBus
import com.blockbuster.core.common.event.MutableProtectionEventBus
import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.model.DetectionEvent
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
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
class OverlayCoordinatorAuditTest {

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
    fun auditsOnlyOnceForRepeatedDetectionsOfSameActivation() = runTest {
        repository.activeRules.value = listOf(rule(1, "example.com"))
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()

        eventBus.publish(detection("https://example.com", 1_000L))
        advanceUntilIdle()
        eventBus.publish(detection("https://example.com", 2_000L))
        advanceUntilIdle()

        assertEquals(1, repository.recordBlockCalls.size)
        assertEquals(1, repository.blockLogs.size)
        assertEquals(1, overlay.showCount)
    }

    @Test
    fun auditsNewTargetAsNewActivation() = runTest {
        repository.activeRules.value = listOf(
            rule(1, "one.test"),
            rule(2, "two.test"),
        )
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()

        eventBus.publish(detection("https://one.test", 1_000L))
        advanceUntilIdle()
        eventBus.publish(detection("https://two.test", 2_000L))
        advanceUntilIdle()

        assertEquals(2, repository.recordBlockCalls.size)
        assertEquals(2, overlay.showCount)
    }

    @Test
    fun auditFailureDoesNotHideOverlay() = runTest {
        repository.recordBlockResult = AppResult.Failure(
            AppError.Storage("Simulated audit failure"),
        )
        repository.activeRules.value = listOf(rule(1, "example.com"))
        val coordinator = createCoordinator()
        coordinator.start()
        advanceUntilIdle()

        eventBus.publish(detection("https://example.com", 1_000L))
        advanceUntilIdle()

        assertEquals(1, repository.recordBlockCalls.size)
        assertEquals(1, overlay.showCount)
        assertTrue(overlay.isOverlayVisible.value)
        assertTrue(coordinator.state.value is OverlayState.Blocking)
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
        matcher = DefaultBlockRuleMatcher(),
        scope = scope,
    )

    private fun detection(target: String, timestamp: Long) =
        DetectionEvent.ScreenDetected(
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
