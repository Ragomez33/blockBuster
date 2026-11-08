package com.blockbuster.core.database.datastore

import com.blockbuster.core.common.model.IsPremiumState
import com.blockbuster.core.common.model.LicenseSyncResult
import com.blockbuster.core.common.model.RemoteSubscription
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
import com.blockbuster.core.database.RepositoryTestFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PremiumRepositoryTest {

    private lateinit var fixture: RepositoryTestFixture

    @Before
    fun setUp() {
        fixture = RepositoryTestFixture()
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun defaultsToFreeWithDefaultSettings() = runTest {
        assertFalse(fixture.premiumRepository.observePremiumState().first().isPremium)
        assertEquals(
            60_000L,
            fixture.premiumRepository.observeSettings().first().frictionWindowMillis,
        )
    }

    @Test
    fun premiumPersists() = runTest {
        fixture.premiumRepository.setPremium(true)

        assertTrue(fixture.premiumRepository.observePremiumState().first().isPremium)
    }

    @Test
    fun advancedSettingsRequirePremium() = runTest {
        val result = fixture.premiumRepository.setStrictShortsReels(true)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Permission)
        assertFalse(fixture.premiumRepository.observeSettings().first().strictShortsReels)
    }

    @Test
    fun advancedSettingsPersistWhenPremium() = runTest {
        fixture.premiumRepository.setPremium(true)

        fixture.premiumRepository.setStrictShortsReels(true)
        fixture.premiumRepository.setFrictionWindowMillis(30_000L)
        fixture.premiumRepository.setAccountabilityPartner("  Ada  ")

        val settings = fixture.premiumRepository.observeSettings().first()
        assertTrue(settings.strictShortsReels)
        assertEquals(30_000L, settings.frictionWindowMillis)
        assertEquals("Ada", settings.accountabilityPartner)
    }

    @Test
    fun frictionWindowIsSanitized() = runTest {
        fixture.premiumRepository.setPremium(true)

        fixture.premiumRepository.setFrictionWindowMillis(999_000L)

        assertEquals(
            180_000L,
            fixture.premiumRepository.observeSettings().first().frictionWindowMillis,
        )
    }

    @Test
    fun remotePremiumUpdatesLocalCache() = runTest {
        fixture.remoteDataSource.response = RemoteSubscription(
            userId = "device",
            isPremium = true,
            expiresAtEpochMillis = System.currentTimeMillis() + 86_400_000L,
            paymentProvider = "paypal",
        )

        val result = fixture.premiumRepository.checkSubscriptionStatus()

        assertTrue(result is LicenseSyncResult.Updated)
        val license = fixture.premiumRepository.observePremiumState().first()
        assertTrue(license.isPremium)
        assertEquals("paypal", license.paymentProvider)
        assertTrue((license.expiresAtEpochMillis ?: 0L) > System.currentTimeMillis())
    }

    @Test
    fun remoteFailurePreservesCachedLicense() = runTest {
        fixture.premiumRepository.setPremium(true)
        fixture.remoteDataSource.error = IllegalStateException("network down")

        val result = fixture.premiumRepository.checkSubscriptionStatus()

        assertTrue(result is LicenseSyncResult.Offline)
        assertTrue(fixture.premiumRepository.observePremiumState().first().isPremium)
    }

    @Test
    fun expiredRemoteLicenseBecomesFree() = runTest {
        fixture.remoteDataSource.response = RemoteSubscription(
            userId = "device",
            isPremium = true,
            expiresAtEpochMillis = System.currentTimeMillis() - 1_000L,
            paymentProvider = "binance_pay",
        )

        fixture.premiumRepository.checkSubscriptionStatus()

        assertFalse(fixture.premiumRepository.observePremiumState().first().isPremium)
    }

    @Test
    fun invalidRemoteResponsePreservesCachedLicense() = runTest {
        fixture.premiumRepository.setPremium(true)
        fixture.remoteDataSource.error = IllegalArgumentException("invalid expires_at")

        val result = fixture.premiumRepository.checkSubscriptionStatus()

        assertTrue(result is LicenseSyncResult.Invalid)
        assertTrue(fixture.premiumRepository.observePremiumState().first().isPremium)
    }

    @Test
    fun verifyPaymentConfirmsPremium() = runTest {
        fixture.remoteDataSource.response = RemoteSubscription(
            userId = "device",
            isPremium = true,
            expiresAtEpochMillis = null,
            paymentProvider = "binance_pay",
        )

        val result = fixture.premiumRepository.verifyPayment()

        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).value.isPremium)
        assertTrue(fixture.premiumRepository.observePremiumState().first().isPremium)
    }

    @Test
    fun verifyPaymentFailureKeepsCachedLicense() = runTest {
        fixture.premiumRepository.setPremium(true)
        fixture.remoteDataSource.error = IllegalStateException("offline")

        val result = fixture.premiumRepository.verifyPayment()

        assertTrue(result is AppResult.Failure)
        assertTrue(fixture.premiumRepository.observePremiumState().first().isPremium)
    }

    @Test
    fun automaticSyncIsThrottledButVerifyPaymentBypassesIt() = runTest {
        fixture.remoteDataSource.response = RemoteSubscription(
            userId = "device",
            isPremium = false,
            expiresAtEpochMillis = null,
            paymentProvider = null,
        )

        fixture.premiumRepository.checkSubscriptionStatus()
        fixture.premiumRepository.checkSubscriptionStatus()
        assertEquals(1, fixture.remoteDataSource.calls)

        fixture.premiumRepository.verifyPayment()
        assertEquals(2, fixture.remoteDataSource.calls)
    }

    @Test
    fun identityIsStableAcrossCalls() = runTest {
        val first = fixture.premiumRepository.verifyPayment()
        val second = fixture.premiumRepository.verifyPayment()

        assertTrue(first is AppResult.Success)
        assertTrue(second is AppResult.Success)
        assertTrue(fixture.remoteDataSource.calls >= 2)
    }
}
