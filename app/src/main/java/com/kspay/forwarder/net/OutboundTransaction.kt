package com.kspay.forwarder.net

/**
 * The exact outbound payload forwarded to KSPay's ingest endpoint — the contract of record
 * the backend is built to match. Every KPay field is forwarded verbatim (original names,
 * cent-string units); the backend normalizes at ingest. Do not rename/drop a field here
 * without updating the backend to match — see OutboundTransactionMapperTest for the frozen
 * field set.
 */
data class OutboundTransaction(
    // Idempotency / identity
    val outTradeNo: String,
    val kpayOutTradeNo: String?,
    val transactionNo: String?,
    val refNo: String?,
    val deviceID: String?,
    val kpayTerminalNo: String?,
    val appId: String,
    val terminalType: String?,
    val appVersion: String?,
    val commitTime: Long?,
    // Money (cent strings)
    val payAmount: String?,
    val tipsAmount: String?,
    val discountAmount: String?,
    val surchargeAmount: String?,
    val orderAmount: String?,
    val payCurrency: String?,
    // Result / classification
    val payResult: Int,
    val transactionType: Int?,
    val payMethod: Int?,
    val reason: String?,
    val cardInputCode: String?,
    val cardNo: String?,
    val authCode: String?,
    val batchNo: String?,
    val traceNo: String?,
    val needSignature: Boolean?,
    // Client meta
    val forwarderVersion: String,
    val captureMethod: String = "poll",
    val capturedAt: Long,
    val localId: String,
)
