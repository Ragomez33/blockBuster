package com.blockbuster.core.overlay.matching

import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.model.BlocklistEntry
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistMatcherTest {

    private val matcher = BlocklistMatcher()

    @Test
    fun whitelistMatchesAppsExactPackage() {
        val entries = listOf(entry("com.instagram.android", BlocklistTargetType.APP, BlocklistType.WHITELIST))

        assertTrue(
            matcher.isWhitelisted(
                packageName = "com.instagram.android",
                target = null,
                className = null,
                entries = entries,
            ),
        )
        assertFalse(
            matcher.isWhitelisted(
                packageName = "com.other.app",
                target = null,
                className = null,
                entries = entries,
            ),
        )
    }

    @Test
    fun whitelistMatchesWebsiteSubdomain() {
        val entries = listOf(entry("example.com", BlocklistTargetType.WEBSITE, BlocklistType.WHITELIST))

        assertTrue(
            matcher.isWhitelisted(
                packageName = "com.android.chrome",
                target = "https://news.example.com/article",
                className = null,
                entries = entries,
            ),
        )
    }

    @Test
    fun whitelistMatchesKeywordInClassName() {
        val entries = listOf(entry("reels", BlocklistTargetType.KEYWORD, BlocklistType.WHITELIST))

        assertTrue(
            matcher.isWhitelisted(
                packageName = "com.instagram.android",
                target = null,
                className = "com.instagram.android.ReelsActivity",
                entries = entries,
            ),
        )
    }

    @Test
    fun blacklistRulesUseExpectedTypes() {
        val rules = matcher.blacklistRules(
            listOf(
                entry("com.app", BlocklistTargetType.APP, BlocklistType.BLACKLIST),
                entry("example.com", BlocklistTargetType.WEBSITE, BlocklistType.BLACKLIST),
                entry("shorts", BlocklistTargetType.KEYWORD, BlocklistType.BLACKLIST),
            ),
        )

        assertEquals(
            listOf(BlockRuleType.PACKAGE, BlockRuleType.DOMAIN, BlockRuleType.KEYWORD),
            rules.map { rule -> rule.type },
        )
        assertTrue(rules.all { rule -> rule.id < 0L })
    }

    @Test
    fun blacklistEntriesDoNotAffectWhitelistCheck() {
        val entries = listOf(
            entry("example.com", BlocklistTargetType.WEBSITE, BlocklistType.BLACKLIST),
        )

        assertFalse(
            matcher.isWhitelisted(
                packageName = "com.android.chrome",
                target = "https://example.com",
                className = null,
                entries = entries,
            ),
        )
    }

    private fun entry(
        value: String,
        targetType: BlocklistTargetType,
        listType: BlocklistType,
    ) = BlocklistEntry(
        id = 1L,
        value = value,
        targetType = targetType,
        listType = listType,
        createdAt = 1_000L,
    )
}
