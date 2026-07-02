package com.kspay.forwarder.kpay

import com.kspay.forwarder.crypto.CanonicalString
import com.kspay.forwarder.crypto.Nonce
import com.kspay.forwarder.crypto.RsaSigner
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

private const val WORKING_KEY_MISSING_CODE = 40004

private data class ResponseCode(val code: Int)

/**
 * Signs every request from its method+uri+body per KPay's canonical-string spec. KPOS always
 * responds HTTP 200 with an application-level {code, ...} envelope, so a working-key-expired
 * condition (40004) has to be read out of the body, not the HTTP status.
 *
 * Only attach this to the client used for signed endpoints (sales/query/close/etc). The
 * sign-in call itself (/v2/pos/sign) uses its own unsigned client — there is no working key
 * yet to sign with at that point.
 */
class SignedRequestInterceptor(
    private val appId: String,
    private val appSecret: String,
    private val workingKeyStore: WorkingKeyStore,
    private val signIn: suspend (appId: String, appSecret: String) -> KposEnvelope<SignInResponse>,
) : Interceptor {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val response = chain.proceed(sign(original))

        val bodyString = response.body?.string().orEmpty()
        val code = runCatching { moshi.adapter(ResponseCode::class.java).fromJson(bodyString)?.code }.getOrNull()

        if (code == WORKING_KEY_MISSING_CODE) {
            response.close()
            workingKeyStore.clear()
            val reSignIn = runBlocking { signIn(appId, appSecret) }
            reSignIn.data?.let { workingKeyStore.save(it) }
            return chain.proceed(sign(original))
        }

        return response.newBuilder()
            .body(bodyString.toResponseBody(response.body?.contentType()))
            .build()
    }

    private fun sign(request: Request): Request {
        val method = request.method
        val query = request.url.encodedQuery
        val uri = if (query != null) "${request.url.encodedPath}?$query" else request.url.encodedPath
        val timestamp = System.currentTimeMillis().toString()
        val nonce = Nonce.generate()
        val body = request.body?.let { requestBody ->
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            buffer.readUtf8()
        }

        val canonicalString = CanonicalString.build(method, uri, timestamp, nonce, body)
        val appPrivateKey = workingKeyStore.get()?.appPrivateKey
            ?: error("No working key available — sign in before making signed requests")
        val signature = RsaSigner.sign(canonicalString, appPrivateKey)

        return request.newBuilder()
            .header("timestamp", timestamp)
            .header("nonceStr", nonce)
            .header("appId", appId)
            .header("signature", signature)
            .build()
    }
}
