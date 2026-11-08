package com.blockbuster.core.common.contract

import com.blockbuster.core.common.model.IsPremiumState
import com.blockbuster.core.common.model.LicenseSyncResult
import com.blockbuster.core.common.model.PremiumSettings
import com.blockbuster.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

interface PremiumRepository {
    fun observePremiumState(): Flow<IsPremiumState>

    fun observeSettings(): Flow<PremiumSettings>

    suspend fun checkSubscriptionStatus(): LicenseSyncResult

    suspend fun verifyPayment(): AppResult<IsPremiumState>

    suspend fun setPremium(premium: Boolean): AppResult<Unit>

    suspend fun setStrictShortsReels(enabled: Boolean): AppResult<Unit>

    suspend fun setFrictionWindowMillis(value: Long): AppResult<Unit>

    suspend fun setAccountabilityPartner(value: String?): AppResult<Unit>
}
