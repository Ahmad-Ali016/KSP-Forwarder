package com.kspay.forwarder.kpay

import com.kspay.forwarder.crypto.CanonicalString
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

private class InMemoryWorkingKeyStore(initial: SignInResponse? = null) : WorkingKeyStore {
    private var current = initial
    override fun save(keys: SignInResponse) { current = keys }
    override fun get(): SignInResponse? = current
    override fun clear() { current = null }
}

class SignedRequestInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun generateKeyPair(): Pair<String, java.security.PublicKey> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val base64PrivateKey = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        return base64PrivateKey to keyPair.public
    }

    @Test
    fun `signs a request with correct headers and a verifiable signature`() = runTest {
        val (privateKey, publicKey) = generateKeyPair()
        val store = InMemoryWorkingKeyStore(SignInResponse(platformPublicKey = "pub", appPrivateKey = privateKey))
        val interceptor = SignedRequestInterceptor(
            appId = "202xxxxxxxxxx",
            appSecret = "secret",
            workingKeyStore = store,
            signIn = { _, _ -> error("should not be called") },
        )
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        server.enqueue(MockResponse().setBody("""{"code":10000,"data":{}}"""))

        val requestBody = """{"outTradeNo":"20xxxxxxxxxxxx1"}"""
        val request = Request.Builder()
            .url(server.url("/v2/pos/sales"))
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            assertTrue(response.isSuccessful)
        }

        val recorded = server.takeRequest()
        val timestamp = recorded.getHeader("timestamp")!!
        val nonce = recorded.getHeader("nonceStr")!!
        val signature = recorded.getHeader("signature")!!
        assertEquals("202xxxxxxxxxx", recorded.getHeader("appId"))

        val canonicalString = CanonicalString.build("POST", "/v2/pos/sales", timestamp, nonce, requestBody)
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update(canonicalString.toByteArray(Charsets.UTF_8))
        assertTrue(verifier.verify(Base64.getDecoder().decode(signature)))
    }

    @Test
    fun `on 40004 it re-signs in once and retries the original request`() = runTest {
        val (staleKey, _) = generateKeyPair()
        val (freshKey, _) = generateKeyPair()
        val store = InMemoryWorkingKeyStore(SignInResponse(platformPublicKey = "stalePub", appPrivateKey = staleKey))
        var signInCallCount = 0
        val interceptor = SignedRequestInterceptor(
            appId = "202xxxxxxxxxx",
            appSecret = "secret",
            workingKeyStore = store,
            signIn = { _, _ ->
                signInCallCount++
                KposEnvelope(code = 10000, data = SignInResponse("freshPub", freshKey), message = null)
            },
        )
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        server.enqueue(MockResponse().setBody("""{"code":40004,"message":"Working key does not exist, please sign in again"}"""))
        server.enqueue(MockResponse().setBody("""{"code":10000,"data":{}}"""))

        val request = Request.Builder().url(server.url("/v2/pos/query?outTradeNo=abc")).get().build()

        val finalBody = client.newCall(request).execute().use { it.body?.string() }

        assertEquals(1, signInCallCount)
        assertEquals(freshKey, store.get()?.appPrivateKey)
        assertEquals("""{"code":10000,"data":{}}""", finalBody)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a normal success response body is preserved intact for the caller`() = runTest {
        val (privateKey, _) = generateKeyPair()
        val store = InMemoryWorkingKeyStore(SignInResponse(platformPublicKey = "pub", appPrivateKey = privateKey))
        val interceptor = SignedRequestInterceptor(
            appId = "202xxxxxxxxxx",
            appSecret = "secret",
            workingKeyStore = store,
            signIn = { _, _ -> error("should not be called") },
        )
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val expectedBody = """{"code":10000,"data":{"outTradeNO":"20xxxxxxxxxxxxx2","payResult":2}}"""
        server.enqueue(MockResponse().setBody(expectedBody))

        val request = Request.Builder().url(server.url("/v2/pos/query?outTradeNo=abc")).get().build()
        val actualBody = client.newCall(request).execute().use { it.body?.string() }

        assertEquals(expectedBody, actualBody)
    }
}
