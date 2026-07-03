package com.kspay.forwarder.net

/** Server-driven feature flags, fetched at startup and cached so the app still works offline. */
data class FeatureConfig(
    val enableRefund: Boolean = false,
    val enableVoid: Boolean = false,
    val pollIntervalSeconds: Int = 5,
    val backendIngestUrl: String? = null,
) {
    companion object {
        val SAFE_DEFAULTS = FeatureConfig()
    }
}
