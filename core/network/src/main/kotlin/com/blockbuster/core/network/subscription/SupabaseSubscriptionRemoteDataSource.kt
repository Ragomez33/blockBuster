package com.blockbuster.core.network.subscription

import com.blockbuster.core.common.contract.SubscriptionRemoteDataSource
import com.blockbuster.core.common.model.RemoteSubscription
import com.blockbuster.core.network.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionResponse(
    @SerialName("user_id")
    val userId: String,
    @SerialName("is_premium")
    val isPremium: Boolean = false,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    @SerialName("payment_provider")
    val paymentProvider: String? = null,
)

@Singleton
class SupabaseSubscriptionRemoteDataSource @Inject constructor(
    private val client: SupabaseClient,
) : SubscriptionRemoteDataSource {

    override suspend fun fetchSubscription(userId: String): RemoteSubscription? {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            throw IllegalStateException("Supabase is not configured")
        }

        val response = client.from(TABLE)
            .select {
                filter {
                    eq("user_id", userId)
                }
            }
            .decodeList<SubscriptionResponse>()
            .firstOrNull()
            ?: return null

        return response.toRemoteSubscription()
    }

    private companion object {
        const val TABLE = "subscriptions"
    }
}

internal fun SubscriptionResponse.toRemoteSubscription(): RemoteSubscription {
    val expiresAtEpochMillis = expiresAt?.let { value ->
        runCatching { Instant.parse(value).toEpochMilli() }
            .getOrElse { cause ->
                throw IllegalArgumentException("Invalid expires_at: $value", cause)
            }
    }

    return RemoteSubscription(
        userId = userId,
        isPremium = isPremium,
        expiresAtEpochMillis = expiresAtEpochMillis,
        paymentProvider = paymentProvider,
    )
}
