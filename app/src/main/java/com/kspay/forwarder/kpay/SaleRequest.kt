package com.kspay.forwarder.kpay

/** POST /v2/pos/sales body. Deliberately no callbackUrl — V1 is polling-only. */
data class SaleRequest(
    val outTradeNo: String,
    val payAmount: String,
    val tipsAmount: String = "000000000000",
    val payCurrency: String = "036",
    val paymentType: Int,
)
