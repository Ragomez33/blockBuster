package com.blockbuster.core.common.contract

import kotlinx.coroutines.flow.Flow

/**
 * Persists the user's protection intent in both credential-protected and
 * device-protected storage, so boot receivers can make a decision before unlock.
 */
interface ProtectionStateRepository {
    fun observeProtectionEnabled(): Flow<Boolean>

    suspend fun isProtectionEnabled(): Boolean

    suspend fun setProtectionEnabled(enabled: Boolean, source: String)

    suspend fun isBootProtectionEnabled(): Boolean

    suspend fun setBootProtectionEnabled(enabled: Boolean)
}
