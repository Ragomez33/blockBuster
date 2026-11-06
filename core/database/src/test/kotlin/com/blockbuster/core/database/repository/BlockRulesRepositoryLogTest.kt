package com.blockbuster.core.database.repository

import com.blockbuster.core.common.model.BlockRuleDraft
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.result.AppResult
import com.blockbuster.core.database.RepositoryTestFixture
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
class BlockRulesRepositoryLogTest {

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
    fun recordBlockPersistsAndObservesLogs() = runTest {
        val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))
        val ruleId = insertRule(repository)

        val result = repository.recordBlock(ruleId, "example.com", 2_000L)

        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).value > 0)
        val logs = repository.observeBlockLogs(ruleId).first()
        assertEquals(1, logs.size)
        assertEquals("example.com", logs.first().target)
        assertEquals(2_000L, logs.first().timestamp)
    }

    @Test
    fun logsRemainAfterRuleDeletion() = runTest {
        val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))
        val ruleId = insertRule(repository)
        repository.recordBlock(ruleId, "example.com", 2_000L)

        repository.deleteRule(ruleId)

        val logs = repository.observeBlockLogs(ruleId).first()
        assertEquals(1, logs.size)
        assertEquals(ruleId, logs.first().ruleId)
    }

    private suspend fun insertRule(
        repository: com.blockbuster.core.database.repository.BlockRulesRepositoryImpl,
    ): Long {
        val result = repository.insertRule(
            BlockRuleDraft(
                pattern = "example.com",
                type = BlockRuleType.DOMAIN,
                createdAt = 1_000L,
            ),
        )
        return (result as AppResult.Success).value
    }
}
