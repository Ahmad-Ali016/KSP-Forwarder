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
 * Verifies the raw /v2/pos/sales/close request shape and signing -- SaleController's abort()
 * state-machine behavior (success -> ABORTED, rejection -> lastError) is covered separately in
 * SaleControllerTest.
 */
class KposApiCloseTest {

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
    fun `close request carries outTradeNo and is signed`() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":10000,"data":{}}"""))

        val result = api.close(CloseRequest(outTradeNo = "OT-123"))

        assertTrue(result.isSuccess)
        val recorded = server.takeRequest()
        assertEquals("/v2/pos/sales/close", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"outTradeNo\":\"OT-123\""))
        assertNotNull(recorded.getHeader("signature"))
        assertNotNull(recorded.getHeader("timestamp"))
        assertNotNull(recorded.getHeader("nonceStr"))
    }

    @Test
    fun `close rejection is not marked successful`() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":20010,"data":null,"message":"Transaction completed, cannot be closed"}"""))

        val result = api.close(CloseRequest(outTradeNo = "OT-123"))

        assertTrue(!result.isSuccess)
        assertEquals("Transaction completed, cannot be closed", result.message)
    }
}
