package com.blockbuster.core.database.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.blockbuster.core.common.model.BlockTimer
import com.blockbuster.core.common.model.BlockingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockTimerDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val timer: Flow<BlockTimer?> = dataStore.data
        .map { preferences ->
            val packageName = preferences[PreferenceKeys.ACTIVE_TIMER_PACKAGE]
                ?: return@map null
            val mode = preferences[PreferenceKeys.ACTIVE_TIMER_MODE]
                ?.let { storedMode -> runCatching { BlockingMode.valueOf(storedMode) }.getOrNull() }
                ?: return@map null
            val startedAt = preferences[PreferenceKeys.ACTIVE_TIMER_STARTED_AT]
                ?: return@map null

            BlockTimer(
                packageName = packageName,
                mode = mode,
                startedAtEpochMillis = startedAt,
                expiresAtEpochMillis = preferences[PreferenceKeys.ACTIVE_TIMER_EXPIRES_AT],
            )
        }
        .distinctUntilChanged()

    val monitoringEnabled: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferenceKeys.MONITORING_ENABLED] ?: false }
        .distinctUntilChanged()

    suspend fun saveTimer(timer: BlockTimer) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.ACTIVE_TIMER_PACKAGE] = timer.packageName
            preferences[PreferenceKeys.ACTIVE_TIMER_MODE] = timer.mode.name
            preferences[PreferenceKeys.ACTIVE_TIMER_STARTED_AT] = timer.startedAtEpochMillis
            val expiresAt = timer.expiresAtEpochMillis
            if (expiresAt != null) {
                preferences[PreferenceKeys.ACTIVE_TIMER_EXPIRES_AT] = expiresAt
            } else {
                preferences.remove(PreferenceKeys.ACTIVE_TIMER_EXPIRES_AT)
            }
        }
    }

    suspend fun clearTimer() {
        dataStore.edit { preferences ->
            preferences.remove(PreferenceKeys.ACTIVE_TIMER_PACKAGE)
            preferences.remove(PreferenceKeys.ACTIVE_TIMER_MODE)
            preferences.remove(PreferenceKeys.ACTIVE_TIMER_STARTED_AT)
            preferences.remove(PreferenceKeys.ACTIVE_TIMER_EXPIRES_AT)
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.MONITORING_ENABLED] = enabled
        }
    }
}
