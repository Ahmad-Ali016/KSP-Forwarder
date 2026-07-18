package com.kspay.forwarder.kpay

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KposApiSignInTest {

    private lateinit var server: MockWebServer
    private lateinit var api: KposApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = KposClientFactory.create(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient.Builder().addInterceptor(SignInHeaderInterceptor()).build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sign-in parses keys and applies the escaped-equals fixup`() = runTest {
        // Wire JSON "\\u003d" (escaped backslash + literal u003d) is what KPay's real bug
        // sends — Moshi correctly parses it into the literal 6-char = artifact, which
        // signInWithFixedKeys must then clean up into a real "=".
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "code": 10000,
                  "data": {
                    "platformPublicKey": "platformKeyBody\\u003d",
                    "appPrivateKey": "privateKeyBody\\u003d"
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = api.signInWithFixedKeys(appId = "202xxxxxxxxxx", appSecret = "secret")

        assertTrue(result.isSuccess)
        assertEquals("platformKeyBody=", result.data?.platformPublicKey)
        assertEquals("privateKeyBody=", result.data?.appPrivateKey)
    }

    @Test
    fun `sign-in request is not signed`() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":10000,"data":{"platformPublicKey":"a","appPrivateKey":"b"}}"""))

        api.signInWithFixedKeys(appId = "202xxxxxxxxxx", appSecret = "secret")

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("signature"))
        assertEquals("/v2/pos/sign", recorded.path)
    }

    @Test
    fun `sign-in request carries timestamp and nonceStr headers`() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":10000,"data":{"platformPublicKey":"a","appPrivateKey":"b"}}"""))

        api.signInWithFixedKeys(appId = "202xxxxxxxxxx", appSecret = "secret")

        val recorded = server.takeRequest()
        assertTrue(recorded.getHeader("timestamp")?.toLongOrNull() != null)
        assertEquals(32, recorded.getHeader("nonceStr")?.length)
    }

    @Test
    fun `error response is not marked successful`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"code":40006,"message":"Developer application information does not exist"}"""),
        )

        val result = api.signInWithFixedKeys(appId = "bad", appSecret = "bad")

        assertTrue(!result.isSuccess)
        assertNull(result.data)
        assertEquals("Developer application information does not exist", result.message)
    }
}
