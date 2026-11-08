package com.blockbuster.core.database.preset

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.blockbuster.core.common.contract.ProtectionPresetRepository
import com.blockbuster.core.common.model.ProtectionPreset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class ProtectionPresetRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ProtectionPresetRepository {

    override fun observeEnabledPresets(): Flow<Set<ProtectionPreset>> =
        dataStore.data
            .map { preferences ->
                ProtectionPreset.entries
                    .filterTo(linkedSetOf()) { preset ->
                        preferences[preferenceKey(preset)] ?: false
                    }
            }
            .distinctUntilChanged()

    override suspend fun isPresetEnabled(preset: ProtectionPreset): Boolean =
        dataStore.data.first()[preferenceKey(preset)] ?: false

    override suspend fun setPresetEnabled(preset: ProtectionPreset, enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[preferenceKey(preset)] = enabled
        }
    }

    private fun preferenceKey(preset: ProtectionPreset) =
        booleanPreferencesKey("preset_${preset.name.lowercase()}_enabled")
}
