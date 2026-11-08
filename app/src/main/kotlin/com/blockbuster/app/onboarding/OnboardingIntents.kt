package com.blockbuster.app.onboarding

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.blockbuster.app.R
import com.blockbuster.app.admin.BlockerDeviceAdminReceiver

/**
 * Abstraction over opening the system settings screens, so the ViewModel can be
 * exercised with a fake launcher in tests.
 */
interface OnboardingSettingsLauncher {
    fun openOverlaySettings(context: Context): Boolean

    fun openAccessibilitySettings(context: Context): Boolean

    fun openDeviceAdminSettings(context: Context): Boolean
}

/**
 * Opens the platform settings for each onboarding permission.
 *
 * Every intent is checked for resolution and launched inside [runCatching].
 * When the primary intent is unavailable, an ordered list of fallbacks is tried
 * so the user can always reach a useful settings screen.
 */
object OnboardingIntents : OnboardingSettingsLauncher {

    override fun openOverlaySettings(context: Context): Boolean =
        launchFirstResolvable(context, overlayIntents(context))

    override fun openAccessibilitySettings(context: Context): Boolean =
        launchFirstResolvable(context, accessibilityIntents(context))

    override fun openDeviceAdminSettings(context: Context): Boolean =
        launchFirstResolvable(context, deviceAdminIntents(context))

    fun overlayIntents(context: Context): List<Intent> = listOf(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun accessibilityIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun deviceAdminIntents(context: Context): List<Intent> {
        val admin = ComponentName(context, BlockerDeviceAdminReceiver::class.java)
        return listOf(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    context.getString(R.string.onboarding_step_admin_description),
                )
            },
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    internal fun launchFirstResolvable(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            val resolvable = runCatching {
                intent.resolveActivity(context.packageManager) != null
            }.getOrDefault(false)
            if (!resolvable) continue

            val launched = runCatching {
                context.startActivity(prepareForContext(context, intent))
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        return false
    }

    /**
     * DeviceAdminAdd refuses to start as a new task, so the flag is only added
     * when there is no activity to host the settings screen. This keeps the
     * device admin flow working when the screen is opened from the UI.
     */
    internal fun prepareForContext(context: Context, intent: Intent): Intent =
        if (context is Activity) {
            intent
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
