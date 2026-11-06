package com.blockbuster.core.common.model

/**
 * Events published by the accessibility layer. Nothing in this contract knows
 * about Compose, overlays or persistence, which keeps the service decoupled.
 */
sealed interface DetectionEvent {
    val timestampEpochMillis: Long

    data class ScreenDetected(
        val packageName: String,
        val className: String?,
        override val timestampEpochMillis: Long,
        val target: String? = null,
    ) : DetectionEvent

    data class ServiceConnectionChanged(
        val connected: Boolean,
        override val timestampEpochMillis: Long,
    ) : DetectionEvent
}
