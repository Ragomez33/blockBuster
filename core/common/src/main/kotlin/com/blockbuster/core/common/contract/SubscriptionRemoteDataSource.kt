package com.blockbuster.core.common.contract

import com.blockbuster.core.common.model.RemoteSubscription

/**
 * Remote source for subscription licenses. Implementations must throw on
 * network/parse failures so the repository can apply its offline fallback.
 */
interface SubscriptionRemoteDataSource {
    suspend fun fetchSubscription(userId: String): RemoteSubscription?
}
