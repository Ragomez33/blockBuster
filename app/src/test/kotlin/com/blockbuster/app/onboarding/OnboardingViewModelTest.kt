package com.blockbuster.app.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.app.R
import com.blockbuster.app.protection.PermissionStatusProvider
import com.blockbuster.core.common.contract.AccessibilityConnectionState
import com.blockbuster.core.common.contract.AccessibilityEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun failedSettingsLaunchPublishesError() {
        val viewModel = createViewModel(launchSucceeds = false)

        viewModel.openOverlaySettings(context)

        assertEquals(
            R.string.onboarding_error_settings_unavailable,
            viewModel.uiState.value.actionError,
        )
    }

    @Test
    fun consumeErrorClearsTheMessage() {
        val viewModel = createViewModel(launchSucceeds = false)
        viewModel.openAccessibilitySettings(context)

        viewModel.consumeError()

        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun successfulSettingsLaunchDoesNotPublishError() {
        val viewModel = createViewModel(launchSucceeds = true)

        viewModel.openDeviceAdminSettings(context)

        assertNull(viewModel.uiState.value.actionError)
    }

    private fun createViewModel(launchSucceeds: Boolean): OnboardingViewModel {
        val repository = OnboardingRepository(
            checker = AlwaysGrantedChecker,
            accessibilityEngine = FakeAccessibilityEngine(),
        )
        return OnboardingViewModel(
            repository = repository,
            settingsLauncher = FakeLauncher(launchSucceeds),
        )
    }

    private object AlwaysGrantedChecker : PermissionStatusProvider {
        override fun isOverlayGranted(): Boolean = true
        override fun isAccessibilityEnabled(): Boolean = true
        override fun isDeviceAdminActive(): Boolean = true
    }

    private class FakeAccessibilityEngine : AccessibilityEngine {
        override val connectionState =
            MutableStateFlow(AccessibilityConnectionState.DISCONNECTED)
    }

    private class FakeLauncher(private val result: Boolean) : OnboardingSettingsLauncher {
        override fun openOverlaySettings(context: Context): Boolean = result
        override fun openAccessibilitySettings(context: Context): Boolean = result
        override fun openDeviceAdminSettings(context: Context): Boolean = result
    }
}
