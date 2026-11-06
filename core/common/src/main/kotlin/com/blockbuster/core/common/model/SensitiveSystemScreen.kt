package com.blockbuster.core.common.model

enum class SensitiveScreenCategory {
    ACCESSIBILITY_SETTINGS,
    APP_INFO,
    DEVICE_ADMIN_SETTINGS,
}

data class SensitiveSystemScreen(
    val category: SensitiveScreenCategory,
    val packageName: String,
    val className: String?,
    val detectedAtEpochMillis: Long,
)
