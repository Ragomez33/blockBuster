package com.blockbuster.core.common.contract

import com.blockbuster.core.common.model.BlocklistEntry
import com.blockbuster.core.common.model.BlocklistEntryDraft
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType
import com.blockbuster.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

interface BlocklistRepository {
    fun observeEntries(): Flow<List<BlocklistEntry>>

    fun observeEntries(
        listType: BlocklistType,
        targetType: BlocklistTargetType,
    ): Flow<List<BlocklistEntry>>

    suspend fun addEntry(draft: BlocklistEntryDraft): AppResult<Long>

    suspend fun deleteEntry(id: Long): AppResult<Unit>
}
