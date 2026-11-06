package com.blockbuster.core.common.model

/**
 * Immutable local audit record for a block event.
 *
 * @property ruleId Rule that produced the block.
 * @property target Domain, text or package that was blocked.
 * @property timestamp Event time in milliseconds since the epoch.
 */
data class BlockLog(
    val id: Long,
    val ruleId: Long,
    val target: String,
    val timestamp: Long,
)
