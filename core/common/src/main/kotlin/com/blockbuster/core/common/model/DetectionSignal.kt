package com.blockbuster.core.common.model

/**
 * Hot state for the latest accessibility detection. Keeping the target typed
 * separately from [DetectionEvent.ScreenDetected] lets new collectors read the
 * current detection without a replay buffer.
 */
data class DetectionSignal(
    val packageName: String,
    val target: String?,
    val className: String?,
    val timestampEpochMillis: Long,
)
