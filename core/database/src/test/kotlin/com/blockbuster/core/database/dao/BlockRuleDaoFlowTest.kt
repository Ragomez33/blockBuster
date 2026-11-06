package com.blockbuster.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.database.AppDatabase
import com.blockbuster.core.database.entity.BlockRuleEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlockRuleDaoFlowTest {

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
    fun activeStreamEmitsAgainAfterInsert() = runBlocking {
        val emissions = async {
            withTimeout(10_000) {
                dao.observeActiveRules().take(2).toList()
            }
        }

        delay(200)
        dao.insertRule(rule(pattern = "active.example", enabled = true))
        val result = emissions.await()

        assertEquals(2, result.size)
        assertTrue(result.first().isEmpty())
        assertTrue(result.last().any { it.pattern == "active.example" })
    }

    @Test
    fun activeQueryExcludesDisabledRules() = runTest {
        dao.insertRule(rule(pattern = "enabled.example", enabled = true))
        dao.insertRule(rule(pattern = "disabled.example", enabled = false))

        val active = dao.observeActiveRules().first()

        assertEquals(listOf("enabled.example"), active.map { it.pattern })
    }

    @Test
    fun allRulesIncludeDisabledRules() = runTest {
        dao.insertRule(rule(pattern = "enabled.example", enabled = true))
        dao.insertRule(rule(pattern = "disabled.example", enabled = false))

        val all = dao.observeAllRules().first()

        assertEquals(2, all.size)
        assertTrue(all.any { !it.isEnabled })
    }

    @Test
    fun activeRulesByTypeFiltersTypeAndEnabled() = runTest {
        dao.insertRule(rule("domain.example", BlockRuleType.DOMAIN, enabled = true))
        dao.insertRule(rule("shorts", BlockRuleType.KEYWORD_SHORTS, enabled = true))
        dao.insertRule(rule("domain.disabled", BlockRuleType.DOMAIN, enabled = false))

        val domains = dao.getActiveRulesByType(BlockRuleType.DOMAIN.name)

        assertEquals(1, domains.size)
        assertEquals("domain.example", domains.first().pattern)
        assertTrue(domains.first().isEnabled)
    }

    private fun rule(
        pattern: String,
        type: BlockRuleType = BlockRuleType.DOMAIN,
        enabled: Boolean,
    ) = BlockRuleEntity(
        pattern = pattern,
        type = type,
        isEnabled = enabled,
        createdAt = 1_000L,
    )
}
