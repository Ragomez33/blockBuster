package com.blockbuster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.blockbuster.core.common.model.BlockLog

@Entity(
    tableName = "block_logs",
    indices = [
        Index(value = ["rule_id"]),
        Index(value = ["timestamp"]),
    ],
)
data class BlockLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "rule_id")
    val ruleId: Long,
    @ColumnInfo(name = "target")
    val target: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
)

fun BlockLogEntity.toDomain(): BlockLog = BlockLog(
    id = id,
    ruleId = ruleId,
    target = target,
    timestamp = timestamp,
)
