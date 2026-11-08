package com.blockbuster.core.overlay.matching

import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultBlockRuleMatcherTest {

    private val matcher = DefaultBlockRuleMatcher()

    @Test
    fun matchesDomainExactly() {
        val rule = rule(id = 1, pattern = "example.com", type = BlockRuleType.DOMAIN)

        val match = matcher.findMatch(
            target = "https://example.com/watch?v=1",
            rules = listOf(rule),
            detectedAtEpochMillis = 1_000L,
        )

        assertEquals(rule, match?.rule)
        assertEquals("example.com", match?.normalizedTarget)
        assertEquals(1_000L, match?.detectedAtEpochMillis)
    }

    @Test
    fun matchesSubdomainAndIgnoresSchemeAndCase() {
        val rule = rule(id = 1, pattern = "Example.COM", type = BlockRuleType.DOMAIN)

        val match = matcher.findMatch(
            target = "HTTPS://WWW.Example.com/path?q=1",
            rules = listOf(rule),
            detectedAtEpochMillis = 1_000L,
        )

        assertEquals(rule, match?.rule)
        assertEquals("example.com", match?.normalizedTarget)
    }

    @Test
    fun matchesKeywordsIgnoringCase() {
        val rule = rule(id = 1, pattern = "shorts", type = BlockRuleType.KEYWORD_SHORTS)

        val match = matcher.findMatch(
            target = "Funny SHORTS compilation",
            rules = listOf(rule),
            detectedAtEpochMillis = 1_000L,
        )

        assertEquals(rule, match?.rule)
        assertEquals("funny shorts compilation", match?.normalizedTarget)
    }

    @Test
    fun returnsNullForBlankOrMissingTarget() {
        val rule = rule(id = 1, pattern = "example.com", type = BlockRuleType.DOMAIN)

        assertNull(matcher.findMatch(null, listOf(rule), 1_000L))
        assertNull(matcher.findMatch("   ", listOf(rule), 1_000L))
    }

    @Test
    fun choosesLowestRuleIdWhenSeveralMatch() {
        val high = rule(id = 9, pattern = "example.com", type = BlockRuleType.DOMAIN)
        val low = rule(id = 2, pattern = "example.com", type = BlockRuleType.DOMAIN)

        val match = matcher.findMatch(
            target = "https://example.com",
            rules = listOf(high, low),
            detectedAtEpochMillis = 1_000L,
        )

        assertEquals(low, match?.rule)
    }

    @Test
    fun matchesPackageSignatureWithoutTargetText() {
        val rule = rule(
            id = 1,
            pattern = "com.zhiliaoapp.musically",
            type = BlockRuleType.PACKAGE,
        )

        val match = matcher.findMatch(
            target = null,
            rules = listOf(rule),
            detectedAtEpochMillis = 1_000L,
            packageName = "com.zhiliaoapp.musically",
        )

        assertEquals(rule, match?.rule)
        assertEquals("com.zhiliaoapp.musically", match?.normalizedTarget)
    }

    @Test
    fun packageSignatureDoesNotMatchDifferentPackage() {
        val rule = rule(
            id = 1,
            pattern = "com.zhiliaoapp.musically",
            type = BlockRuleType.PACKAGE,
        )

        assertNull(
            matcher.findMatch(
                target = null,
                rules = listOf(rule),
                detectedAtEpochMillis = 1_000L,
                packageName = "com.android.chrome",
            ),
        )
    }

    @Test
    fun matchesGenericKeywords() {
        val rule = rule(id = 1, pattern = "visual search", type = BlockRuleType.KEYWORD)

        val match = matcher.findMatch(
            target = "Try Visual Search on this image",
            rules = listOf(rule),
            detectedAtEpochMillis = 1_000L,
        )

        assertEquals(rule, match?.rule)
    }

    @Test
    fun matchesKeywordInAccessibilityClassName() {
        val rule = rule(id = 1, pattern = "reels", type = BlockRuleType.KEYWORD)

        val match = matcher.findMatch(
            target = null,
            rules = listOf(rule),
            detectedAtEpochMillis = 1_000L,
            packageName = "com.instagram.android",
            className = "com.instagram.android.ReelsViewerActivity",
        )

        assertEquals(rule, match?.rule)
        assertTrue(match?.normalizedTarget?.contains("reels") == true)
    }

    @Test
    fun ignoresDisabledRules() {
        val disabled = rule(
            id = 1,
            pattern = "example.com",
            type = BlockRuleType.DOMAIN,
            enabled = false,
        )

        assertNull(matcher.findMatch("https://example.com", listOf(disabled), 1_000L))
    }

    private fun rule(
        id: Long,
        pattern: String,
        type: BlockRuleType,
        enabled: Boolean = true,
    ) = BlockRule(
        id = id,
        pattern = pattern,
        type = type,
        isEnabled = enabled,
        createdAt = 1_000L,
    )
}
