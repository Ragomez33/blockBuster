package com.blockbuster.core.common.model

enum class BlocklistType {
    BLACKLIST,
    WHITELIST,
}

enum class BlocklistTargetType {
    APP,
    WEBSITE,
    KEYWORD,
}

/**
 * A user-defined entry in a blacklist or whitelist.
 *
 * APP values are package identifiers, WEBSITE values are normalized hosts and
 * KEYWORD values are free-form text.
 */
data class BlocklistEntry(
    val id: Long,
    val value: String,
    val targetType: BlocklistTargetType,
    val listType: BlocklistType,
    val createdAt: Long,
)

data class BlocklistEntryDraft(
    val value: String,
    val targetType: BlocklistTargetType,
    val listType: BlocklistType,
    val createdAt: Long = System.currentTimeMillis(),
)
