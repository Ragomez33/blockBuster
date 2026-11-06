package com.blockbuster.core.overlay.support

import com.blockbuster.core.common.contract.BlockRulesRepository
import com.blockbuster.core.common.model.BlockLog
import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleDraft
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.model.BlockTimer
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FakeBlockRulesRepository : BlockRulesRepository {
    val activeRules = MutableStateFlow<List<BlockRule>>(emptyList())
    val timerState = MutableStateFlow<BlockTimer?>(null)
    val blockLogs = mutableListOf<BlockLog>()
    val recordBlockCalls = mutableListOf<Triple<Long, String, Long>>()
    var recordBlockResult: AppResult<Long> = AppResult.Success(1L)

    private var nextId = 1L

    override fun observeActiveRules(): Flow<List<BlockRule>> = activeRules

    override fun observeAllRules(): Flow<List<BlockRule>> = activeRules

    override fun observeRule(id: Long): Flow<BlockRule?> =
        activeRules.map { rules -> rules.firstOrNull { it.id == id } }

    override suspend fun getActiveRulesByType(
        type: BlockRuleType,
    ): AppResult<List<BlockRule>> = AppResult.Success(
        activeRules.value.filter { it.isEnabled && it.type == type },
    )

    override suspend fun insertRule(draft: BlockRuleDraft): AppResult<Long> {
        val id = nextId++
        activeRules.value = activeRules.value + BlockRule(
            id = id,
            pattern = draft.pattern,
            type = draft.type,
            isEnabled = draft.isEnabled,
            createdAt = draft.createdAt,
        )
        return AppResult.Success(id)
    }

    override suspend fun updateRule(rule: BlockRule): AppResult<Unit> {
        val current = activeRules.value
        if (current.none { it.id == rule.id }) {
            return AppResult.Failure(AppError.NotFound("Missing rule ${rule.id}"))
        }
        activeRules.value = current.map { if (it.id == rule.id) rule else it }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteRule(id: Long): AppResult<Unit> {
        val current = activeRules.value
        if (current.none { it.id == id }) {
            return AppResult.Failure(AppError.NotFound("Missing rule $id"))
        }
        activeRules.value = current.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    override suspend fun recordBlock(
        ruleId: Long,
        target: String,
        timestamp: Long,
    ): AppResult<Long> {
        recordBlockCalls += Triple(ruleId, target, timestamp)
        if (recordBlockResult is AppResult.Success) {
            blockLogs += BlockLog(
                id = blockLogs.size.toLong() + 1,
                ruleId = ruleId,
                target = target,
                timestamp = timestamp,
            )
        }
        return recordBlockResult
    }

    override fun observeBlockLogs(ruleId: Long): Flow<List<BlockLog>> =
        flowOf(blockLogs.filter { it.ruleId == ruleId })

    override fun observeTimer(): Flow<BlockTimer?> = timerState

    override suspend fun startTimer(timer: BlockTimer): AppResult<Unit> {
        timerState.value = timer
        return AppResult.Success(Unit)
    }

    override suspend fun stopTimer(): AppResult<Unit> {
        timerState.value = null
        return AppResult.Success(Unit)
    }

    override suspend fun clearAll(): AppResult<Unit> {
        activeRules.value = emptyList()
        blockLogs.clear()
        timerState.value = null
        return AppResult.Success(Unit)
    }
}
