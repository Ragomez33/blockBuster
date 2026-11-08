package com.blockbuster.core.common.model

/**
 * Remote license row normalized to domain types.
 */
data class RemoteSubscription(
    val userId: String,
    val isPremium: Boolean,
    val expiresAtEpochMillis: Long?,
    val paymentProvider: String?,
)
