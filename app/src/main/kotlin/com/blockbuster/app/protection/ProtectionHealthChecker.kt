package com.blockbuster.app.protection

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.blockbuster.app.admin.BlockerDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ProtectionHealth(
    val accessibilityEnabled: Boolean,
    val overlayGranted: Boolean,
    val deviceAdminActive: Boolean,
    val checkedAtEpochMillis: Long,
)

@Singleton
class ProtectionHealthChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun check(): ProtectionHealth = ProtectionHealth(
        accessibilityEnabled = isAccessibilityEnabled(),
        overlayGranted = Settings.canDrawOverlays(context),
        deviceAdminActive = isDeviceAdminActive(),
        checkedAtEpochMillis = System.currentTimeMillis(),
    )

    private fun isAccessibilityEnabled(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val enabledServices = manager?.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        ).orEmpty()
        if (enabledServices.any { info ->
                info.resolveInfo?.serviceInfo?.packageName == context.packageName
            }
        ) {
            return true
        }

        val secureValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return secureValue.contains(context.packageName)
    }

    private fun isDeviceAdminActive(): Boolean {
        val policyManager = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        val component = ComponentName(context, BlockerDeviceAdminReceiver::class.java)
        return policyManager.isAdminActive(component)
    }
}
