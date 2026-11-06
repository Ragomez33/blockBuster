package com.blockbuster.core.overlay.support

import com.blockbuster.core.common.contract.FrictionConfig
import com.blockbuster.core.common.contract.OverlayController
import com.blockbuster.core.common.contract.OverlayUnlock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeOverlayController : OverlayController {
    private val _isOverlayVisible = MutableStateFlow(false)
    override val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _unlockEvents = MutableSharedFlow<OverlayUnlock>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val unlockEvents: SharedFlow<OverlayUnlock> = _unlockEvents.asSharedFlow()

    var shownConfig: FrictionConfig? = null
    var showCount: Int = 0
    var hideCount: Int = 0

    override fun showFriction(config: FrictionConfig) {
        shownConfig = config
        showCount += 1
        _isOverlayVisible.value = true
    }

    override fun hideFriction() {
        hideCount += 1
        _isOverlayVisible.value = false
    }

    fun simulateUserDismiss() {
        _isOverlayVisible.value = false
    }

    fun emitUnlock(target: String, timestamp: Long = 0L) {
        _unlockEvents.tryEmit(OverlayUnlock(target, timestamp))
    }
}

