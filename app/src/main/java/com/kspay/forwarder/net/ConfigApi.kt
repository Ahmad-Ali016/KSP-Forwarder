package com.kspay.forwarder.net

import retrofit2.http.GET
import retrofit2.http.Header

/**
 * ⚠ UNCONFIRMED CONVENTION — the feature-config endpoint path isn't documented anywhere
 * available here (KSPay backend doesn't exist in this repo). TODO: confirm once B1
 * (per-terminal device auth + feature-config endpoint) exists in the backend repo, and update
 * this + ConfigProviderTest together if it differs.
 */
interface ConfigApi {
    @GET("/api/v1/config/")
    suspend fun fetchConfig(@Header("X-Device-Token") deviceToken: String): FeatureConfig
}
