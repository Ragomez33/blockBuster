package com.blockbuster.core.overlay

import android.os.SystemClock

/**
 * Source of truth for one friction session. It lives outside the composition
 * so the remaining time and typed text survive host recreation.
 */
data class FrictionSession(
    val target: String,
    val requiredText: String,
    val startedAtElapsedRealtime: Long,
    val windowMillis: Long = WINDOW_MILLIS,
    val input: String = "",
    val warningVisible: Boolean = false,
    val warningUntilElapsedRealtime: Long? = null,
) {
    fun remainingMillis(nowElapsedRealtime: Long = SystemClock.elapsedRealtime()): Long =
        (windowMillis - (nowElapsedRealtime - startedAtElapsedRealtime)).coerceAtLeast(0L)

    fun remainingSeconds(nowElapsedRealtime: Long = SystemClock.elapsedRealtime()): Int =
        ((remainingMillis(nowElapsedRealtime) + 999L) / 1_000L).toInt()

    fun canSubmit(nowElapsedRealtime: Long = SystemClock.elapsedRealtime()): Boolean =
        remainingMillis(nowElapsedRealtime) == 0L && input == requiredText

    companion object {
        const val WINDOW_MILLIS = 60_000L
        const val WARNING_DURATION_MILLIS = 3_000L
    }
}
