package com.blockbuster.app.onboarding

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.app.admin.BlockerDeviceAdminReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingIntentsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun overlayPrimaryIntentTargetsThisPackage() {
        val intent = OnboardingIntents.overlayIntents(context).first()

        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals(Uri.parse("package:${context.packageName}"), intent.data)
    }

    @Test
    fun overlayFallbacksEndWithGeneralSettings() {
        val actions = OnboardingIntents.overlayIntents(context).map { it.action }

        assertEquals(
            listOf(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
                Settings.ACTION_SETTINGS,
            ),
            actions,
        )
    }

    @Test
    fun accessibilityPrimaryIntentOpensAccessibilitySettings() {
        val intent = OnboardingIntents.accessibilityIntents(context).first()

        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
    }

    @Test
    fun accessibilityFallsBackToGeneralSettings() {
        val actions = OnboardingIntents.accessibilityIntents(context).map { it.action }

        assertEquals(
            listOf(Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS),
            actions,
        )
    }

    @Test
    fun deviceAdminIntentCarriesTheReceiverComponent() {
        val intent = OnboardingIntents.deviceAdminIntents(context).first()
        val expected = ComponentName(context, BlockerDeviceAdminReceiver::class.java)

        val extra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName::class.java,
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN)
        }

        assertEquals(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN, intent.action)
        assertEquals(expected, extra)
        assertTrue(
            intent.getStringExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION).orEmpty().isNotEmpty(),
        )
    }

    @Test
    fun deviceAdminFallbacksEndWithGeneralSettings() {
        val actions = OnboardingIntents.deviceAdminIntents(context).map { it.action }

        assertEquals(
            listOf(
                DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN,
                Settings.ACTION_SECURITY_SETTINGS,
                Settings.ACTION_SETTINGS,
            ),
            actions,
        )
    }

    @Test
    fun unresolvableIntentReturnsFailureWithoutThrowing() {
        val launched = OnboardingIntents.launchFirstResolvable(
            context,
            listOf(Intent("com.blockbuster.app.UNRESOLVABLE_ACTION")),
        )

        assertFalse(launched)
    }

    @Test
    fun emptyIntentListReturnsFailure() {
        assertFalse(OnboardingIntents.launchFirstResolvable(context, emptyList()))
    }

    @Test
    fun activityContextDoesNotForceNewTask() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val prepared = OnboardingIntents.prepareForContext(
            activity,
            Intent(Settings.ACTION_SETTINGS),
        )

        assertEquals(0, prepared.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun applicationContextAddsNewTaskFlag() {
        val prepared = OnboardingIntents.prepareForContext(
            context,
            Intent(Settings.ACTION_SETTINGS),
        )

        assertTrue(prepared.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
