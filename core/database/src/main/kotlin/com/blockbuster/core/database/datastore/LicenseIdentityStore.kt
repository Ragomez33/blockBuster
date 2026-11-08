package com.blockbuster.core.database.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class LicenseIdentityStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun getOrCreateUserId(): String {
        var userId: String? = null
        dataStore.edit { preferences ->
            val existing = preferences[LicensePreferenceKeys.USER_ID]
            if (existing != null) {
                userId = existing
            } else {
                val created = UUID.randomUUID().toString()
                preferences[LicensePreferenceKeys.USER_ID] = created
                userId = created
            }
        }
        return checkNotNull(userId) { "License identity was not persisted" }
    }

    suspend fun peekUserId(): String? =
        dataStore.data.first()[LicensePreferenceKeys.USER_ID]
}
