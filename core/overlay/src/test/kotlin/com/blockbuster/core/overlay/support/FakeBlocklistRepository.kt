package com.blockbuster.core.overlay.support

import com.blockbuster.core.common.contract.BlocklistRepository
import com.blockbuster.core.common.model.BlocklistEntry
import com.blockbuster.core.common.model.BlocklistEntryDraft
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType
import com.blockbuster.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBlocklistRepository : BlocklistRepository {
    val entries = MutableStateFlow<List<BlocklistEntry>>(emptyList())

    override fun observeEntries(): Flow<List<BlocklistEntry>> = entries

    override fun observeEntries(
        listType: BlocklistType,
        targetType: BlocklistTargetType,
    ): Flow<List<BlocklistEntry>> = entries.map { list ->
        list.filter { it.listType == listType && it.targetType == targetType }
    }

    override suspend fun addEntry(draft: BlocklistEntryDraft): AppResult<Long> {
        val id = (entries.value.maxOfOrNull { it.id } ?: 0L) + 1L
        entries.value = entries.value + BlocklistEntry(
            id = id,
            value = draft.value,
            targetType = draft.targetType,
            listType = draft.listType,
            createdAt = draft.createdAt,
        )
        return AppResult.Success(id)
    }

    override suspend fun deleteEntry(id: Long): AppResult<Unit> {
        entries.value = entries.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }
}
