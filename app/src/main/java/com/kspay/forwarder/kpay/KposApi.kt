package com.kspay.forwarder.kpay

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * KPOS LAN client. Base URL is the terminal's local API — http://127.0.0.1:18080 for the
 * real 2-in-1 background mode integration, injectable for testing.
 */
interface KposApi {

    /**
     * Sign in and obtain the working key pair. Deliberately unsigned — the app has no
     * working key yet, so there is nothing to sign the request with.
     */
    @POST("/v2/pos/sign")
    suspend fun signIn(@Body request: SignInRequest): KposEnvelope<SignInResponse>

    /** Must be called on a client with SignedRequestInterceptor attached. */
    @POST("/v2/pos/sales")
    suspend fun sale(@Body request: SaleRequest): KposEnvelope<Map<String, Any?>?>
}

/**
 * Signs in and applies KposKeyFixup to both returned keys, so callers never handle the
 * raw escaped-equals bug themselves.
 */
suspend fun KposApi.signInWithFixedKeys(appId: String, appSecret: String): KposEnvelope<SignInResponse> {
    val envelope = signIn(SignInRequest(appId, appSecret))
    val fixedData = envelope.data?.let {
        SignInResponse(
            platformPublicKey = KposKeyFixup.fix(it.platformPublicKey),
            appPrivateKey = KposKeyFixup.fix(it.appPrivateKey),
        )
    }
    return envelope.copy(data = fixedData)
}
