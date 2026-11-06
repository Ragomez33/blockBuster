package com.blockbuster.core.database.entity

import androidx.room.TypeConverter
import com.blockbuster.core.common.model.BlockRuleType

class BlockRuleTypeConverter {
    @TypeConverter
    fun fromBlockRuleType(type: BlockRuleType): String = type.name

    @TypeConverter
    fun toBlockRuleType(value: String): BlockRuleType = BlockRuleType.valueOf(value)
}
