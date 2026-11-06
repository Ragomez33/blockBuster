package com.blockbuster.core.database.repository

import com.blockbuster.core.common.model.BlockRuleDraft
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
import com.blockbuster.core.database.RepositoryTestFixture
import com.blockbuster.core.database.dao.BlockRuleDao
import com.blockbuster.core.database.entity.BlockRuleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class BlockRulesRepositoryCrudTest {

    private lateinit var fixture: RepositoryTestFixture

    @Before
    fun setUp() {
        fixture = RepositoryTestFixture()
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun insertReturnsIdAndPersistsDraft() = runTest {
        val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))

        val result = repository.insertRule(
            BlockRuleDraft(
                pattern = "example.com",
                type = BlockRuleType.DOMAIN,
                createdAt = 1_000L,
            ),
        )

        assertTrue(result is AppResult.Success)
        val id = (result as AppResult.Success).value
        assertTrue(id > 0)

        val stored = repository.observeRule(id).first()
        assertEquals("example.com", stored?.pattern)
        assertEquals(BlockRuleType.DOMAIN, stored?.type)
    }

    @Test
    fun updateMissingRuleReturnsNotFound() = runTest {
        val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))
        val rule = com.blockbuster.core.common.model.BlockRule(
            id = 999,
            pattern = "missing.example",
            type = BlockRuleType.DOMAIN,
            isEnabled = true,
            createdAt = 1_000L,
        )

        val result = repository.updateRule(rule)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.NotFound)
    }

    @Test
    fun deleteMissingRuleReturnsNotFound() = runTest {
        val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))

        val result = repository.deleteRule(999)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.NotFound)
    }

    @Test
    fun observeAllRulesMapsEntitiesToDomain() = runTest {
        val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))
        repository.insertRule(
            BlockRuleDraft(
                pattern = "example.com",
                type = BlockRuleType.DOMAIN,
                createdAt = 1_000L,
            ),
        )

        val rules = repository.observeAllRules().first()

        assertEquals(1, rules.size)
        assertEquals("example.com", rules.first().pattern)
        assertEquals(BlockRuleType.DOMAIN, rules.first().type)
    }

    @Test
    fun storageFailureReturnsFailureResult() = runTest {
        val failingDao = object : BlockRuleDao by fixture.ruleDao {
            override suspend fun insertRule(rule: BlockRuleEntity): Long =
                error("Simulated storage failure")
        }
        val repository = fixture.repository(
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            ruleDaoOverride = failingDao,
        )

        val result = repository.insertRule(
            BlockRuleDraft(
                pattern = "closed.db",
                type = BlockRuleType.DOMAIN,
                createdAt = 1_000L,
            ),
        )

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Storage)
    }
}
