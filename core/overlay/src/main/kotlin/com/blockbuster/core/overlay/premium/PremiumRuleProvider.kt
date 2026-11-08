package com.blockbuster.core.overlay.premium

import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extra Shorts/Reels rules unlocked by Premium. They are intentionally
 * keyword-based so they complement, not replace, the free SHORT_VIDEOS preset.
 */
@Singleton
class PremiumRuleProvider @Inject constructor() {

    fun strictShortsReelsRules(): List<BlockRule> = RULES.mapIndexed { index, pattern ->
        BlockRule(
            id = SYNTHETIC_ID_BASE - index,
            pattern = pattern,
            type = BlockRuleType.KEYWORD,
            isEnabled = true,
            createdAt = 0L,
        )
    }

    private companion object {
        const val SYNTHETIC_ID_BASE = -3_000_000L
        val RULES = listOf(
            "reels",
            "shorts",
            "shortsfeed",
            "reel",
            "spotlight",
        )
    }
}
