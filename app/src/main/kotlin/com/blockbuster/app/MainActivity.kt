package com.blockbuster.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.blockbuster.app.focus.FocusSessionCoordinator
import com.blockbuster.app.navigation.BlockBusterNavHost
import com.blockbuster.app.onboarding.OnboardingUiState
import com.blockbuster.app.onboarding.OnboardingViewModel
import com.blockbuster.app.ui.onboarding.OnboardingScreen
import com.blockbuster.app.ui.theme.BlockBusterTheme
import com.blockbuster.core.common.contract.PremiumRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var focusSessionCoordinator: FocusSessionCoordinator

    @Inject
    lateinit var premiumRepository: PremiumRepository

    private val onboardingViewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        focusSessionCoordinator.start()
        enableEdgeToEdge()
        setContent {
            BlockBusterTheme {
                val onboardingState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current

                BlockBusterRoot(
                    uiState = onboardingState,
                    onOpenOverlaySettings = {
                        onboardingViewModel.openOverlaySettings(context)
                    },
                    onOpenAccessibilitySettings = {
                        onboardingViewModel.openAccessibilitySettings(context)
                    },
                    onOpenDeviceAdminSettings = {
                        onboardingViewModel.openDeviceAdminSettings(context)
                    },
                    onConsumeError = onboardingViewModel::consumeError,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        onboardingViewModel.refresh()
        lifecycleScope.launch {
            premiumRepository.checkSubscriptionStatus()
        }
    }
}

@Composable
private fun BlockBusterRoot(
    uiState: OnboardingUiState,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenDeviceAdminSettings: () -> Unit,
    onConsumeError: () -> Unit,
) {
    if (uiState.isComplete) {
        BlockBusterNavHost()
    } else {
        OnboardingScreen(
            uiState = uiState,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onOpenDeviceAdminSettings = onOpenDeviceAdminSettings,
            onConsumeError = onConsumeError,
        )
    }
}
