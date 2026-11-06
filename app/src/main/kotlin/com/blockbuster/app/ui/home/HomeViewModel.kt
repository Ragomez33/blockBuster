package com.blockbuster.app.ui.home

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockbuster.app.service.MonitoringForegroundService
import com.blockbuster.core.common.contract.AccessibilityConnectionState
import com.blockbuster.core.common.contract.AccessibilityEngine
import com.blockbuster.core.common.contract.BlockRulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    accessibilityEngine: AccessibilityEngine,
    repository: BlockRulesRepository,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0L)

    val uiState: StateFlow<HomeUiState> = combine(
        accessibilityEngine.connectionState,
        repository.observeAllRules(),
        MonitoringForegroundService.isRunning,
        refreshTrigger,
    ) { connectionState, rules, monitoringRunning, _ ->
        HomeUiState(
            accessibilityConnected = connectionState == AccessibilityConnectionState.CONNECTED,
            overlayPermissionGranted = Settings.canDrawOverlays(context),
            monitoringRunning = monitoringRunning,
            activeRuleCount = rules.count { rule -> rule.isEnabled },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun refresh() {
        refreshTrigger.update { current -> current + 1 }
    }

    fun startMonitoring() {
        MonitoringForegroundService.start(context)
        refresh()
    }
}

data class HomeUiState(
    val accessibilityConnected: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val monitoringRunning: Boolean = false,
    val activeRuleCount: Int = 0,
)
