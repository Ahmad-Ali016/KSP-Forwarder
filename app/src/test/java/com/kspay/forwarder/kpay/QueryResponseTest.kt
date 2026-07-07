package com.kspay.forwarder.kpay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryResponseTest {

    @Test
    fun `toString never exposes the raw cardNo`() {
        val result = QueryResponse(
            outTradeNO = "OT123",
            payResult = 2,
            cardNo = "4111111111111111",
        )

        val logged = result.toString()

        assertFalse(logged.contains("4111111111111111"))
        assertTrue(logged.contains("411111******1111"))
    }
}
