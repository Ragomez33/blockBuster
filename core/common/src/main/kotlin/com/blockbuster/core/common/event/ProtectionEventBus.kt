package com.blockbuster.core.common.event

import com.blockbuster.core.common.model.ProtectionThreat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-priority security bus. It bypasses content-rule evaluation so sensitive
 * system screens and admin events reach the overlay coordinator immediately.
 */
interface ProtectionEventBus {
    val events: SharedFlow<ProtectionThreat>
    val currentThreat: StateFlow<ProtectionThreat?>

    fun publish(threat: ProtectionThreat)
    fun clear()
}

class MutableProtectionEventBus : ProtectionEventBus {
    private val _events = MutableSharedFlow<ProtectionThreat>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<ProtectionThreat> = _events.asSharedFlow()

    private val _currentThreat = MutableStateFlow<ProtectionThreat?>(null)
    override val currentThreat: StateFlow<ProtectionThreat?> = _currentThreat.asStateFlow()

    override fun publish(threat: ProtectionThreat) {
        _currentThreat.value = threat
        _events.tryEmit(threat)
    }

    override fun clear() {
        _currentThreat.value = null
    }
}
