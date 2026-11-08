package com.blockbuster.app.onboarding

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.app.protection.PermissionStatusProvider
import com.blockbuster.app.protection.ProtectionHealthChecker
import com.blockbuster.core.accessibility.BlockBusterAccessibilityService
import com.blockbuster.core.common.contract.AccessibilityConnectionState
import com.blockbuster.core.common.contract.AccessibilityEngine
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun incompleteWhenAnyPermissionIsMissing() {
        val repository = OnboardingRepository(
            checker = FakeChecker(overlay = true, accessibility = true, admin = false),
            accessibilityEngine = FakeAccessibilityEngine(),
        )

        assertFalse(repository.state.value.isComplete)
        assertTrue(repository.state.value.overlayGranted)
        assertTrue(repository.state.value.accessibilityEnabled)
        assertFalse(repository.state.value.deviceAdminActive)
    }

    @Test
    fun completeOnlyWhenAllPermissionsAreActive() {
        val repository = OnboardingRepository(
            checker = FakeChecker(overlay = true, accessibility = true, admin = true),
            accessibilityEngine = FakeAccessibilityEngine(),
        )

        assertTrue(repository.state.value.isComplete)
    }

    @Test
    fun refreshReevaluatesPermissions() {
        val checker = FakeChecker(overlay = false, accessibility = false, admin = false)
        val repository = OnboardingRepository(checker, FakeAccessibilityEngine())

        checker.overlay = true
        checker.accessibility = true
        checker.admin = true
        repository.refresh()

        assertTrue(repository.state.value.isComplete)

        checker.overlay = false
        repository.refresh()

        assertFalse(repository.state.value.isComplete)
    }

    @Test
    fun accessibilityConnectionChangeUpdatesStateWithoutManualRefresh() {
        val checker = FakeChecker(overlay = true, accessibility = false, admin = true)
        val engine = FakeAccessibilityEngine()
        val repository = OnboardingRepository(checker, engine)

        assertFalse(repository.state.value.isComplete)

        checker.accessibility = true
        engine.connectionState.value = AccessibilityConnectionState.CONNECTED

        assertTrue(repository.state.value.isComplete)
    }

    @Test
    fun accessibilityDetectionMatchesFullComponentNameOnly() {
        val checker = ProtectionHealthChecker(context)

        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            context.packageName,
        )
        assertFalse(checker.isAccessibilityEnabled())

        val component = ComponentName(context, BlockBusterAccessibilityService::class.java)
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            component.flattenToString(),
        )
        assertTrue(checker.isAccessibilityEnabled())
    }

    private class FakeChecker(
        var overlay: Boolean,
        var accessibility: Boolean,
        var admin: Boolean,
    ) : PermissionStatusProvider {
        override fun isOverlayGranted(): Boolean = overlay
        override fun isAccessibilityEnabled(): Boolean = accessibility
        override fun isDeviceAdminActive(): Boolean = admin
    }

    private class FakeAccessibilityEngine : AccessibilityEngine {
        override val connectionState =
            MutableStateFlow(AccessibilityConnectionState.DISCONNECTED)
    }
}
