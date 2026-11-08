package com.blockbuster.core.common.contract

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Controls the WindowManager overlay. Implementations live in :core:overlay;
 * callers only see this contract.
 */
interface OverlayController {
    val isOverlayVisible: StateFlow<Boolean>
    val unlockEvents: SharedFlow<OverlayUnlock>

    fun showFriction(config: FrictionConfig)

    fun hideFriction()
}

data class OverlayUnlock(
    val target: String,
    val timestampEpochMillis: Long,
)

enum class FrictionMode {
    STANDARD,
    PROTECTION,
}

data class FrictionConfig(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val message: String,
    val dismissLabel: String,
    val mode: FrictionMode = FrictionMode.STANDARD,
    val frictionWindowMillis: Long = DEFAULT_FRICTION_WINDOW_MILLIS,
) {
    companion object {
        const val DEFAULT_FRICTION_WINDOW_MILLIS = 60_000L
    }
}
