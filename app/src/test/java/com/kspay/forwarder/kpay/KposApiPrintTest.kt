package com.kspay.forwarder.kpay

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * Verifies the raw /v2/pos/print and /v2/pos/query/settlement request/response shapes --
 * SaleController's printReceipt() orchestration (TID caching, lastError on failure) is covered
 * separately in SaleControllerTest.
 */
class KposApiPrintTest {

    private lateinit var server: MockWebServer
    private lateinit var api: KposApi

    private fun generatePrivateKeyBase64() =
        Base64.getEncoder().encodeToString(
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private.encoded,
        )

    private val store = object : WorkingKeyStore {
        private var current: SignInResponse? = SignInResponse("platformKey", generatePrivateKeyBase64())
        override fun save(keys: SignInResponse) { current = keys }
        override fun get(): SignInResponse? = current
        override fun clear() { current = null }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val unsignedApi = KposClientFactory.create(baseUrl = server.url("/").toString())
        api = KposClientFactory.create(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient.Builder()
                .addInterceptor(
                    SignedRequestInterceptor(
                        appId = "test-app-id",
                        appSecret = "test-app-secret",
                        workingKeyStore = store,
                        signIn = unsignedApi::signInWithFixedKeys,
                    ),
                )
                .build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `print request carries the step list and is signed`() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":10000,"data":{}}"""))

        val result = api.print(PrintRequest(listOf(PrintStep.text("KSPay", size = "L"), PrintStep.lrText("BASE", "AUD 1.00"))))

        assertTrue(result.isSuccess)
        val recorded = server.takeRequest()
        assertEquals("/v2/pos/print", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"textContent\":\"KSPay\""))
        assertTrue(body.contains("\"leftTextContent\":\"BASE\""))
        // KPOS's server crashes with an NPE (confirmed live, 2026-07-20) if any step is missing
        // "alignment" -- every step, including LR_TEXT, must always carry an explicit value.
        assertEquals(2, "\"alignment\":\"LEFT\"".toRegex().findAll(body).count())
        assertNotNull(recorded.getHeader("signature"))
        assertNotNull(recorded.getHeader("timestamp"))
        assertNotNull(recorded.getHeader("nonceStr"))
    }

    @Test
    fun `print request serializes a barcode step with an explicit alignment`() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":10000,"data":{}}"""))

        val result = api.print(PrintRequest(listOf(PrintStep.barcode("260611000662"))))

        assertTrue(result.isSuccess)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"printType\":\"BAR_CODE\""))
        assertTrue(body.contains("\"barcodeContent\":\"260611000662\""))
        assertTrue(body.contains("\"alignment\":\"LEFT\""))
    }

    @Test
    fun `print rejection is not marked successful`() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":50003,"data":null,"message":"Printer status abnormal"}"""))

        val result = api.print(PrintRequest(listOf(PrintStep.text("KSPay"))))

        assertTrue(!result.isSuccess)
        assertEquals("Printer status abnormal", result.message)
    }

    @Test
    fun `querySettlement is signed and parses the nested extra kpayTerminalNo`() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":10000,"extra":{"kpayTerminalNo":"00000917"},"message":null}"""))

        val result = api.querySettlement()

        assertTrue(result.isSuccess)
        assertEquals("00000917", result.extra?.kpayTerminalNo)
        val recorded = server.takeRequest()
        assertEquals("/v2/pos/query/settlement?previousBatch=false", recorded.path)
        assertNotNull(recorded.getHeader("signature"))
    }
}
