package com.blockbuster.app.ui.premium

import com.blockbuster.app.BuildConfig
import com.blockbuster.core.common.model.PaymentGateway

object PaymentGatewayLinks {

    fun urlFor(
        gateway: PaymentGateway,
        binancePayUrl: String = BuildConfig.BINANCE_PAY_URL,
        paypalUrl: String = BuildConfig.PAYPAL_URL,
    ): String? = when (gateway) {
        PaymentGateway.BINANCE_PAY -> binancePayUrl
        PaymentGateway.PAYPAL -> paypalUrl
    }.trim().takeIf { value -> value.isNotEmpty() }
}
