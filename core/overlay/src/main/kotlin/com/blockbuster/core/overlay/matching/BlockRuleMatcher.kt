package com.blockbuster.core.overlay.matching

import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.overlay.BlockMatch

interface BlockRuleMatcher {
    fun findMatch(
        target: String?,
        rules: List<BlockRule>,
        detectedAtEpochMillis: Long,
    ): BlockMatch?
}
