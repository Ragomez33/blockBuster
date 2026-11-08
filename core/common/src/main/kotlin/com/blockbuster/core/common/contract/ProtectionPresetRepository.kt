package com.blockbuster.core.common.contract

import com.blockbuster.core.common.model.ProtectionPreset
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for the state of the master protection presets.
 *
 * Preset content itself is static and owned by the data layer; this contract
 * only tracks which presets the user has enabled.
 */
interface ProtectionPresetRepository {
    fun observeEnabledPresets(): Flow<Set<ProtectionPreset>>

    suspend fun isPresetEnabled(preset: ProtectionPreset): Boolean

    suspend fun setPresetEnabled(preset: ProtectionPreset, enabled: Boolean)
}
