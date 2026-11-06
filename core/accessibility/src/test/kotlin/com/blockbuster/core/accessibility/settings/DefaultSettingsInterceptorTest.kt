package com.blockbuster.core.accessibility.settings

import com.blockbuster.core.common.model.SensitiveScreenCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultSettingsInterceptorTest {

    private val interceptor = DefaultSettingsInterceptor()

    @Test
    fun detectsAccessibilitySettings() {
        val screen = interceptor.inspect(
            "com.android.settings",
            "com.android.settings.accessibility.AccessibilitySettings",
        )

        assertNotNull(screen)
        assertEquals(SensitiveScreenCategory.ACCESSIBILITY_SETTINGS, screen?.category)
    }

    @Test
    fun detectsAppInfoAndUninstall() {
        val appInfo = interceptor.inspect(
            "com.android.settings",
            "com.android.settings.applications.InstalledAppDetails",
        )
        val uninstall = interceptor.inspect(
            "com.android.settings",
            "com.android.settings.applications.appinfo.UninstallAppProgress",
        )

        assertEquals(SensitiveScreenCategory.APP_INFO, appInfo?.category)
        assertEquals(SensitiveScreenCategory.APP_INFO, uninstall?.category)
    }

    @Test
    fun detectsDeviceAdminSettings() {
        val screen = interceptor.inspect(
            "com.android.settings",
            "com.android.settings.deviceadmin.DeviceAdminSettings",
        )

        assertEquals(SensitiveScreenCategory.DEVICE_ADMIN_SETTINGS, screen?.category)
    }

    @Test
    fun detectsOemSettingsPackage() {
        val screen = interceptor.inspect(
            "com.samsung.android.settings",
            "com.samsung.android.settings.accessibility.AccessibilitySettings",
        )

        assertEquals(SensitiveScreenCategory.ACCESSIBILITY_SETTINGS, screen?.category)
    }

    @Test
    fun ignoresNonSensitiveAndNonSettingsPackages() {
        assertNull(interceptor.inspect("com.android.settings", "com.android.settings.Settings"))
        assertNull(interceptor.inspect("com.example.app", "com.example.AppInfoActivity"))
        assertNull(interceptor.inspect(null, null))
    }
}
