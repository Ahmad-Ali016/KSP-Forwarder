package com.kspay.forwarder.kpay

import com.kspay.forwarder.crypto.maskCardNo

/**
 * GET /v2/pos/query response data. payResult: -1 timeout, 1 pending, 2 successful, 3 failed,
 * 4 returned, 5 canceled, 6 transaction canceled.
 *
 * `cardNo`/`cardInputCode`/`authCode`/`batchNo`/`traceNo`/`commitTime`/`appVersion`/
 * `terminalType`/`deviceID`/`kpayMerchantNo`/`aid`/`aidLabel`/`tc`/`kpayMerchantNameEN`/
 * `kpayMerchantAddress`/`receiptDisclaimersEN` are the "KPay receipt data" fields — per KPay's
 * docs these are ONLY populated when the request sets `includeReceipt=true` (see
 * `KposApi.query`). Used by `ReceiptFormatter` to print a passenger receipt.
 * `deviceID`/`commitTime` are load-bearing: the backend's driver attribution depends on them
 * being present in every forwarded transaction, so `includeReceipt` must never be dropped.
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
    val batchNo: String? = null,
    val traceNo: String? = null,
    val commitTime: Long? = null,
    val appVersion: String? = null,
    val terminalType: String? = null,
    val deviceID: String? = null,
    val kpayMerchantNo: String? = null,
    val aid: String? = null,
    val aidLabel: String? = null,
    val tc: String? = null,
    val kpayMerchantNameEN: String? = null,
    val kpayMerchantAddress: String? = null,
    val receiptDisclaimersEN: String? = null,
) {
    // PII-safe: if this object is ever logged/interpolated, cardNo must never appear unmasked.
    override fun toString(): String =
        "QueryResponse(outTradeNO=$outTradeNO, payResult=$payResult, transactionNo=$transactionNo, " +
            "refNo=$refNo, deviceID=$deviceID, commitTime=$commitTime, cardNo=${maskCardNo(cardNo)})"
}
