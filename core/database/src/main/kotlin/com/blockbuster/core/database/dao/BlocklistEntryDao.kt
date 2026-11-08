package com.blockbuster.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.blockbuster.core.database.entity.BlocklistEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistEntryDao {
    @Query("SELECT * FROM blocklist_entries ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BlocklistEntryEntity>>

    @Query(
        "SELECT * FROM blocklist_entries " +
            "WHERE list_type = :listType AND target_type = :targetType " +
            "ORDER BY created_at DESC",
    )
    fun observeByType(
        listType: String,
        targetType: String,
    ): Flow<List<BlocklistEntryEntity>>

    @Query(
        "SELECT * FROM blocklist_entries " +
            "WHERE value = :value AND target_type = :targetType AND list_type = :listType " +
            "LIMIT 1",
    )
    suspend fun findDuplicate(
        value: String,
        targetType: String,
        listType: String,
    ): BlocklistEntryEntity?

    @Query("SELECT * FROM blocklist_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): BlocklistEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: BlocklistEntryEntity): Long

    @Query("DELETE FROM blocklist_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM blocklist_entries")
    suspend fun deleteAll()
}
