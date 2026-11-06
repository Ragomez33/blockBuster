package com.blockbuster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleDraft
import com.blockbuster.core.common.model.BlockRuleType

@Entity(
    tableName = "block_rules",
    indices = [
        Index(value = ["is_enabled", "type"]),
        Index(value = ["created_at"]),
    ],
)
data class BlockRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "pattern")
    val pattern: String,
    @ColumnInfo(name = "type")
    val type: BlockRuleType,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

fun BlockRuleEntity.toDomain(): BlockRule = BlockRule(
    id = id,
    pattern = pattern,
    type = type,
    isEnabled = isEnabled,
    createdAt = createdAt,
)

fun BlockRule.toEntity(): BlockRuleEntity = BlockRuleEntity(
    id = id,
    pattern = pattern,
    type = type,
    isEnabled = isEnabled,
    createdAt = createdAt,
)

fun BlockRuleDraft.toEntity(): BlockRuleEntity = BlockRuleEntity(
    id = 0,
    pattern = pattern,
    type = type,
    isEnabled = isEnabled,
    createdAt = createdAt,
)
