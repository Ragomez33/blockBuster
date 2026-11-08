package com.blockbuster.core.database.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.core.database.AppDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {

    @Test
    fun migration2To3CreatesBlocklistTableAndIndices() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database = helper.writableDatabase

        AppDatabase.MIGRATION_2_3.migrate(database)

        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'blocklist_entries'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' " +
                "AND name = 'index_blocklist_entries_value_target_type_list_type'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' " +
                "AND name = 'index_blocklist_entries_list_type_target_type'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }

        helper.close()
    }
}
