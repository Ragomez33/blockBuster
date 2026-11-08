package com.blockbuster.core.common.model

/**
 * Advanced capabilities that require a Premium subscription.
 */
enum class PremiumFeature {
    SHORTS_REELS_RESTRICTIONS,
    FRICTION_TIME_CUSTOMIZATION,
    ACCOUNTABILITY_PARTNER,
}

/**
 * Locally persisted advanced settings. Their effect is only applied when
 * [IsPremiumState.isPremium] is true.
 */
data class PremiumSettings(
    val strictShortsReels: Boolean = false,
    val frictionWindowMillis: Long = DEFAULT_FRICTION_WINDOW_MILLIS,
    val accountabilityPartner: String? = null,
) {
    companion object {
        const val DEFAULT_FRICTION_WINDOW_MILLIS = 60_000L
        const val MIN_FRICTION_WINDOW_MILLIS = 15_000L
        const val MAX_FRICTION_WINDOW_MILLIS = 180_000L
        const val MAX_PARTNER_LENGTH = 120

        fun sanitizeWindow(value: Long): Long =
            value.coerceIn(MIN_FRICTION_WINDOW_MILLIS, MAX_FRICTION_WINDOW_MILLIS)

        fun sanitizePartner(value: String?): String? =
            value?.trim()?.take(MAX_PARTNER_LENGTH)?.takeIf { it.isNotEmpty() }
    }
}
