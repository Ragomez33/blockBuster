package com.blockbuster.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.database.AppDatabase
import com.blockbuster.core.database.entity.BlockRuleEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlockRuleDaoCrudTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: BlockRuleDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.blockRuleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertReturnsGeneratedId() = runTest {
        val id = dao.insertRule(rule(pattern = "example.com"))

        assertTrue(id > 0)
        val stored = dao.getRuleById(id)
        assertNotNull(stored)
        assertEquals("example.com", stored?.pattern)
        assertEquals(BlockRuleType.DOMAIN, stored?.type)
        assertTrue(stored?.isEnabled == true)
    }

    @Test
    fun updateChangesFieldsAndReportsAffectedRows() = runTest {
        val id = dao.insertRule(rule(pattern = "old.example"))

        val stored = requireNotNull(dao.getRuleById(id))
        val updated = dao.updateRule(
            stored.copy(
                pattern = "new.example",
                type = BlockRuleType.KEYWORD_SHORTS,
                isEnabled = false,
            ),
        )

        assertEquals(1, updated)
        val reloaded = requireNotNull(dao.getRuleById(id))
        assertEquals("new.example", reloaded.pattern)
        assertEquals(BlockRuleType.KEYWORD_SHORTS, reloaded.type)
        assertFalse(reloaded.isEnabled)
    }

    @Test
    fun updateOrDeleteMissingReturnsZero() = runTest {
        val missing = rule(pattern = "missing.example").copy(id = 999)

        assertEquals(0, dao.updateRule(missing))
        assertEquals(0, dao.deleteRule(missing))
    }

    @Test
    fun deleteRemovesRule() = runTest {
        val id = dao.insertRule(rule(pattern = "delete.example"))
        val stored = requireNotNull(dao.getRuleById(id))

        assertEquals(1, dao.deleteRule(stored))
        assertNull(dao.getRuleById(id))
    }

    private fun rule(pattern: String) = BlockRuleEntity(
        pattern = pattern,
        type = BlockRuleType.DOMAIN,
        isEnabled = true,
        createdAt = 1_000L,
    )
}
