package com.blockbuster.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.blockbuster.core.database.dao.BlockLogDao
import com.blockbuster.core.database.dao.BlockRuleDao
import com.blockbuster.core.database.dao.BlocklistEntryDao
import com.blockbuster.core.database.entity.BlockLogEntity
import com.blockbuster.core.database.entity.BlockRuleEntity
import com.blockbuster.core.database.entity.BlockRuleTypeConverter
import com.blockbuster.core.database.entity.BlocklistEntryEntity
import com.blockbuster.core.database.entity.BlocklistTypeConverter

@Database(
    entities = [
        BlockRuleEntity::class,
        BlockLogEntity::class,
        BlocklistEntryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(
    BlockRuleTypeConverter::class,
    BlocklistTypeConverter::class,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao

    abstract fun blockLogDao(): BlockLogDao

    abstract fun blocklistEntryDao(): BlocklistEntryDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blocklist_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `value` TEXT NOT NULL,
                        `target_type` TEXT NOT NULL,
                        `list_type` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_blocklist_entries_value_target_type_list_type` " +
                        "ON `blocklist_entries` (`value`, `target_type`, `list_type`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_blocklist_entries_list_type_target_type` " +
                        "ON `blocklist_entries` (`list_type`, `target_type`)",
                )
            }
        }
    }
}
