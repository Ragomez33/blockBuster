package com.blockbuster.core.common.model

/**
 * Category that determines how [BlockRule.pattern] is interpreted.
 */
enum class BlockRuleType {
    DOMAIN,
    PACKAGE,
    KEYWORD,
    KEYWORD_SHORTS,
    KEYWORD_REELS,
}
