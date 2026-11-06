package com.blockbuster.core.database.repository

import com.blockbuster.core.common.contract.BlockRulesRepository
import com.blockbuster.core.common.model.BlockLog
import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleDraft
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.model.BlockTimer
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
import com.blockbuster.core.database.dao.BlockLogDao
import com.blockbuster.core.database.dao.BlockRuleDao
import com.blockbuster.core.database.datastore.BlockTimerDataSource
import com.blockbuster.core.database.di.IoDispatcher
import com.blockbuster.core.database.entity.BlockLogEntity
import com.blockbuster.core.database.entity.toDomain
import com.blockbuster.core.database.entity.toEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockRulesRepositoryImpl @Inject constructor(
    private val blockRuleDao: BlockRuleDao,
    private val blockLogDao: BlockLogDao,
    private val timerDataSource: BlockTimerDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BlockRulesRepository {

    override fun observeActiveRules(): Flow<List<BlockRule>> =
        blockRuleDao.observeActiveRules()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeAllRules(): Flow<List<BlockRule>> =
        blockRuleDao.observeAllRules()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeRule(id: Long): Flow<BlockRule?> =
        blockRuleDao.observeRuleById(id)
            .map { entity -> entity?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun getActiveRulesByType(
        type: BlockRuleType,
    ): AppResult<List<BlockRule>> = ioResult {
        blockRuleDao.getActiveRulesByType(type.name).map { it.toDomain() }
    }

    override suspend fun insertRule(draft: BlockRuleDraft): AppResult<Long> = ioResult {
        blockRuleDao.insertRule(draft.toEntity())
    }

    override suspend fun updateRule(rule: BlockRule): AppResult<Unit> = ioResult {
        val updatedRows = blockRuleDao.updateRule(rule.toEntity())
        if (updatedRows == 0) {
            throw EntityNotFoundException("No block rule for id ${rule.id}")
        }
    }

    override suspend fun deleteRule(id: Long): AppResult<Unit> = ioResult {
        val entity = blockRuleDao.getRuleById(id)
            ?: throw EntityNotFoundException("No block rule for id $id")
        val deletedRows = blockRuleDao.deleteRule(entity)
        if (deletedRows == 0) {
            throw EntityNotFoundException("No block rule for id $id")
        }
    }

    override suspend fun recordBlock(
        ruleId: Long,
        target: String,
        timestamp: Long,
    ): AppResult<Long> = ioResult {
        blockLogDao.insertLog(
            BlockLogEntity(
                ruleId = ruleId,
                target = target,
                timestamp = timestamp,
            ),
        )
    }

    override fun observeBlockLogs(ruleId: Long): Flow<List<BlockLog>> =
        blockLogDao.observeLogsForRule(ruleId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeTimer(): Flow<BlockTimer?> = timerDataSource.timer

    override suspend fun startTimer(timer: BlockTimer): AppResult<Unit> = ioResult {
        timerDataSource.saveTimer(timer)
    }

    override suspend fun stopTimer(): AppResult<Unit> = ioResult {
        timerDataSource.clearTimer()
    }

    override suspend fun clearAll(): AppResult<Unit> = ioResult {
        blockRuleDao.deleteAllRules()
        blockLogDao.deleteAllLogs()
        timerDataSource.clearTimer()
    }

    private suspend fun <T> ioResult(block: suspend () -> T): AppResult<T> =
        withContext(ioDispatcher) {
            try {
                AppResult.Success(block())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (notFound: EntityNotFoundException) {
                AppResult.Failure(
                    AppError.NotFound(notFound.message ?: "Block rule not found"),
                )
            } catch (throwable: Throwable) {
                AppResult.Failure(
                    AppError.Storage(
                        message = throwable.message ?: "Block rules storage failure",
                        cause = throwable,
                    ),
                )
            }
        }

    private class EntityNotFoundException(message: String) : Exception(message)
}
