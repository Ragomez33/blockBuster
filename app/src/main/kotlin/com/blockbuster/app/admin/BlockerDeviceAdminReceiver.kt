package com.blockbuster.app.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.blockbuster.app.R
import com.blockbuster.app.service.MonitoringForegroundService
import com.blockbuster.core.common.contract.ProtectionStateRepository
import com.blockbuster.core.common.event.ProtectionEventBus
import com.blockbuster.core.common.model.ProtectionThreat
import com.blockbuster.core.common.model.ProtectionThreatType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Best-effort device administration layer. Android can still remove the admin;
 * this receiver warns, publishes a high-priority threat and records the outcome.
 */
class BlockerDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        launchStateUpdate(context, enabled = true, source = "device_admin_enabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        entryPoint(context)?.protectionEventBus()?.publish(
            ProtectionThreat(
                type = ProtectionThreatType.ADMIN_DISABLE_REQUESTED,
                screen = null,
                timestampEpochMillis = System.currentTimeMillis(),
            ),
        )
        runCatching { MonitoringForegroundService.start(context) }
        return context.getString(R.string.device_admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        entryPoint(context)?.protectionEventBus()?.publish(
            ProtectionThreat(
                type = ProtectionThreatType.ADMIN_DISABLED,
                screen = null,
                timestampEpochMillis = System.currentTimeMillis(),
            ),
        )
        // Preserve the user's monitoring intent; only the admin layer changed.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val repository = entryPoint(context)?.protectionStateRepository() ?: return@runCatching
                val enabled = repository.isProtectionEnabled()
                repository.setProtectionEnabled(enabled, "admin_disabled")
            }.onFailure { Log.w(TAG, "Unable to record admin disable", it) }
        }
    }

    private fun launchStateUpdate(context: Context, enabled: Boolean, source: String) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                entryPoint(context)?.protectionStateRepository()
                    ?.setProtectionEnabled(enabled, source)
            }.onFailure { Log.w(TAG, "Unable to update protection state", it) }
        }
    }

    private fun entryPoint(context: Context): DeviceAdminEntryPoint? =
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                DeviceAdminEntryPoint::class.java,
            )
        }.getOrNull()

    private companion object {
        const val TAG = "BlockerDeviceAdmin"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DeviceAdminEntryPoint {
    fun protectionEventBus(): ProtectionEventBus
    fun protectionStateRepository(): ProtectionStateRepository
}
