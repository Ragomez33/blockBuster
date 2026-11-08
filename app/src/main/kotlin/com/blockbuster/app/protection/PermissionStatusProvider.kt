package com.blockbuster.app.protection

/**
 * Small seam over the platform permission checks so onboarding state logic can
 * be tested without touching real system services.
 */
interface PermissionStatusProvider {
    fun isOverlayGranted(): Boolean

    fun isAccessibilityEnabled(): Boolean

    fun isDeviceAdminActive(): Boolean
}
