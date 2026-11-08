package com.blockbuster.core.database.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.blockbuster.core.common.contract.PremiumRepository
import com.blockbuster.core.common.contract.SubscriptionRemoteDataSource
import com.blockbuster.core.common.model.IsPremiumState
import com.blockbuster.core.common.model.LicenseSyncResult
import com.blockbuster.core.common.model.PremiumSettings
import com.blockbuster.core.common.model.RemoteSubscription
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
import com.blockbuster.core.database.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal object PremiumPreferenceKeys {
    val IS_PREMIUM = booleanPreferencesKey("is_premium")
    val STRICT_SHORTS_REELS = booleanPreferencesKey("premium_strict_shorts_reels")
    val FRICTION_WINDOW_MILLIS = longPreferencesKey("premium_friction_window_millis")
    val ACCOUNTABILITY_PARTNER = stringPreferencesKey("premium_accountability_partner")
}

@Singleton
class PremiumRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val remoteDataSource: SubscriptionRemoteDataSource,
    private val identityStore: LicenseIdentityStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PremiumRepository {

    private val syncMutex = Mutex()

    override fun observePremiumState(): Flow<IsPremiumState> =
        dataStore.data
            .map { preferences -> readLicense(preferences) }
            .distinctUntilChanged()

    override fun observeSettings(): Flow<PremiumSettings> =
        dataStore.data
            .map { preferences ->
                PremiumSettings(
                    strictShortsReels =
                        preferences[PremiumPreferenceKeys.STRICT_SHORTS_REELS] ?: false,
                    frictionWindowMillis = PremiumSettings.sanitizeWindow(
                        preferences[PremiumPreferenceKeys.FRICTION_WINDOW_MILLIS]
                            ?: PremiumSettings.DEFAULT_FRICTION_WINDOW_MILLIS,
                    ),
                    accountabilityPartner = PremiumSettings.sanitizePartner(
                        preferences[PremiumPreferenceKeys.ACCOUNTABILITY_PARTNER],
                    ),
                )
            }
            .distinctUntilChanged()

    override suspend fun checkSubscriptionStatus(): LicenseSyncResult =
        withContext(ioDispatcher) {
            syncMutex.withLock {
                val now = System.currentTimeMillis()
                val lastSync = dataStore.data.first()[LicensePreferenceKeys.LAST_SYNC_AT] ?: 0L
                if (now - lastSync < AUTOMATIC_SYNC_INTERVAL_MILLIS) {
                    return@withLock LicenseSyncResult.Offline(currentLicense(now))
                }

                val userId = runCatching { identityStore.getOrCreateUserId() }.getOrNull()
                    ?: return@withLock LicenseSyncResult.NoIdentity

                when (val fetch = fetchRemote(userId)) {
                    is RemoteFetch.Success -> {
                        val license = applyRemote(fetch.subscription, now)
                        LicenseSyncResult.Updated(license)
                    }

                    RemoteFetch.Invalid -> {
                        markSyncAttempt(now)
                        LicenseSyncResult.Invalid(currentLicense(now))
                    }

                    RemoteFetch.Offline -> {
                        markSyncAttempt(now)
                        LicenseSyncResult.Offline(currentLicense(now))
                    }
                }
            }
        }

    override suspend fun verifyPayment(): AppResult<IsPremiumState> =
        withContext(ioDispatcher) {
            syncMutex.withLock {
                val userId = runCatching { identityStore.getOrCreateUserId() }.getOrNull()
                    ?: return@withLock AppResult.Failure(
                        AppError.Permission("Missing license identity"),
                    )

                when (val fetch = fetchRemote(userId)) {
                    is RemoteFetch.Success -> AppResult.Success(
                        applyRemote(fetch.subscription, System.currentTimeMillis()),
                    )

                    RemoteFetch.Invalid -> AppResult.Failure(
                        AppError.Unexpected("Invalid subscription response"),
                    )

                    RemoteFetch.Offline -> AppResult.Failure(
                        AppError.Storage("Subscription service unavailable"),
                    )
                }
            }
        }

    override suspend fun setPremium(premium: Boolean): AppResult<Unit> = writeResult {
        dataStore.edit { preferences ->
            preferences[PremiumPreferenceKeys.IS_PREMIUM] = premium
            if (!premium) {
                preferences.remove(LicensePreferenceKeys.EXPIRES_AT)
                preferences.remove(LicensePreferenceKeys.PAYMENT_PROVIDER)
            }
        }
    }

    override suspend fun setStrictShortsReels(enabled: Boolean): AppResult<Unit> =
        premiumWriteResult {
            dataStore.edit { preferences ->
                preferences[PremiumPreferenceKeys.STRICT_SHORTS_REELS] = enabled
            }
        }

    override suspend fun setFrictionWindowMillis(value: Long): AppResult<Unit> =
        premiumWriteResult {
            dataStore.edit { preferences ->
                preferences[PremiumPreferenceKeys.FRICTION_WINDOW_MILLIS] =
                    PremiumSettings.sanitizeWindow(value)
            }
        }

    override suspend fun setAccountabilityPartner(value: String?): AppResult<Unit> =
        premiumWriteResult {
            dataStore.edit { preferences ->
                val sanitized = PremiumSettings.sanitizePartner(value)
                if (sanitized == null) {
                    preferences.remove(PremiumPreferenceKeys.ACCOUNTABILITY_PARTNER)
                } else {
                    preferences[PremiumPreferenceKeys.ACCOUNTABILITY_PARTNER] = sanitized
                }
            }
        }

    private suspend fun isPremium(): Boolean = currentLicense().isActive()

    private suspend fun premiumWriteResult(block: suspend () -> Unit): AppResult<Unit> {
        if (!isPremium()) {
            return AppResult.Failure(
                AppError.Permission("Premium subscription required"),
            )
        }
        return writeResult(block)
    }

    private suspend fun fetchRemote(userId: String): RemoteFetch =
        try {
            RemoteFetch.Success(
                withTimeout(REMOTE_TIMEOUT_MILLIS) {
                    remoteDataSource.fetchSubscription(userId)
                },
            )
        } catch (timeout: TimeoutCancellationException) {
            RemoteFetch.Offline
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (invalid: IllegalArgumentException) {
            RemoteFetch.Invalid
        } catch (throwable: Throwable) {
            RemoteFetch.Offline
        }

    private suspend fun markSyncAttempt(now: Long) {
        dataStore.edit { preferences ->
            preferences[LicensePreferenceKeys.LAST_SYNC_AT] = now
        }
    }

    private suspend fun applyRemote(
        subscription: RemoteSubscription?,
        now: Long,
    ): IsPremiumState {
        val license = if (subscription == null) {
            IsPremiumState(isPremium = false)
        } else {
            IsPremiumState(
                isPremium = subscription.isPremium,
                expiresAtEpochMillis = subscription.expiresAtEpochMillis,
                paymentProvider = subscription.paymentProvider,
            )
        }
        val expiresAt = subscription?.expiresAtEpochMillis
        val provider = subscription?.paymentProvider
        dataStore.edit { preferences ->
            preferences[PremiumPreferenceKeys.IS_PREMIUM] = subscription?.isPremium ?: false
            if (expiresAt == null) {
                preferences.remove(LicensePreferenceKeys.EXPIRES_AT)
            } else {
                preferences[LicensePreferenceKeys.EXPIRES_AT] = expiresAt
            }
            if (provider == null) {
                preferences.remove(LicensePreferenceKeys.PAYMENT_PROVIDER)
            } else {
                preferences[LicensePreferenceKeys.PAYMENT_PROVIDER] = provider
            }
            preferences[LicensePreferenceKeys.LAST_SYNC_AT] = now
        }
        return readLicense(dataStore.data.first())
    }

    private suspend fun currentLicense(now: Long = System.currentTimeMillis()): IsPremiumState =
        readLicense(dataStore.data.first(), now)

    private fun readLicense(
        preferences: Preferences,
        now: Long = System.currentTimeMillis(),
    ): IsPremiumState {
        val stored = IsPremiumState(
            isPremium = preferences[PremiumPreferenceKeys.IS_PREMIUM] ?: false,
            expiresAtEpochMillis = preferences[LicensePreferenceKeys.EXPIRES_AT],
            paymentProvider = preferences[LicensePreferenceKeys.PAYMENT_PROVIDER],
        )
        return stored.copy(isPremium = stored.isActive(now))
    }

    private suspend fun writeResult(block: suspend () -> Unit): AppResult<Unit> =
        try {
            block()
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppResult.Failure(
                AppError.Storage(
                    message = throwable.message ?: "Premium storage failure",
                    cause = throwable,
                ),
            )
        }

    private sealed interface RemoteFetch {
        data class Success(val subscription: RemoteSubscription?) : RemoteFetch

        data object Offline : RemoteFetch

        data object Invalid : RemoteFetch
    }

    private companion object {
        const val AUTOMATIC_SYNC_INTERVAL_MILLIS = 30_000L
        const val REMOTE_TIMEOUT_MILLIS = 10_000L
    }
}
