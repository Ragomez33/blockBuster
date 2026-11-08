package com.blockbuster.app.ui.blocking

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockbuster.app.R
import com.blockbuster.app.service.MonitoringForegroundService
import com.blockbuster.core.common.contract.AccessibilityConnectionState
import com.blockbuster.core.common.contract.AccessibilityEngine
import com.blockbuster.core.common.contract.BlockRulesRepository
import com.blockbuster.core.common.contract.PremiumRepository
import com.blockbuster.core.common.contract.ProtectionPresetRepository
import com.blockbuster.core.common.model.ProtectionPreset
import com.blockbuster.core.common.result.AppResult
import com.blockbuster.core.database.preset.ProtectionPresetCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BlockingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val presetRepository: ProtectionPresetRepository,
    blockRulesRepository: BlockRulesRepository,
    accessibilityEngine: AccessibilityEngine,
    private val premiumRepository: PremiumRepository,
) : ViewModel() {

    private val actionError = MutableStateFlow<Int?>(null)
    private val premiumState = combine(
        premiumRepository.observePremiumState(),
        premiumRepository.observeSettings(),
        actionError,
    ) { premium, settings, error -> Triple(premium, settings, error) }

    val uiState: StateFlow<BlockingUiState> = combine(
        presetRepository.observeEnabledPresets(),
        blockRulesRepository.observeActiveRules(),
        accessibilityEngine.connectionState,
        MonitoringForegroundService.isRunning,
        premiumState,
    ) { enabledPresets, activeRules, connectionState, monitoringRunning, premium ->
        val (premiumState, settings, error) = premium
        BlockingUiState(
            presets = ProtectionPreset.entries.map { preset ->
                PresetSwitchUiState(
                    preset = preset,
                    enabled = preset in enabledPresets,
                    ruleCount = ProtectionPresetCatalog.ruleCount(preset),
                )
            },
            activeProtectionCount = activeRules.size,
            accessibilityConnected = connectionState == AccessibilityConnectionState.CONNECTED,
            monitoringRunning = monitoringRunning,
            isPremium = premiumState.isPremium,
            strictShortsReels = settings.strictShortsReels,
            frictionWindowMillis = settings.frictionWindowMillis,
            accountabilityPartner = settings.accountabilityPartner,
            actionError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = initialUiState(),
    )

    fun setPresetEnabled(preset: ProtectionPreset, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                presetRepository.setPresetEnabled(preset, enabled)
            }.onFailure {
                actionError.value = R.string.dashboard_error_preset
            }
        }
    }

    fun setStrictShortsReels(enabled: Boolean) {
        viewModelScope.launch {
            if (premiumRepository.setStrictShortsReels(enabled) is AppResult.Failure) {
                actionError.value = R.string.premium_error_save
            }
        }
    }

    fun setFrictionWindowMillis(value: Long) {
        viewModelScope.launch {
            if (premiumRepository.setFrictionWindowMillis(value) is AppResult.Failure) {
                actionError.value = R.string.premium_error_save
            }
        }
    }

    fun setAccountabilityPartner(value: String?) {
        viewModelScope.launch {
            if (premiumRepository.setAccountabilityPartner(value) is AppResult.Failure) {
                actionError.value = R.string.premium_error_save
            }
        }
    }

    fun startMonitoring() {
        MonitoringForegroundService.start(context)
    }

    fun consumeError() {
        actionError.value = null
    }

    private fun initialUiState(): BlockingUiState = BlockingUiState(
        presets = ProtectionPreset.entries.map { preset ->
            PresetSwitchUiState(
                preset = preset,
                enabled = false,
                ruleCount = ProtectionPresetCatalog.ruleCount(preset),
            )
        },
    )
}

data class PresetSwitchUiState(
    val preset: ProtectionPreset,
    val enabled: Boolean,
    val ruleCount: Int,
)

data class BlockingUiState(
    val presets: List<PresetSwitchUiState> = emptyList(),
    val activeProtectionCount: Int = 0,
    val accessibilityConnected: Boolean = false,
    val monitoringRunning: Boolean = false,
    val isPremium: Boolean = false,
    val strictShortsReels: Boolean = false,
    val frictionWindowMillis: Long = 60_000L,
    val accountabilityPartner: String? = null,
    val actionError: Int? = null,
)
