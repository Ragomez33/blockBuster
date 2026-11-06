package com.blockbuster.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.blockbuster.core.database.entity.BlockLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockLogDao {
    @Insert
    suspend fun insertLog(log: BlockLogEntity): Long

    @Query(
        "SELECT * FROM block_logs WHERE rule_id = :ruleId " +
            "ORDER BY timestamp DESC",
    )
    fun observeLogsForRule(ruleId: Long): Flow<List<BlockLogEntity>>

    @Query("DELETE FROM block_logs")
    suspend fun deleteAllLogs()
}
