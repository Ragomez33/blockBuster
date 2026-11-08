package com.blockbuster.app.ui.premium

import com.blockbuster.core.common.model.PaymentGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentGatewayLinksTest {

    @Test
    fun returnsBinancePayUrl() {
        assertEquals(
            "https://pay.binance.test/checkout",
            PaymentGatewayLinks.urlFor(
                gateway = PaymentGateway.BINANCE_PAY,
                binancePayUrl = "https://pay.binance.test/checkout",
                paypalUrl = "https://paypal.test/checkout",
            ),
        )
    }

    @Test
    fun returnsPaypalUrl() {
        assertEquals(
            "https://paypal.test/checkout",
            PaymentGatewayLinks.urlFor(
                gateway = PaymentGateway.PAYPAL,
                binancePayUrl = "https://pay.binance.test/checkout",
                paypalUrl = "https://paypal.test/checkout",
            ),
        )
    }

    @Test
    fun blankUrlIsUnavailable() {
        assertNull(
            PaymentGatewayLinks.urlFor(
                gateway = PaymentGateway.PAYPAL,
                binancePayUrl = "",
                paypalUrl = "   ",
            ),
        )
    }
}
