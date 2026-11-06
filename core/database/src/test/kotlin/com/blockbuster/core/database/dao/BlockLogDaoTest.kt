package com.blockbuster.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.database.AppDatabase
import com.blockbuster.core.database.entity.BlockLogEntity
import com.blockbuster.core.database.entity.BlockRuleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlockLogDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var ruleDao: BlockRuleDao
    private lateinit var logDao: BlockLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ruleDao = database.blockRuleDao()
        logDao = database.blockLogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndObserveLogsOrderedByTimestampDesc() = runTest {
        val ruleId = ruleDao.insertRule(rule())

        val firstId = logDao.insertLog(log(ruleId, "first", 1_000L))
        val secondId = logDao.insertLog(log(ruleId, "second", 2_000L))

        assertTrue(firstId > 0)
        assertTrue(secondId > 0)

        val logs = logDao.observeLogsForRule(ruleId).first()
        assertEquals(listOf("second", "first"), logs.map { it.target })
    }

    @Test
    fun logsSurviveRuleDeletion() = runTest {
        val entity = rule()
        val ruleId = ruleDao.insertRule(entity)
        logDao.insertLog(log(ruleId, "target", 1_000L))

        val storedRule = requireNotNull(ruleDao.getRuleById(ruleId))
        ruleDao.deleteRule(storedRule)

        val logs = logDao.observeLogsForRule(ruleId).first()
        assertEquals(1, logs.size)
        assertEquals("target", logs.first().target)
    }

    private fun rule() = BlockRuleEntity(
        pattern = "example.com",
        type = BlockRuleType.DOMAIN,
        isEnabled = true,
        createdAt = 1_000L,
    )

    private fun log(ruleId: Long, target: String, timestamp: Long) = BlockLogEntity(
        ruleId = ruleId,
        target = target,
        timestamp = timestamp,
    )
}
