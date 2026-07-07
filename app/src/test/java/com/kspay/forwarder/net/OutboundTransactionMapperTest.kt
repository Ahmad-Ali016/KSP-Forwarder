package com.kspay.forwarder.net

import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutboundTransactionMapperTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val sampleRawResult = """
        {"outTradeNO":"OT123","payResult":2,"transactionNo":"TXN1","refNo":"REF1",
         "payAmount":"000000010000","tipsAmount":"000000000000","discountAmount":"000000000000",
         "surchargeAmount":"000000000500","orderAmount":"000000010500","payCurrency":"036",
         "transactionType":1,"payMethod":1,"reason":"","cardInputCode":"C","cardNo":"1234",
         "authCode":"AUTH1","batchNo":"000001","traceNo":"000001","needSignature":true,
         "kpayOutTradeNo":"KP123","deviceID":"DEV123456789","commitTime":1700000000000,
         "appVersion":"6.10.37","terminalType":"KPOS-A1"}
    """.trimIndent()

    private fun sampleTransaction() = LocalTransaction(
        id = 42,
        outTradeNo = "OT123",
        state = TransactionState.SUCCEEDED,
        payAmountCents = "000000010000",
        currency = "036",
        paymentType = 1,
        rawSaleResultJson = sampleRawResult,
        createdAt = 1_699_999_000_000,
        updatedAt = 1_700_000_005_000,
    )

    @Test
    fun `maps every field of the frozen outbound contract from a stored KPay result`() {
        val outbound = OutboundTransactionMapper.map(sampleTransaction(), appId = "202xxxxxxxxxx", forwarderVersion = "1.0")

        assertEquals("OT123", outbound.outTradeNo)
        assertEquals("KP123", outbound.kpayOutTradeNo)
        assertEquals("TXN1", outbound.transactionNo)
        assertEquals("REF1", outbound.refNo)
        assertEquals("DEV123456789", outbound.deviceID)
        assertNull(outbound.kpayTerminalNo)
        assertEquals("202xxxxxxxxxx", outbound.appId)
        assertEquals("KPOS-A1", outbound.terminalType)
        assertEquals("6.10.37", outbound.appVersion)
        assertEquals(1_700_000_000_000L, outbound.commitTime)
        assertEquals("000000010000", outbound.payAmount)
        assertEquals("000000000000", outbound.tipsAmount)
        assertEquals("000000000000", outbound.discountAmount)
        assertEquals("000000000500", outbound.surchargeAmount)
        assertEquals("000000010500", outbound.orderAmount)
        assertEquals("036", outbound.payCurrency)
        assertEquals(2, outbound.payResult)
        assertEquals(1, outbound.transactionType)
        assertEquals(1, outbound.payMethod)
        assertEquals("", outbound.reason)
        assertEquals("C", outbound.cardInputCode)
        assertEquals("1234", outbound.cardNo)
        assertEquals("AUTH1", outbound.authCode)
        assertEquals("000001", outbound.batchNo)
        assertEquals("000001", outbound.traceNo)
        assertEquals(true, outbound.needSignature)
        assertEquals("1.0", outbound.forwarderVersion)
        assertEquals("poll", outbound.captureMethod)
        assertEquals(1_700_000_005_000L, outbound.capturedAt)
        assertEquals("42", outbound.localId)
    }

    @Test
    fun `serializes and parses back to an identical value (round-trip fidelity)`() {
        val outbound = OutboundTransactionMapper.map(sampleTransaction(), appId = "202xxxxxxxxxx", forwarderVersion = "1.0")
        val adapter = moshi.adapter(OutboundTransaction::class.java)

        val parsedBack = adapter.fromJson(adapter.toJson(outbound))

        assertEquals(outbound, parsedBack)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mapping a transaction with no stored result fails loudly`() {
        OutboundTransactionMapper.map(sampleTransaction().copy(rawSaleResultJson = null), appId = "x", forwarderVersion = "1.0")
    }

    @Test
    fun `toString never exposes the raw cardNo`() {
        val raw = sampleRawResult.replace("\"cardNo\":\"1234\"", "\"cardNo\":\"4111111111111111\"")
        val outbound = OutboundTransactionMapper.map(
            sampleTransaction().copy(rawSaleResultJson = raw),
            appId = "202xxxxxxxxxx",
            forwarderVersion = "1.0",
        )

        val logged = outbound.toString()

        assertEquals(false, logged.contains("4111111111111111"))
        assertEquals(true, logged.contains("411111******1111"))
    }
}
