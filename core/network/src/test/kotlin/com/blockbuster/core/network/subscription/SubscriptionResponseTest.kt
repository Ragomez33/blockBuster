package com.blockbuster.core.network.subscription

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionResponseTest {

    @Test
    fun decodesRemoteColumns() {
        val response = Json.decodeFromString<SubscriptionResponse>(
            """
            {
              "user_id": "device-1",
              "is_premium": true,
              "expires_at": "2030-01-01T00:00:00Z",
              "payment_provider": "paypal"
            }
            """.trimIndent(),
        )

        assertEquals("device-1", response.userId)
        assertTrue(response.isPremium)
        assertEquals("paypal", response.paymentProvider)
    }

    @Test
    fun mapsValidExpiryToEpochMillis() {
        val subscription = SubscriptionResponse(
            userId = "device-1",
            isPremium = true,
            expiresAt = "2030-01-01T00:00:00Z",
            paymentProvider = "binance_pay",
        ).toRemoteSubscription()

        assertEquals("device-1", subscription.userId)
        assertTrue(subscription.isPremium)
        assertTrue((subscription.expiresAtEpochMillis ?: 0L) > 0L)
        assertEquals("binance_pay", subscription.paymentProvider)
    }

    @Test
    fun missingExpiryIsNull() {
        val subscription = SubscriptionResponse(
            userId = "device-1",
            isPremium = true,
            expiresAt = null,
            paymentProvider = null,
        ).toRemoteSubscription()

        assertNull(subscription.expiresAtEpochMillis)
        assertNull(subscription.paymentProvider)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidExpiryThrowsControlledException() {
        SubscriptionResponse(
            userId = "device-1",
            isPremium = true,
            expiresAt = "not-a-date",
            paymentProvider = null,
        ).toRemoteSubscription()
    }
}
