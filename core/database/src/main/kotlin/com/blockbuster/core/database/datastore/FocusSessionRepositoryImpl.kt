package com.blockbuster.core.database.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.blockbuster.core.common.contract.FocusSessionRepository
import com.blockbuster.core.common.model.FocusPhase
import com.blockbuster.core.common.model.FocusRunState
import com.blockbuster.core.common.model.FocusSession
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal object FocusPreferenceKeys {
    val RUN_STATE = stringPreferencesKey("focus_run_state")
    val PHASE = stringPreferencesKey("focus_phase")
    val WORK_MINUTES = intPreferencesKey("focus_work_minutes")
    val BREAK_MINUTES = intPreferencesKey("focus_break_minutes")
    val PHASE_STARTED_AT = longPreferencesKey("focus_phase_started_at")
    val PHASE_ENDS_AT = longPreferencesKey("focus_phase_ends_at")
    val PAUSED_REMAINING = longPreferencesKey("focus_paused_remaining")
    val COMPLETED_CYCLES = intPreferencesKey("focus_completed_cycles")
}

@Singleton
class FocusSessionRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : FocusSessionRepository {

    override fun observeSession(): Flow<FocusSession> =
        dataStore.data
            .map { preferences -> preferences.toSession() }
            .distinctUntilChanged()

    override suspend fun start(
        workMinutes: Int,
        breakMinutes: Int,
    ): AppResult<Unit> {
        if (!isValidDurations(workMinutes, breakMinutes)) {
            return AppResult.Failure(
                AppError.Unexpected("Invalid Focus Mode durations"),
            )
        }
        return writeResult {
            val now = System.currentTimeMillis()
            dataStore.edit { preferences ->
                preferences.putSession(
                    FocusSession(
                        runState = FocusRunState.RUNNING,
                        phase = FocusPhase.WORK,
                        workMinutes = workMinutes,
                        breakMinutes = breakMinutes,
                        phaseStartedAtEpochMillis = now,
                        phaseEndsAtEpochMillis = now + workMinutes.toMillis(),
                        pausedRemainingMillis = null,
                        completedWorkCycles = 0,
                    ),
                )
            }
        }
    }

    override suspend fun pause(): AppResult<Unit> {
        val session = dataStore.data.first().toSession()
        if (session.runState != FocusRunState.RUNNING) {
            return AppResult.Failure(AppError.Unexpected("No running Focus session"))
        }
        val remaining = session.remainingMillis()
        return writeResult {
            dataStore.edit { preferences ->
                preferences.putSession(
                    session.copy(
                        runState = FocusRunState.PAUSED,
                        pausedRemainingMillis = remaining,
                    ),
                )
            }
        }
    }

    override suspend fun resume(): AppResult<Unit> {
        val session = dataStore.data.first().toSession()
        if (session.runState != FocusRunState.PAUSED) {
            return AppResult.Failure(AppError.Unexpected("No paused Focus session"))
        }
        val remaining = session.pausedRemainingMillis ?: 0L
        val now = System.currentTimeMillis()
        return writeResult {
            dataStore.edit { preferences ->
                preferences.putSession(
                    session.copy(
                        runState = FocusRunState.RUNNING,
                        phaseStartedAtEpochMillis = now,
                        phaseEndsAtEpochMillis = now + remaining,
                        pausedRemainingMillis = null,
                    ),
                )
            }
        }
    }

    override suspend fun stop(): AppResult<Unit> = writeResult {
        dataStore.edit { preferences ->
            val current = preferences.toSession()
            preferences.putSession(
                FocusSession(
                    runState = FocusRunState.IDLE,
                    phase = FocusPhase.WORK,
                    workMinutes = current.workMinutes,
                    breakMinutes = current.breakMinutes,
                    phaseStartedAtEpochMillis = null,
                    phaseEndsAtEpochMillis = null,
                    pausedRemainingMillis = null,
                    completedWorkCycles = 0,
                ),
            )
        }
    }

    override suspend fun saveDurations(
        workMinutes: Int,
        breakMinutes: Int,
    ): AppResult<Unit> {
        if (!isValidDurations(workMinutes, breakMinutes)) {
            return AppResult.Failure(
                AppError.Unexpected("Invalid Focus Mode durations"),
            )
        }
        return writeResult {
            dataStore.edit { preferences ->
                preferences[FocusPreferenceKeys.WORK_MINUTES] = workMinutes
                preferences[FocusPreferenceKeys.BREAK_MINUTES] = breakMinutes
            }
        }
    }

    override suspend fun advancePhase(nowEpochMillis: Long): AppResult<Unit> {
        val session = dataStore.data.first().toSession()
        if (session.runState != FocusRunState.RUNNING) {
            return AppResult.Success(Unit)
        }

        var updated = session
        var iterations = 0
        while (updated.runState == FocusRunState.RUNNING &&
            (updated.phaseEndsAtEpochMillis ?: Long.MAX_VALUE) <= nowEpochMillis &&
            iterations < MAX_TRANSITIONS
        ) {
            val previousEnd = updated.phaseEndsAtEpochMillis ?: nowEpochMillis
            updated = when (updated.phase) {
                FocusPhase.WORK -> {
                    val duration = updated.breakMinutes.toMillis()
                    updated.copy(
                        phase = FocusPhase.BREAK,
                        phaseStartedAtEpochMillis = previousEnd,
                        phaseEndsAtEpochMillis = previousEnd + duration,
                        completedWorkCycles = updated.completedWorkCycles + 1,
                    )
                }

                FocusPhase.BREAK -> {
                    val duration = updated.workMinutes.toMillis()
                    updated.copy(
                        phase = FocusPhase.WORK,
                        phaseStartedAtEpochMillis = previousEnd,
                        phaseEndsAtEpochMillis = previousEnd + duration,
                    )
                }
            }
            iterations++
        }

        if (updated == session) return AppResult.Success(Unit)

        return writeResult {
            dataStore.edit { preferences ->
                preferences.putSession(updated)
            }
        }
    }

    private fun isValidDurations(workMinutes: Int, breakMinutes: Int): Boolean =
        workMinutes in FocusSession.MIN_WORK_MINUTES..FocusSession.MAX_WORK_MINUTES &&
            breakMinutes in FocusSession.MIN_BREAK_MINUTES..FocusSession.MAX_BREAK_MINUTES

    private fun Preferences.toSession(): FocusSession = FocusSession(
        runState = runCatching {
            FocusRunState.valueOf(
                this[FocusPreferenceKeys.RUN_STATE] ?: FocusRunState.IDLE.name,
            )
        }.getOrDefault(FocusRunState.IDLE),
        phase = runCatching {
            FocusPhase.valueOf(
                this[FocusPreferenceKeys.PHASE] ?: FocusPhase.WORK.name,
            )
        }.getOrDefault(FocusPhase.WORK),
        workMinutes = this[FocusPreferenceKeys.WORK_MINUTES]
            ?: FocusSession.DEFAULT_WORK_MINUTES,
        breakMinutes = this[FocusPreferenceKeys.BREAK_MINUTES]
            ?: FocusSession.DEFAULT_BREAK_MINUTES,
        phaseStartedAtEpochMillis = this[FocusPreferenceKeys.PHASE_STARTED_AT],
        phaseEndsAtEpochMillis = this[FocusPreferenceKeys.PHASE_ENDS_AT],
        pausedRemainingMillis = this[FocusPreferenceKeys.PAUSED_REMAINING],
        completedWorkCycles = this[FocusPreferenceKeys.COMPLETED_CYCLES] ?: 0,
    )

    private fun MutablePreferences.putSession(session: FocusSession) {
        this[FocusPreferenceKeys.RUN_STATE] = session.runState.name
        this[FocusPreferenceKeys.PHASE] = session.phase.name
        this[FocusPreferenceKeys.WORK_MINUTES] = session.workMinutes
        this[FocusPreferenceKeys.BREAK_MINUTES] = session.breakMinutes
        session.phaseStartedAtEpochMillis?.let {
            this[FocusPreferenceKeys.PHASE_STARTED_AT] = it
        } ?: remove(FocusPreferenceKeys.PHASE_STARTED_AT)
        session.phaseEndsAtEpochMillis?.let {
            this[FocusPreferenceKeys.PHASE_ENDS_AT] = it
        } ?: remove(FocusPreferenceKeys.PHASE_ENDS_AT)
        session.pausedRemainingMillis?.let {
            this[FocusPreferenceKeys.PAUSED_REMAINING] = it
        } ?: remove(FocusPreferenceKeys.PAUSED_REMAINING)
        this[FocusPreferenceKeys.COMPLETED_CYCLES] = session.completedWorkCycles
    }

    private suspend fun writeResult(block: suspend () -> Unit): AppResult<Unit> =
        try {
            block()
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppResult.Failure(
                AppError.Storage(
                    message = throwable.message ?: "Focus storage failure",
                    cause = throwable,
                ),
            )
        }

    private fun Int.toMillis(): Long = this * 60_000L

    private companion object {
        const val MAX_TRANSITIONS = 500
    }
}
