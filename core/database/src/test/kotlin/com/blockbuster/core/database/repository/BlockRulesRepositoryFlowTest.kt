package com.blockbuster.core.database.repository

import com.blockbuster.core.common.model.BlockRuleDraft
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.model.ProtectionPreset
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
class BlockRulesRepositoryFlowTest {

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
    fun observeActiveRulesExcludesDisabledRules() = runTest {
        val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))
        repository.insertRule(draft("active.example", BlockRuleType.DOMAIN))
        val disabled = repository.insertRule(draft("disabled.example", BlockRuleType.DOMAIN))
        val disabledId = (disabled as AppResult.Success).value
        repository.updateRule(
            repository.observeRule(disabledId).first()!!.copy(isEnabled = false),
        )

        val active = repository.observeActiveRules().first()

        assertEquals(1, active.size)
        assertEquals("active.example", active.first().pattern)
    }

    @Test
    fun observeActiveRulesIncludesEnabledPresetRules() = runTest {
        val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))
        fixture.presetRepository.setPresetEnabled(ProtectionPreset.SHORT_VIDEOS, true)

        val active = repository.observeActiveRules().first()

        assertTrue(
            active.any { rule ->
                rule.type == BlockRuleType.PACKAGE &&
                    rule.pattern == "com.zhiliaoapp.musically"
            },
        )
    }

    @Test
    fun disablingPresetRemovesItsSyntheticRules() = runTest {
        val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))
        fixture.presetRepository.setPresetEnabled(ProtectionPreset.ADULT_CONTENT, true)
        assertTrue(repository.observeActiveRules().first().isNotEmpty())

        fixture.presetRepository.setPresetEnabled(ProtectionPreset.ADULT_CONTENT, false)

        assertTrue(repository.observeActiveRules().first().isEmpty())
    }

    @Test
    fun observeRuleReturnsNullWhenMissing() = runTest {
        val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))

        assertEquals(null, repository.observeRule(999).first())
    }

    @Test
    fun getActiveRulesByTypeReturnsOnlyRequestedType() = runTest {
        val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))
        repository.insertRule(draft("domain.example", BlockRuleType.DOMAIN))
        repository.insertRule(draft("shorts", BlockRuleType.KEYWORD_SHORTS))
        repository.insertRule(draft("reels", BlockRuleType.KEYWORD_REELS))

        val result = repository.getActiveRulesByType(BlockRuleType.KEYWORD_SHORTS)

        assertTrue(result is AppResult.Success)
        val rules = (result as AppResult.Success).value
        assertEquals(1, rules.size)
        assertEquals("shorts", rules.first().pattern)
        assertEquals(BlockRuleType.KEYWORD_SHORTS, rules.first().type)
    }

    private fun draft(pattern: String, type: BlockRuleType) = BlockRuleDraft(
        pattern = pattern,
        type = type,
        createdAt = 1_000L,
    )
}
