package com.blockbuster.core.common.model

/**
 * Master protection profiles exposed to the user as a single switch.
 *
 * Each preset maps to a curated set of internal blocking rules so the user does
 * not need to create rules manually.
 */
enum class ProtectionPreset {
    ADULT_CONTENT,
    SHORT_VIDEOS,
    VISUAL_SEARCH,
}
