package com.blockbuster.core.common.model

enum class ProtectionThreatType {
    SENSITIVE_SCREEN,
    ADMIN_DISABLE_REQUESTED,
    ADMIN_DISABLED,
}

data class ProtectionThreat(
    val type: ProtectionThreatType,
    val screen: SensitiveSystemScreen?,
    val timestampEpochMillis: Long,
)
