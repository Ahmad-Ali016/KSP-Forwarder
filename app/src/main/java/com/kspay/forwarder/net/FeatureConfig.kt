package com.kspay.forwarder.net

import com.squareup.moshi.Json

/**
 * Server-driven feature flags, fetched at startup and cached so the app still works offline.
 * Field names/JSON keys and SAFE_DEFAULTS match the real KSPay backend response, confirmed
 * directly by the backend team (2026-07-05) -- not an assumed shape.
 */
data class FeatureConfig(
    @Json(name = "enable_sale") val enableSale: Boolean = true,
    @Json(name = "enable_abort") val enableAbort: Boolean = true,
    @Json(name = "enable_void") val enableVoid: Boolean = false,
    @Json(name = "enable_refund") val enableRefund: Boolean = false,
    @Json(name = "poll_interval_seconds") val pollIntervalSeconds: Int = 5,
    @Json(name = "poll_timeout_seconds") val pollTimeoutSeconds: Int = 90,
    @Json(name = "reconciliation_interval_minutes") val reconciliationIntervalMinutes: Int = 15,
    @Json(name = "ingest_path") val ingestPath: String? = "/api/v1/ingest/kpay/transactions/",
) {
    companion object {
        val SAFE_DEFAULTS = FeatureConfig()
    }
}
