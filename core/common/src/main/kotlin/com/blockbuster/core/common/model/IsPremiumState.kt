package com.blockbuster.core.common.model

/**
 * Effective subscription state. A missing or unreadable value is treated as Free.
 */
data class IsPremiumState(
    val isPremium: Boolean = false,
    val expiresAtEpochMillis: Long? = null,
    val paymentProvider: String? = null,
) {
    fun isActive(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        isPremium && (expiresAtEpochMillis == null || expiresAtEpochMillis > nowEpochMillis)
}
