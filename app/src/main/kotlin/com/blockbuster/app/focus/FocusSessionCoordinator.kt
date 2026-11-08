package com.blockbuster.app.focus

import com.blockbuster.core.common.contract.FocusSessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the Focus Mode clock. It is intentionally independent from the UI so a
 * session keeps transitioning while the app is in the background or the
 * monitoring service is alive.
 */
@Singleton
class FocusSessionCoordinator @Inject constructor(
    private val repository: FocusSessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                runCatching {
                    repository.advancePhase()
                }
                delay(TICK_MILLIS)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}
