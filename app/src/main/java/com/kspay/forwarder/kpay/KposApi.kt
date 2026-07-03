package com.kspay.forwarder.kpay

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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

    /**
     * Must be called on a client with SignedRequestInterceptor attached. includeReceipt
     * defaults to true — deviceID/commitTime (the backend's driver-attribution fields) are
     * only returned by KPOS when this flag is set, per KPay's docs. Do not default this to
     * false; see QueryResponse's KDoc.
     */
    @GET("/v2/pos/query")
    suspend fun query(
        @Query("outTradeNo") outTradeNo: String,
        @Query("includeReceipt") includeReceipt: Boolean = true,
    ): KposEnvelope<QueryResponse>
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
