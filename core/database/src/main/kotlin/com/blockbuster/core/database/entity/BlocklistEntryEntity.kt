package com.blockbuster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.blockbuster.core.common.model.BlocklistEntry
import com.blockbuster.core.common.model.BlocklistEntryDraft
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType

@Entity(
    tableName = "blocklist_entries",
    indices = [
        Index(
            value = ["value", "target_type", "list_type"],
            unique = true,
        ),
        Index(value = ["list_type", "target_type"]),
    ],
)
data class BlocklistEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "value")
    val value: String,
    @ColumnInfo(name = "target_type")
    val targetType: BlocklistTargetType,
    @ColumnInfo(name = "list_type")
    val listType: BlocklistType,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

fun BlocklistEntryEntity.toDomain(): BlocklistEntry = BlocklistEntry(
    id = id,
    value = value,
    targetType = targetType,
    listType = listType,
    createdAt = createdAt,
)

fun BlocklistEntryDraft.toEntity(): BlocklistEntryEntity = BlocklistEntryEntity(
    id = 0,
    value = value,
    targetType = targetType,
    listType = listType,
    createdAt = createdAt,
)
