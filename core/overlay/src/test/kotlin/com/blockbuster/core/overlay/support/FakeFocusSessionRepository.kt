package com.blockbuster.core.overlay.support

import com.blockbuster.core.common.contract.FocusSessionRepository
import com.blockbuster.core.common.model.FocusSession
import com.blockbuster.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFocusSessionRepository : FocusSessionRepository {
    val session = MutableStateFlow(FocusSession())

    override fun observeSession(): Flow<FocusSession> = session

    override suspend fun start(workMinutes: Int, breakMinutes: Int): AppResult<Unit> {
        session.value = FocusSession(
            runState = com.blockbuster.core.common.model.FocusRunState.RUNNING,
            workMinutes = workMinutes,
            breakMinutes = breakMinutes,
            phaseEndsAtEpochMillis = Long.MAX_VALUE,
        )
        return AppResult.Success(Unit)
    }

    override suspend fun pause(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun resume(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun stop(): AppResult<Unit> {
        session.value = FocusSession()
        return AppResult.Success(Unit)
    }

    override suspend fun saveDurations(workMinutes: Int, breakMinutes: Int): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun advancePhase(nowEpochMillis: Long): AppResult<Unit> =
        AppResult.Success(Unit)
}
