package com.blockbuster.core.database.protection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.blockbuster.core.common.contract.ProtectionStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

internal object ProtectionPreferenceKeys {
    val PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
    val PROTECTION_UPDATED_AT = longPreferencesKey("protection_updated_at")
    val PROTECTION_SOURCE = stringPreferencesKey("protection_source")

    val BOOT_PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
    val BOOT_PROTECTION_UPDATED_AT = longPreferencesKey("protection_updated_at")
}

@Singleton
class ProtectionStateRepositoryImpl @Inject constructor(
    private val mainDataStore: DataStore<Preferences>,
    @BootDataStore private val bootDataStore: DataStore<Preferences>,
) : ProtectionStateRepository {

    override fun observeProtectionEnabled(): Flow<Boolean> =
        mainDataStore.data
            .map { preferences ->
                preferences[ProtectionPreferenceKeys.PROTECTION_ENABLED] ?: false
            }
            .distinctUntilChanged()

    override suspend fun isProtectionEnabled(): Boolean =
        mainDataStore.data.first()[ProtectionPreferenceKeys.PROTECTION_ENABLED] ?: false

    override suspend fun setProtectionEnabled(enabled: Boolean, source: String) {
        val now = System.currentTimeMillis()
        mainDataStore.edit { preferences ->
            preferences[ProtectionPreferenceKeys.PROTECTION_ENABLED] = enabled
            preferences[ProtectionPreferenceKeys.PROTECTION_UPDATED_AT] = now
            preferences[ProtectionPreferenceKeys.PROTECTION_SOURCE] = source
        }
        bootDataStore.edit { preferences ->
            preferences[ProtectionPreferenceKeys.BOOT_PROTECTION_ENABLED] = enabled
            preferences[ProtectionPreferenceKeys.BOOT_PROTECTION_UPDATED_AT] = now
        }
    }

    override suspend fun isBootProtectionEnabled(): Boolean =
        bootDataStore.data.first()[ProtectionPreferenceKeys.BOOT_PROTECTION_ENABLED] ?: false

    override suspend fun setBootProtectionEnabled(enabled: Boolean) {
        bootDataStore.edit { preferences ->
            preferences[ProtectionPreferenceKeys.BOOT_PROTECTION_ENABLED] = enabled
            preferences[ProtectionPreferenceKeys.BOOT_PROTECTION_UPDATED_AT] =
                System.currentTimeMillis()
        }
    }
}
