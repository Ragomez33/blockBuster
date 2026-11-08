package com.blockbuster.app.onboarding

/**
 * Snapshot of the three critical system permissions required by BlockBuster.
 * The state is always derived from the operating system, never persisted.
 */
data class OnboardingState(
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val deviceAdminActive: Boolean = false,
) {
    val isComplete: Boolean
        get() = overlayGranted && accessibilityEnabled && deviceAdminActive
}
