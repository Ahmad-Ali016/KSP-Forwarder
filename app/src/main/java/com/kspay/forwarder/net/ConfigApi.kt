package com.kspay.forwarder.net

import retrofit2.http.GET
import retrofit2.http.Header

/**
 * Confirmed live (2026-07-05) by the KSPay backend team, device-authed via X-Device-Token.
 * NOTE: the real response uses snake_case keys that don't match FeatureConfig's field names
 * one-for-one (e.g. enable_sale/enable_abort/poll_timeout_seconds/reconciliation_interval_minutes/
 * ingest_path aren't modeled at all yet) — see BUILD_PROGRESS.md's "Resolved" section for the
 * full real shape; FeatureConfig.kt needs updating to match before this can parse anything real.
 */
interface ConfigApi {
    @GET("/api/v1/config/")
    suspend fun fetchConfig(@Header("X-Device-Token") deviceToken: String): FeatureConfig
}
