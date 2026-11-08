package com.blockbuster.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockbuster.app.R
import com.blockbuster.core.common.contract.PremiumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    premiumRepository: PremiumRepository,
) : ViewModel() {

    private val updateResult = MutableStateFlow<Int?>(null)
    private val isCheckingUpdates = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        premiumRepository.observePremiumState(),
        updateResult,
        isCheckingUpdates,
    ) { premium, result, checking ->
        SettingsUiState(
            isPremium = premium.isPremium,
            updateResult = result,
            isCheckingUpdates = checking,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun checkForUpdates() {
        if (isCheckingUpdates.value) return
        viewModelScope.launch {
            isCheckingUpdates.value = true
            delay(SIMULATED_CHECK_MILLIS)
            updateResult.value = R.string.settings_update_result
            isCheckingUpdates.value = false
        }
    }

    private companion object {
        const val SIMULATED_CHECK_MILLIS = 700L
    }
}

data class SettingsUiState(
    val isPremium: Boolean = false,
    val updateResult: Int? = null,
    val isCheckingUpdates: Boolean = false,
)
