package com.blockbuster.core.database.entity

import androidx.room.TypeConverter
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType

class BlocklistTypeConverter {
    @TypeConverter
    fun fromBlocklistType(value: BlocklistType): String = value.name

    @TypeConverter
    fun toBlocklistType(value: String): BlocklistType = BlocklistType.valueOf(value)

    @TypeConverter
    fun fromBlocklistTargetType(value: BlocklistTargetType): String = value.name

    @TypeConverter
    fun toBlocklistTargetType(value: String): BlocklistTargetType =
        BlocklistTargetType.valueOf(value)
}
