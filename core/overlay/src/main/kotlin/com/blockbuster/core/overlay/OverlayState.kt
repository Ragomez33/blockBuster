package com.blockbuster.core.overlay

import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.SensitiveSystemScreen

data class BlockMatch(
    val rule: BlockRule,
    val target: String,
    val normalizedTarget: String,
    val detectedAtEpochMillis: Long,
)

data class BlockActivationKey(
    val ruleId: Long,
    val normalizedTarget: String,
)

sealed interface OverlayState {
    data object Idle : OverlayState
    data class Blocking(
        val match: BlockMatch,
        val audited: Boolean,
    ) : OverlayState
    data class Dismissed(val match: BlockMatch) : OverlayState
    data class ProtectionBlocking(val screen: SensitiveSystemScreen?) : OverlayState
}
