package com.kspay.forwarder.kpay

import com.kspay.forwarder.crypto.Nonce
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Stamps the `timestamp`/`nonceStr` headers KPay requires on every request, including
 * /v2/pos/sign. Sign-in is unsigned (no RSA `signature` — there is no working key yet), but
 * KPay's docs still require timestamp/nonceStr there; without them KPOS rejects the call with
 * code=10001 ("invalid parameter"), since the timestamp is what it validates for freshness.
 */
class SignInHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("timestamp", System.currentTimeMillis().toString())
            .header("nonceStr", Nonce.generate())
            .build()
        return chain.proceed(request)
    }
}
