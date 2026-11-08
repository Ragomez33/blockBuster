package com.blockbuster.app.onboarding

import com.blockbuster.app.protection.PermissionStatusProvider
import com.blockbuster.core.common.contract.AccessibilityEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for onboarding progress.
 *
 * The first value is computed synchronously so the UI never shows the dashboard
 * before permissions have been checked. [refresh] recomputes the state from the
 * operating system, and accessibility connection changes are observed so the
 * accessibility card can update while the app is visible.
 */
@Singleton
class OnboardingRepository @Inject constructor(
    private val checker: PermissionStatusProvider,
    accessibilityEngine: AccessibilityEngine,
) {
    private val _state = MutableStateFlow(evaluate())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    init {
        scope.launch {
            accessibilityEngine.connectionState.collect {
                _state.value = evaluate()
            }
        }
    }

    fun refresh() {
        _state.value = evaluate()
    }

    private fun evaluate(): OnboardingState = OnboardingState(
        overlayGranted = runCatching { checker.isOverlayGranted() }.getOrDefault(false),
        accessibilityEnabled = runCatching { checker.isAccessibilityEnabled() }
            .getOrDefault(false),
        deviceAdminActive = runCatching { checker.isDeviceAdminActive() }
            .getOrDefault(false),
    )
}
