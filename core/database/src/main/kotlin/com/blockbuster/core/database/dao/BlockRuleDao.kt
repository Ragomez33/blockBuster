package com.blockbuster.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.blockbuster.core.database.entity.BlockRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockRuleDao {
    @Query("SELECT * FROM block_rules WHERE is_enabled = 1 ORDER BY created_at DESC")
    fun observeActiveRules(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM block_rules ORDER BY created_at DESC")
    fun observeAllRules(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM block_rules WHERE id = :id LIMIT 1")
    fun observeRuleById(id: Long): Flow<BlockRuleEntity?>

    @Query(
        "SELECT * FROM block_rules " +
            "WHERE is_enabled = 1 AND type = :type " +
            "ORDER BY created_at DESC",
    )
    suspend fun getActiveRulesByType(type: String): List<BlockRuleEntity>

    @Query("SELECT * FROM block_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: Long): BlockRuleEntity?

    @Insert
    suspend fun insertRule(rule: BlockRuleEntity): Long

    @Update
    suspend fun updateRule(rule: BlockRuleEntity): Int

    @Delete
    suspend fun deleteRule(rule: BlockRuleEntity): Int

    @Query("DELETE FROM block_rules")
    suspend fun deleteAllRules()
}
