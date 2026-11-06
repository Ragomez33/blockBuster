package com.blockbuster.core.common.event

import com.blockbuster.core.common.model.DetectionEvent
import com.blockbuster.core.common.model.DetectionSignal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local reactive bus used to move detections from :core:accessibility to
 * :core:overlay without either module knowing about the other.
 *
 * [events] carries every detection; [currentForegroundPackage] keeps the latest
 * foreground package as hot state so new collectors do not need a replay cache.
 */
interface BlockEventBus {
    val events: SharedFlow<DetectionEvent>
    val currentForegroundPackage: StateFlow<String?>
    val currentDetection: StateFlow<DetectionSignal?>

    fun publish(event: DetectionEvent)
}

/**
 * Default in-process implementation. It is bound as a Hilt singleton by :app.
 */
class MutableBlockEventBus : BlockEventBus {
    private val _events = MutableSharedFlow<DetectionEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<DetectionEvent> = _events.asSharedFlow()

    private val _currentForegroundPackage = MutableStateFlow<String?>(null)
    override val currentForegroundPackage: StateFlow<String?> =
        _currentForegroundPackage.asStateFlow()

    private val _currentDetection = MutableStateFlow<DetectionSignal?>(null)
    override val currentDetection: StateFlow<DetectionSignal?> =
        _currentDetection.asStateFlow()

    override fun publish(event: DetectionEvent) {
        when (event) {
            is DetectionEvent.ScreenDetected -> {
                _currentForegroundPackage.value = event.packageName
                _currentDetection.value = DetectionSignal(
                    packageName = event.packageName,
                    target = event.target,
                    className = event.className,
                    timestampEpochMillis = event.timestampEpochMillis,
                )
            }

            is DetectionEvent.ServiceConnectionChanged -> {
                if (!event.connected) {
                    _currentForegroundPackage.value = null
                    _currentDetection.value = null
                }
            }
        }
        _events.tryEmit(event)
    }
}
