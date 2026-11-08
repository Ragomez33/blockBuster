package com.blockbuster.core.database.preset

import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.model.ProtectionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionPresetCatalogTest {

    @Test
    fun everyPresetHasAtLeastOneRule() {
        ProtectionPreset.entries.forEach { preset ->
            assertTrue(
                "Preset $preset has no rules",
                ProtectionPresetCatalog.rulesFor(preset).isNotEmpty(),
            )
            assertEquals(
                ProtectionPresetCatalog.rulesFor(preset).size,
                ProtectionPresetCatalog.ruleCount(preset),
            )
        }
    }

    @Test
    fun adultContentCombinesDomainsAndKeywords() {
        val types = ProtectionPresetCatalog.rulesFor(ProtectionPreset.ADULT_CONTENT)
            .map { rule -> rule.type }
            .toSet()

        assertTrue(BlockRuleType.DOMAIN in types)
        assertTrue(BlockRuleType.KEYWORD in types)
    }

    @Test
    fun shortVideosIncludesTikTokPackageSignatures() {
        val packages = ProtectionPresetCatalog.rulesFor(ProtectionPreset.SHORT_VIDEOS)
            .filter { rule -> rule.type == BlockRuleType.PACKAGE }
            .map { rule -> rule.pattern }
            .toSet()

        assertTrue("com.zhiliaoapp.musically" in packages)
        assertTrue("com.ss.android.ugc.trill" in packages)
    }

    @Test
    fun visualSearchIncludesImageSearchSignatures() {
        val patterns = ProtectionPresetCatalog.rulesFor(ProtectionPreset.VISUAL_SEARCH)
            .map { rule -> rule.pattern }

        assertTrue(patterns.any { pattern -> "imghp" in pattern })
        assertTrue(patterns.any { pattern -> "bing.com/images" in pattern })
    }

    @Test
    fun activeRulesAreOnlyGeneratedForEnabledPresets() {
        val adultOnly = ProtectionPresetCatalog.activeRules(
            setOf(ProtectionPreset.ADULT_CONTENT),
        )
        val adultIds = ProtectionPresetCatalog.rulesFor(ProtectionPreset.ADULT_CONTENT)
            .indices
            .map { index -> ProtectionPresetCatalog.syntheticId(ProtectionPreset.ADULT_CONTENT, index) }
            .toSet()

        assertTrue(adultOnly.isNotEmpty())
        assertTrue(adultOnly.all { rule -> rule.id in adultIds })
        assertFalse(adultOnly.any { rule -> rule.id in shortVideoIds() })
    }

    @Test
    fun noRulesAreGeneratedWhenNoPresetIsEnabled() {
        assertTrue(ProtectionPresetCatalog.activeRules(emptySet()).isEmpty())
    }

    @Test
    fun syntheticIdsAreUniqueAcrossPresets() {
        val allIds = ProtectionPresetCatalog.activeRules(ProtectionPreset.entries.toSet())
            .map { rule -> rule.id }

        assertEquals(allIds.size, allIds.toSet().size)
        assertTrue(allIds.all { id -> id < 0L })
    }

    private fun shortVideoIds(): Set<Long> =
        ProtectionPresetCatalog.rulesFor(ProtectionPreset.SHORT_VIDEOS)
            .indices
            .map { index ->
                ProtectionPresetCatalog.syntheticId(ProtectionPreset.SHORT_VIDEOS, index)
            }
            .toSet()
}
