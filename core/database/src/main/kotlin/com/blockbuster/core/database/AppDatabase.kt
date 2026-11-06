package com.blockbuster.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.blockbuster.core.database.dao.BlockLogDao
import com.blockbuster.core.database.dao.BlockRuleDao
import com.blockbuster.core.database.entity.BlockLogEntity
import com.blockbuster.core.database.entity.BlockRuleEntity
import com.blockbuster.core.database.entity.BlockRuleTypeConverter

@Database(
    entities = [
        BlockRuleEntity::class,
        BlockLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(BlockRuleTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao

    abstract fun blockLogDao(): BlockLogDao
}
