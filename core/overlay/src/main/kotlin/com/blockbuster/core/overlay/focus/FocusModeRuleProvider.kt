package com.blockbuster.core.overlay.focus

import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.model.FocusSession
import com.blockbuster.core.common.model.LeisureAppCatalog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts the active Focus Mode session into package rules for leisure apps.
 * Rules exist only during a running work phase.
 */
@Singleton
class FocusModeRuleProvider @Inject constructor() {

    fun rules(session: FocusSession, nowEpochMillis: Long = System.currentTimeMillis()): List<BlockRule> {
        if (!session.isWorkBlockingActive(nowEpochMillis)) return emptyList()

        return LeisureAppCatalog.packages
            .sorted()
            .mapIndexed { index, packageName ->
                BlockRule(
                    id = SYNTHETIC_ID_BASE - index,
                    pattern = packageName,
                    type = BlockRuleType.PACKAGE,
                    isEnabled = true,
                    createdAt = 0L,
                )
            }
    }

    private companion object {
        const val SYNTHETIC_ID_BASE = -2_000_000L
    }
}
