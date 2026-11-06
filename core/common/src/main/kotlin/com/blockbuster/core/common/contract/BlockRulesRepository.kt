package com.blockbuster.core.common.contract

import com.blockbuster.core.common.model.BlockLog
import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleDraft
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.model.BlockTimer
import com.blockbuster.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for block rules (Room) and the active timer (DataStore).
 *
 * Reactive reads expose plain [Flow] streams of domain models. One-shot reads
 * and writes return [AppResult] so storage failures never escape as
 * uncontrolled exceptions.
 */
interface BlockRulesRepository {
    fun observeActiveRules(): Flow<List<BlockRule>>

    fun observeAllRules(): Flow<List<BlockRule>>

    fun observeRule(id: Long): Flow<BlockRule?>

    suspend fun getActiveRulesByType(type: BlockRuleType): AppResult<List<BlockRule>>

    suspend fun insertRule(draft: BlockRuleDraft): AppResult<Long>

    suspend fun updateRule(rule: BlockRule): AppResult<Unit>

    suspend fun deleteRule(id: Long): AppResult<Unit>

    suspend fun recordBlock(
        ruleId: Long,
        target: String,
        timestamp: Long,
    ): AppResult<Long>

    fun observeBlockLogs(ruleId: Long): Flow<List<BlockLog>>

    fun observeTimer(): Flow<BlockTimer?>

    suspend fun startTimer(timer: BlockTimer): AppResult<Unit>

    suspend fun stopTimer(): AppResult<Unit>

    suspend fun clearAll(): AppResult<Unit>
}
