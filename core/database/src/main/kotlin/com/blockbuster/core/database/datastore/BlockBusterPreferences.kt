package com.blockbuster.core.database.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private const val PREFERENCES_NAME = "blockbuster_preferences"

val Context.blockBusterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_NAME,
)

internal object PreferenceKeys {
    val ACTIVE_TIMER_PACKAGE = stringPreferencesKey("active_timer_package")
    val ACTIVE_TIMER_MODE = stringPreferencesKey("active_timer_mode")
    val ACTIVE_TIMER_STARTED_AT = longPreferencesKey("active_timer_started_at")
    val ACTIVE_TIMER_EXPIRES_AT = longPreferencesKey("active_timer_expires_at")
    val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
}
