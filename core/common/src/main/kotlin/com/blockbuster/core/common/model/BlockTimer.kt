package com.blockbuster.core.common.model

/**
 * The single active timer persisted in DataStore.
 */
data class BlockTimer(
    val packageName: String,
    val mode: BlockingMode,
    val startedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long?,
) {
    fun isExpired(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtEpochMillis != null && nowEpochMillis >= expiresAtEpochMillis

    fun isActive(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        !isExpired(nowEpochMillis)
}
