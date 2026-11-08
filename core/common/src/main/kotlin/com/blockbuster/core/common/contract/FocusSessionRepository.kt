package com.blockbuster.core.common.contract

import com.blockbuster.core.common.model.FocusSession
import com.blockbuster.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

interface FocusSessionRepository {
    fun observeSession(): Flow<FocusSession>

    suspend fun start(workMinutes: Int, breakMinutes: Int): AppResult<Unit>

    suspend fun pause(): AppResult<Unit>

    suspend fun resume(): AppResult<Unit>

    suspend fun stop(): AppResult<Unit>

    suspend fun saveDurations(workMinutes: Int, breakMinutes: Int): AppResult<Unit>

    suspend fun advancePhase(nowEpochMillis: Long = System.currentTimeMillis()): AppResult<Unit>
}
