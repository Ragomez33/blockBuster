package com.blockbuster.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.blockbuster.core.common.event.BlockEventBus
import com.blockbuster.core.common.event.ProtectionEventBus
import com.blockbuster.core.common.model.DetectionEvent
import com.blockbuster.core.common.model.ProtectionThreat
import com.blockbuster.core.common.model.ProtectionThreatType
import com.blockbuster.core.accessibility.settings.SettingsInterceptor
import com.blockbuster.core.accessibility.target.TargetExtractor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Pure, presentation-agnostic accessibility engine.
 *
 * It does not load rules, decide what is blocked, or draw anything. It only
 * translates relevant platform events into [DetectionEvent]s on the local bus.
 */
@AndroidEntryPoint
class BlockBusterAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var eventBus: BlockEventBus

    @Inject
    lateinit var stateHolder: AccessibilityEngineStateHolder

    @Inject
    lateinit var targetExtractor: TargetExtractor

    @Inject
    lateinit var settingsInterceptor: SettingsInterceptor

    @Inject
    lateinit var protectionEventBus: ProtectionEventBus

    private var lastPackageName: String? = null
    private var lastTarget: String? = null
    private var lastEventUptimeMillis: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        stateHolder.setConnected(true)
        eventBus.publish(
            DetectionEvent.ServiceConnectionChanged(
                connected = true,
                timestampEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> Unit

            else -> return
        }

        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (packageName == applicationContext.packageName) return

        val className = event.className?.toString()
        val sensitiveScreen = settingsInterceptor.inspect(packageName, className)
        if (sensitiveScreen != null) {
            protectionEventBus.publish(
                ProtectionThreat(
                    type = ProtectionThreatType.SENSITIVE_SCREEN,
                    screen = sensitiveScreen,
                    timestampEpochMillis = System.currentTimeMillis(),
                ),
            )
        } else {
            protectionEventBus.clear()
        }

        val target = targetExtractor.extract(event)
        val nowUptime = SystemClock.uptimeMillis()
        val isDuplicate = packageName == lastPackageName &&
            target == lastTarget &&
            nowUptime - lastEventUptimeMillis < EVENT_THROTTLE_MILLIS
        if (isDuplicate) return

        lastPackageName = packageName
        lastTarget = target
        lastEventUptimeMillis = nowUptime

        eventBus.publish(
            DetectionEvent.ScreenDetected(
                packageName = packageName,
                className = event.className?.toString(),
                timestampEpochMillis = System.currentTimeMillis(),
                target = target,
            ),
        )    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        markDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        markDisconnected()
        super.onDestroy()
    }

    private fun markDisconnected() {
        stateHolder.setConnected(false)
        protectionEventBus.clear()
        eventBus.publish(
            DetectionEvent.ServiceConnectionChanged(
                connected = false,
                timestampEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val EVENT_THROTTLE_MILLIS = 150L
    }
}
