package com.blockbuster.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockbuster.app.R
import com.blockbuster.core.common.contract.FocusSessionRepository
import com.blockbuster.core.common.model.FocusRunState
import com.blockbuster.core.common.model.FocusSession
import com.blockbuster.core.common.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FocusModeViewModel @Inject constructor(
    private val repository: FocusSessionRepository,
) : ViewModel() {

    private val actionError = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<FocusModeUiState> = combine(
        repository.observeSession(),
        tickerFlow(),
        actionError,
    ) { session, currentTime, error ->
        FocusModeUiState(
            session = session,
            remainingMillis = session.remainingMillis(currentTime),
            actionError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FocusModeUiState(),
    )

    fun start(workMinutes: Int, breakMinutes: Int) {
        viewModelScope.launch {
            when (val result = repository.start(workMinutes, breakMinutes)) {
                is AppResult.Success -> actionError.value = null
                is AppResult.Failure -> actionError.value = R.string.focus_error_duration
            }
        }
    }

    fun pause() {
        launchAction { repository.pause() }
    }

    fun resume() {
        launchAction { repository.resume() }
    }

    fun stop() {
        launchAction { repository.stop() }
    }

    fun consumeError() {
        actionError.value = null
    }

    private fun launchAction(block: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            when (block()) {
                is AppResult.Success -> actionError.value = null
                is AppResult.Failure -> actionError.value = R.string.focus_error_action
            }
        }
    }

    private fun tickerFlow(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}

data class FocusModeUiState(
    val session: FocusSession = FocusSession(),
    val remainingMillis: Long = 0L,
    val actionError: Int? = null,
) {
    val isRunning: Boolean
        get() = session.runState == FocusRunState.RUNNING

    val isPaused: Boolean
        get() = session.runState == FocusRunState.PAUSED
}
