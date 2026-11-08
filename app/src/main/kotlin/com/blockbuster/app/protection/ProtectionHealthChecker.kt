package com.blockbuster.app.protection

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.blockbuster.app.admin.BlockerDeviceAdminReceiver
import com.blockbuster.core.accessibility.BlockBusterAccessibilityService
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
) : PermissionStatusProvider {

    fun check(): ProtectionHealth = ProtectionHealth(
        accessibilityEnabled = isAccessibilityEnabled(),
        overlayGranted = isOverlayGranted(),
        deviceAdminActive = isDeviceAdminActive(),
        checkedAtEpochMillis = System.currentTimeMillis(),
    )

    override fun isOverlayGranted(): Boolean = Settings.canDrawOverlays(context)

    override fun isAccessibilityEnabled(): Boolean {
        val component = accessibilityComponent()

        val manager = context.getSystemService(AccessibilityManager::class.java)
        val enabledServices = manager?.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        ).orEmpty()
        if (enabledServices.any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                ComponentName(serviceInfo.packageName, serviceInfo.name).matches(component)
            }
        ) {
            return true
        }

        return isServiceListedInSecureSettings(component)
    }

    override fun isDeviceAdminActive(): Boolean {
        val policyManager = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return policyManager.isAdminActive(adminComponent())
    }

    private fun isServiceListedInSecureSettings(component: ComponentName): Boolean {
        val secureValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        if (secureValue.isBlank()) return false

        return secureValue.split(':').any { entry ->
            ComponentName.unflattenFromString(entry)?.matches(component) == true
        }
    }

    private fun accessibilityComponent(): ComponentName =
        ComponentName(context, BlockBusterAccessibilityService::class.java)

    private fun adminComponent(): ComponentName =
        ComponentName(context, BlockerDeviceAdminReceiver::class.java)

    private fun ComponentName.matches(expected: ComponentName): Boolean =
        this == expected ||
            (packageName == expected.packageName && expected.className.endsWith(className))
}
