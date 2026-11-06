package com.blockbuster.core.accessibility.settings

import com.blockbuster.core.common.model.SensitiveScreenCategory
import com.blockbuster.core.common.model.SensitiveSystemScreen
import javax.inject.Inject

/**
 * Heuristic classifier for system screens that can disable protection. It is
 * intentionally conservative: unknown OEM screens return null and are not
 * blocked.
 */
class DefaultSettingsInterceptor @Inject constructor() : SettingsInterceptor {

    override fun inspect(packageName: String?, className: String?): SensitiveSystemScreen? {
        val pkg = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val cls = className?.trim()?.lowercase().orEmpty()
        if (!isSettingsPackage(pkg)) return null

        val category = when {
            "accessibility" in cls -> SensitiveScreenCategory.ACCESSIBILITY_SETTINGS
            "deviceadmin" in cls || "deviceadministration" in cls ->
                SensitiveScreenCategory.DEVICE_ADMIN_SETTINGS
            APP_INFO_MARKERS.any { marker -> marker in cls } -> SensitiveScreenCategory.APP_INFO
            else -> return null
        }

        return SensitiveSystemScreen(
            category = category,
            packageName = pkg,
            className = className,
            detectedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun isSettingsPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return lower == "com.android.settings" ||
            lower == "com.google.android.settings" ||
            "settings" in lower
    }

    private companion object {
        val APP_INFO_MARKERS = listOf(
            "installedappdetails",
            "appinfo",
            "applicationinfo",
            "uninstall",
        )
    }
}
