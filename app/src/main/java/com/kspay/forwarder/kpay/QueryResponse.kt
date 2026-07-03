package com.kspay.forwarder.kpay

/**
 * GET /v2/pos/query response data. payResult: -1 timeout, 1 pending, 2 successful, 3 failed,
 * 4 returned, 5 canceled, 6 transaction canceled. Fields only returned when includeReceipt=true
 * are omitted — V1 never sends that flag.
 */
data class QueryResponse(
    val outTradeNO: String,
    val payResult: Int,
    val transactionNo: String? = null,
    val refNo: String? = null,
    val description: String? = null,
    val payAmount: String? = null,
    val tipsAmount: String? = null,
    val payCurrency: String? = null,
    val memberCode: String? = null,
    val reason: String? = null,
    val payMethod: Int? = null,
    val transactionType: Int? = null,
    val needSignature: Boolean? = null,
    val remark: String? = null,
    val discountAmount: String? = null,
    val discountDescription: String? = null,
    val orderAmount: String? = null,
    val surchargeAmount: String? = null,
    val kpayOutTradeNo: String? = null,
    val cardNo: String? = null,
    val cardInputCode: String? = null,
    val authCode: String? = null,
    val commitTime: Long? = null,
    val appVersion: String? = null,
    val terminalType: String? = null,
    val deviceID: String? = null,
)
