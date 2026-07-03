package com.kspay.forwarder.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KspayApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: KspayApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = KspayClientFactory.create(baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sampleTransaction() = OutboundTransaction(
        outTradeNo = "OT123",
        kpayOutTradeNo = "KP123",
        transactionNo = null,
        refNo = null,
        deviceID = "DEV123456789",
        kpayTerminalNo = null,
        appId = "202xxxxxxxxxx",
        terminalType = null,
        appVersion = null,
        commitTime = 1_700_000_000_000,
        payAmount = "000000010000",
        tipsAmount = "000000000000",
        discountAmount = "000000000000",
        surchargeAmount = "000000000500",
        orderAmount = "000000010500",
        payCurrency = "036",
        payResult = 2,
        transactionType = 1,
        payMethod = 1,
        reason = null,
        cardInputCode = null,
        cardNo = null,
        authCode = null,
        batchNo = null,
        traceNo = null,
        needSignature = null,
        forwarderVersion = "1.0",
        capturedAt = 1_700_000_005_000,
        localId = "42",
    )

    @Test
    fun `forward posts to the ingest path with X-Device-Token and Idempotency-Key set to outTradeNo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val response = api.forwardTransaction(sampleTransaction(), deviceToken = "device-token-abc")

        assertTrue(response.isSuccessful)
        val recorded = server.takeRequest()
        assertEquals("/api/v1/ingest/kpay/transactions/", recorded.path)
        assertEquals("device-token-abc", recorded.getHeader("X-Device-Token"))
        assertEquals("OT123", recorded.getHeader("Idempotency-Key"))
        assertTrue(recorded.body.readUtf8().contains("\"outTradeNo\":\"OT123\""))
    }

    @Test
    fun `a non-2xx response is reported as unsuccessful`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val response = api.forwardTransaction(sampleTransaction(), deviceToken = "device-token-abc")

        assertTrue(!response.isSuccessful)
    }
}
