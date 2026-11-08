package com.blockbuster.core.database.repository

import com.blockbuster.core.common.contract.BlocklistRepository
import com.blockbuster.core.common.model.BlocklistEntry
import com.blockbuster.core.common.model.BlocklistEntryDraft
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
import com.blockbuster.core.database.dao.BlocklistEntryDao
import com.blockbuster.core.database.di.IoDispatcher
import com.blockbuster.core.database.entity.toDomain
import com.blockbuster.core.database.entity.toEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class BlocklistRepositoryImpl @Inject constructor(
    private val dao: BlocklistEntryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BlocklistRepository {

    override fun observeEntries(): Flow<List<BlocklistEntry>> =
        dao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeEntries(
        listType: BlocklistType,
        targetType: BlocklistTargetType,
    ): Flow<List<BlocklistEntry>> =
        dao.observeByType(listType.name, targetType.name)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun addEntry(draft: BlocklistEntryDraft): AppResult<Long> =
        ioResult {
            val value = normalizeValue(draft.value, draft.targetType)
                ?: throw InvalidEntryException("Invalid blocklist value")
            val duplicate = dao.findDuplicate(
                value = value,
                targetType = draft.targetType.name,
                listType = draft.listType.name,
            )
            if (duplicate != null) {
                throw DuplicateEntryException(
                    "Entry already exists in ${draft.listType.name.lowercase()}",
                )
            }
            dao.insert(
                draft.copy(value = value).toEntity(),
            )
        }

    override suspend fun deleteEntry(id: Long): AppResult<Unit> = ioResult {
        val entity = dao.findById(id)
            ?: throw MissingEntryException("No blocklist entry for id $id")
        val deleted = dao.deleteById(entity.id)
        if (deleted == 0) {
            throw MissingEntryException("No blocklist entry for id $id")
        }
    }

    private fun normalizeValue(
        rawValue: String,
        targetType: BlocklistTargetType,
    ): String? {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return null

        return when (targetType) {
            BlocklistTargetType.APP -> {
                val normalized = trimmed.lowercase()
                normalized.takeIf { PACKAGE_REGEX.matches(it) }
            }

            BlocklistTargetType.WEBSITE -> {
                val host = trimmed.lowercase()
                    .substringAfter("://", trimmed.lowercase())
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')
                    .substringBefore(':')
                    .removePrefix("www.")
                    .trimEnd('.')
                host.takeIf { it.isNotBlank() && !it.contains(' ') }
            }

            BlocklistTargetType.KEYWORD -> trimmed
        }
    }

    private suspend fun <T> ioResult(block: suspend () -> T): AppResult<T> =
        withContext(ioDispatcher) {
            try {
                AppResult.Success(block())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (duplicate: DuplicateEntryException) {
                AppResult.Failure(AppError.Conflict(duplicate.message ?: "Duplicate entry"))
            } catch (invalid: InvalidEntryException) {
                AppResult.Failure(AppError.Unexpected(invalid.message ?: "Invalid entry"))
            } catch (missing: MissingEntryException) {
                AppResult.Failure(AppError.NotFound(missing.message ?: "Entry not found"))
            } catch (throwable: Throwable) {
                AppResult.Failure(
                    AppError.Storage(
                        message = throwable.message ?: "Blocklist storage failure",
                        cause = throwable,
                    ),
                )
            }
        }

    private class DuplicateEntryException(message: String) : Exception(message)

    private class InvalidEntryException(message: String) : Exception(message)

    private class MissingEntryException(message: String) : Exception(message)

    private companion object {
        val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
    }
}
