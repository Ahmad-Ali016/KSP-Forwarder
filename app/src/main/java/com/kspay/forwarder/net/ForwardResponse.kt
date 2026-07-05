package com.kspay.forwarder.net

import com.squareup.moshi.Json

/**
 * Body of a successful (2xx) forward response. Confirmed by the KSPay backend team
 * (2026-07-05): the status code alone is a complete, correct success contract -- `data.status`
 * is parsed for local debug logging only (e.g. flagging QUARANTINED as a stale device/driver
 * mapping signal); no forwarder logic ever branches on it.
 */
data class ForwardResponse(
    val success: Boolean? = null,
    val data: ForwardResultData? = null,
)

data class ForwardResultData(
    @Json(name = "out_trade_no") val outTradeNo: String? = null,
    @Json(name = "transaction_id") val transactionId: String? = null,
    val status: String? = null,
)
