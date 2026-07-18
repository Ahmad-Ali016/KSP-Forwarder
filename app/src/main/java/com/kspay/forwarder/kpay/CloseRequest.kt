package com.kspay.forwarder.kpay

/** POST /v2/pos/sales/close body. Aborts a still-pending sale (no final payResult yet). */
data class CloseRequest(val outTradeNo: String)
