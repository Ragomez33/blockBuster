package com.blockbuster.core.database.support

import com.blockbuster.core.common.contract.SubscriptionRemoteDataSource
import com.blockbuster.core.common.model.RemoteSubscription

class FakeSubscriptionRemoteDataSource : SubscriptionRemoteDataSource {
    var response: RemoteSubscription? = null
    var error: Throwable? = null
    var calls: Int = 0
        private set

    override suspend fun fetchSubscription(userId: String): RemoteSubscription? {
        calls++
        error?.let { throw it }
        return response
    }
}
