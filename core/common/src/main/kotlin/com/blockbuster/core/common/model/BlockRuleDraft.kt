package com.blockbuster.core.common.model

/**
 * A new rule that has not been persisted yet, so it has no identifier.
 */
data class BlockRuleDraft(
    val pattern: String,
    val type: BlockRuleType,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
