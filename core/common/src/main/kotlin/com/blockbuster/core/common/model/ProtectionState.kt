package com.blockbuster.core.common.model

data class ProtectionState(
    val enabled: Boolean,
    val lastUpdatedEpochMillis: Long,
    val source: String,
)
