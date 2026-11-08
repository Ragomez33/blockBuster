package com.blockbuster.core.overlay

import android.os.SystemClock

/**
 * Retains the active friction session independently of Compose. A session for
 * the same target is reused when the overlay host is recreated; a session for a
 * different target starts from 60 seconds with an empty input.
 */
class FrictionSessionStore(
    private val nowElapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) {
    var current: FrictionSession? = null
        private set

    fun obtain(
        target: String,
        requiredText: String,
        windowMillis: Long = FrictionSession.WINDOW_MILLIS,
    ): FrictionSession {
        val existing = current
        if (existing != null &&
            existing.target == target &&
            existing.windowMillis == windowMillis
        ) {
            return existing
        }

        return FrictionSession(
            target = target,
            requiredText = requiredText,
            startedAtElapsedRealtime = nowElapsedRealtime(),
            windowMillis = windowMillis,
        ).also { current = it }
    }

    fun updateInput(input: String) {
        current = current?.copy(input = input)
    }

    fun clear() {
        current = null
    }
}
