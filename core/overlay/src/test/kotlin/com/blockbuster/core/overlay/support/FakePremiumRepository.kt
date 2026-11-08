package com.blockbuster.core.overlay.support

import com.blockbuster.core.common.contract.PremiumRepository
import com.blockbuster.core.common.model.IsPremiumState
import com.blockbuster.core.common.model.LicenseSyncResult
import com.blockbuster.core.common.model.PremiumSettings
import com.blockbuster.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePremiumRepository : PremiumRepository {
    val premiumState = MutableStateFlow(IsPremiumState())
    val settings = MutableStateFlow(PremiumSettings())

    override fun observePremiumState(): Flow<IsPremiumState> = premiumState

    override fun observeSettings(): Flow<PremiumSettings> = settings

    override suspend fun checkSubscriptionStatus(): LicenseSyncResult =
        LicenseSyncResult.Updated(premiumState.value)

    override suspend fun verifyPayment(): AppResult<IsPremiumState> =
        AppResult.Success(premiumState.value)

    override suspend fun setPremium(premium: Boolean): AppResult<Unit> {
        premiumState.value = IsPremiumState(premium)
        return AppResult.Success(Unit)
    }

    override suspend fun setStrictShortsReels(enabled: Boolean): AppResult<Unit> {
        settings.value = settings.value.copy(strictShortsReels = enabled)
        return AppResult.Success(Unit)
    }

    override suspend fun setFrictionWindowMillis(value: Long): AppResult<Unit> {
        settings.value = settings.value.copy(frictionWindowMillis = value)
        return AppResult.Success(Unit)
    }

    override suspend fun setAccountabilityPartner(value: String?): AppResult<Unit> {
        settings.value = settings.value.copy(accountabilityPartner = value)
        return AppResult.Success(Unit)
    }
}
