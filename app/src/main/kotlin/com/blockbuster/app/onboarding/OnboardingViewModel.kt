package com.blockbuster.app.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockbuster.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
    private val settingsLauncher: OnboardingSettingsLauncher,
) : ViewModel() {

    private val actionError = MutableStateFlow<Int?>(null)
    private val initialPermissions = repository.state.value

    val uiState: StateFlow<OnboardingUiState> = combine(
        repository.state,
        actionError,
    ) { permissions, error ->
        OnboardingUiState(
            permissions = permissions,
            isComplete = permissions.isComplete,
            actionError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = OnboardingUiState(
            permissions = initialPermissions,
            isComplete = initialPermissions.isComplete,
        ),
    )

    fun refresh() {
        repository.refresh()
    }

    fun openOverlaySettings(context: Context) {
        launchSettings(context, settingsLauncher::openOverlaySettings)
    }

    fun openAccessibilitySettings(context: Context) {
        launchSettings(context, settingsLauncher::openAccessibilitySettings)
    }

    fun openDeviceAdminSettings(context: Context) {
        launchSettings(context, settingsLauncher::openDeviceAdminSettings)
    }

    fun consumeError() {
        actionError.value = null
    }

    private fun launchSettings(context: Context, action: (Context) -> Boolean) {
        if (!action(context)) {
            actionError.value = R.string.onboarding_error_settings_unavailable
        }
    }
}

data class OnboardingUiState(
    val permissions: OnboardingState = OnboardingState(),
    val isComplete: Boolean = false,
    val actionError: Int? = null,
)
