package com.blockbuster.core.common.model

enum class FocusRunState {
    IDLE,
    RUNNING,
    PAUSED,
}

enum class FocusPhase {
    WORK,
    BREAK,
}

/**
 * Persisted Pomodoro session.
 *
 * While RUNNING, phase timing is derived from absolute epoch timestamps. When
 * PAUSED, [pausedRemainingMillis] keeps the remaining fraction of the phase.
 */
data class FocusSession(
    val runState: FocusRunState = FocusRunState.IDLE,
    val phase: FocusPhase = FocusPhase.WORK,
    val workMinutes: Int = DEFAULT_WORK_MINUTES,
    val breakMinutes: Int = DEFAULT_BREAK_MINUTES,
    val phaseStartedAtEpochMillis: Long? = null,
    val phaseEndsAtEpochMillis: Long? = null,
    val pausedRemainingMillis: Long? = null,
    val completedWorkCycles: Int = 0,
) {
    fun remainingMillis(nowEpochMillis: Long = System.currentTimeMillis()): Long =
        when (runState) {
            FocusRunState.IDLE -> 0L
            FocusRunState.PAUSED -> (pausedRemainingMillis ?: 0L).coerceAtLeast(0L)
            FocusRunState.RUNNING ->
                ((phaseEndsAtEpochMillis ?: nowEpochMillis) - nowEpochMillis).coerceAtLeast(0L)
        }

    fun isWorkBlockingActive(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        runState == FocusRunState.RUNNING &&
            phase == FocusPhase.WORK &&
            remainingMillis(nowEpochMillis) > 0L

    companion object {
        const val DEFAULT_WORK_MINUTES = 25
        const val DEFAULT_BREAK_MINUTES = 5
        const val MIN_WORK_MINUTES = 1
        const val MAX_WORK_MINUTES = 120
        const val MIN_BREAK_MINUTES = 1
        const val MAX_BREAK_MINUTES = 60
    }
}
