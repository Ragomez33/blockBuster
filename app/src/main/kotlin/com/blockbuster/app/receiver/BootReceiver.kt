package com.blockbuster.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.blockbuster.app.protection.ProtectionHealthChecker
import com.blockbuster.app.service.MonitoringForegroundService
import com.blockbuster.core.common.contract.ProtectionStateRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores protection after boot or app update only when the user left it
 * enabled. LOCKED_BOOT_COMPLETED uses the device-protected copy of the flag.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BootEntryPoint::class.java,
                )
                val repository = entryPoint.protectionStateRepository()
                val protectionActive = if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
                    repository.isBootProtectionEnabled()
                } else {
                    repository.isProtectionEnabled()
                }

                if (protectionActive) {
                    runCatching { MonitoringForegroundService.start(context) }
                        .onFailure { Log.w(TAG, "Unable to start monitoring after $action", it) }

                    val health = entryPoint.protectionHealthChecker().check()
                    Log.i(TAG, "Protection health after $action: $health")
                } else {
                    Log.i(TAG, "Protection disabled; monitoring not started after $action")
                }
            } catch (throwable: Throwable) {
                Log.w(TAG, "Boot protection check failed for $action", throwable)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BootEntryPoint {
    fun protectionStateRepository(): ProtectionStateRepository
    fun protectionHealthChecker(): ProtectionHealthChecker
}
