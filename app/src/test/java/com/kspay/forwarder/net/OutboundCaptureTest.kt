package com.kspay.forwarder.net

import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionState
import com.kspay.forwarder.kpay.QueryResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Captures one real, production-serialized outbound request body -- the exact bytes ForwardWorker
 * would send over the wire -- so it can be handed to KSPay to eyeball ahead of the real 5f
 * connect. Not a throwaway script: this exercises the actual OutboundTransactionMapper +
 * KspayClientFactory + Moshi/Retrofit pipeline against a MockWebServer stand-in, so the captured
 * JSON is exactly what production code produces, not a hand-typed approximation.
 */
class OutboundCaptureTest {

    private lateinit var server: MockWebServer
    private lateinit var api: KspayApi
    private val queryResponseAdapter =
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(QueryResponse::class.java)

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

    // A realistic KPay query result for a successful card sale -- all the fields KPay's docs say
    // are present when includeReceipt=true, values shaped like real KPay output (not "SIM-..."
    // simulate-mode placeholders).
    private val realisticRawResult = queryResponseAdapter.toJson(
        QueryResponse(
            outTradeNO = "dev01-1751800000000-a1b2c3",
            payResult = 2,
            transactionNo = "T202607060001",
            refNo = "REF20260706000123",
            kpayOutTradeNo = "KPAY20260706000123",
            deviceID = "869123456789012",
            commitTime = 1_751_800_012_000,
            appVersion = "6.10.37",
            terminalType = "KPOS-A1",
            payAmount = "000000012345",
            tipsAmount = "000000000000",
            discountAmount = "000000000000",
            surchargeAmount = "000000000000",
            orderAmount = "000000012345",
            payCurrency = "036",
            transactionType = 1,
            payMethod = 1,
            reason = "",
            cardInputCode = "C",
            cardNo = "556677******1234",
            authCode = "084521",
            batchNo = "000123",
            traceNo = "000456",
            needSignature = false,
        ),
    )

    private fun realisticTransaction() = LocalTransaction(
        id = 1,
        outTradeNo = "dev01-1751800000000-a1b2c3",
        state = TransactionState.SUCCEEDED,
        payAmountCents = "000000012345",
        currency = "036",
        paymentType = 1,
        rawSaleResultJson = realisticRawResult,
        createdAt = 1_751_800_000_000,
        updatedAt = 1_751_800_012_000,
    )

    @Test
    fun `capture the real production-serialized outbound request body for KSPay to review`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val outbound = OutboundTransactionMapper.map(
            realisticTransaction(),
            appId = "202607060000001",
            forwarderVersion = "1.0",
        )

        api.forwardTransaction(outbound, deviceToken = "SAMPLE-DEVICE-TOKEN")

        val recorded = server.takeRequest()
        val outboundAdapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(OutboundTransaction::class.java)
        val wireBody = recorded.body.readUtf8()

        assertEquals("/api/v1/ingest/kpay/transactions/", recorded.path)
        assertTrue(wireBody.contains("\"outTradeNo\""))
        assertTrue(wireBody.contains("\"appId\""))
        assertTrue(wireBody.contains("\"payResult\""))
        assertTrue(wireBody.contains("\"payAmount\""))
        assertTrue(wireBody.contains("\"orderAmount\""))
        // Null-valued fields (e.g. kpayTerminalNo, always null in V1) must be omitted entirely,
        // per KSPay's confirmed preferred wire format -- not sent as a literal `null`.
        assertTrue(!wireBody.contains("\"kpayTerminalNo\""))
        // Round-trip proof that the bytes actually on the wire decode back to the exact object we
        // built -- not just a string-contains sanity check.
        assertEquals(outbound, outboundAdapter.fromJson(wireBody))

        println("----- BEGIN CAPTURED OUTBOUND REQUEST BODY (exact wire bytes, pretty-printed) -----")
        println(outboundAdapter.indent("  ").toJson(outbound))
        println("----- END CAPTURED OUTBOUND REQUEST BODY -----")
    }
}
