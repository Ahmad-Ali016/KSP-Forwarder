package com.kspay.forwarder.net

import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionState
import com.kspay.forwarder.kpay.QueryResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * One-off live smoke test against KSPay's real backend, over the test ngrok tunnel KSPay provided
 * (2026-07-06), to close out Phase 5f. Drives the real OutboundTransactionMapper -> KspayClientFactory
 * pipeline, but against KSPay's actual server instead of MockWebServer -- a genuine network call.
 *
 * Already run once for real (2026-07-06): HTTP 200, {"success":true,"data":{"status":"PROCESSED"}}.
 *
 * @Ignore'd by default: the tunnel URL and device token are temporary test credentials, so this
 * can't be part of the regular offline suite (it would fail for everyone once the tunnel closes).
 * Re-enable by removing @Ignore only when deliberately re-running against a live KSPay endpoint.
 */
@Ignore("Live network call to a temporary KSPay ngrok tunnel -- run manually only, not part of CI/offline suite")
class KspayRealBackendSmokeTest {

    private val queryResponseAdapter =
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(QueryResponse::class.java)

    @Test
    fun `forward a realistic transaction to KSPay's real backend over the ngrok tunnel`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .build()
                chain.proceed(request)
            }
            .build()
        val api = KspayClientFactory.create(
            baseUrl = "https://irrelievable-driftless-kacie.ngrok-free.dev/",
            client = client,
        )

        val outTradeNo = "smoke5f-${System.currentTimeMillis()}"
        val rawResult = queryResponseAdapter.toJson(
            QueryResponse(
                outTradeNO = outTradeNo,
                payResult = 2,
                transactionNo = "T-SMOKE-0001",
                refNo = "REF-SMOKE-0001",
                kpayOutTradeNo = "KPAY-SMOKE-0001",
                deviceID = "D123456789AB",
                commitTime = System.currentTimeMillis(),
                appVersion = "1.0",
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
        val transaction = LocalTransaction(
            id = 1,
            outTradeNo = outTradeNo,
            state = TransactionState.SUCCEEDED,
            payAmountCents = "000000012345",
            currency = "036",
            paymentType = 1,
            rawSaleResultJson = rawResult,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val outbound = OutboundTransactionMapper.map(transaction, appId = "smoke-test-app-id", forwarderVersion = "1.0")

        val response = api.forwardTransaction(outbound, deviceToken = "9fT06Qcx-bVgXHRCoWaONFeS7kLE4UthFQZ1ytHHO4Y")

        println("----- KSPay real backend response -----")
        println("HTTP ${response.code()}")
        println(response.body()?.string() ?: response.errorBody()?.string() ?: "(no body)")
        println("----------------------------------------")

        assertTrue("Expected a 2xx from KSPay's real backend, got ${response.code()}", response.isSuccessful)
    }
}
