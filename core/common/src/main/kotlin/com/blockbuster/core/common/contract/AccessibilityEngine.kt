package com.blockbuster.core.common.contract

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only view of the accessibility engine connection state. The actual
 * engine is the platform AccessibilityService; UI and overlay code depend only
 * on this contract.
 */
interface AccessibilityEngine {
    val connectionState: StateFlow<AccessibilityConnectionState>
}

enum class AccessibilityConnectionState {
    DISCONNECTED,
    CONNECTED,
}
