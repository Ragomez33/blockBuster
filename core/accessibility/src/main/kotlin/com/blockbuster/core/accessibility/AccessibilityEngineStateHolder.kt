package com.blockbuster.core.accessibility

import com.blockbuster.core.common.contract.AccessibilityConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide connection state updated by [BlockBusterAccessibilityService].
 * Holding the state separately from the service lets the UI and overlay observe
 * it without touching service internals.
 */
@Singleton
class AccessibilityEngineStateHolder @Inject constructor() {
    private val _connectionState = MutableStateFlow(AccessibilityConnectionState.DISCONNECTED)
    val connectionState: StateFlow<AccessibilityConnectionState> =
        _connectionState.asStateFlow()

    fun setConnected(connected: Boolean) {
        _connectionState.value = if (connected) {
            AccessibilityConnectionState.CONNECTED
        } else {
            AccessibilityConnectionState.DISCONNECTED
        }
    }
}
