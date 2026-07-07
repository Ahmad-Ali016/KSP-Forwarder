package com.kspay.forwarder.crypto

/**
 * Masks a PAN to a safe first6+last4 form, for use only in toString()/logging paths (see
 * QueryResponse/OutboundTransaction). Never used for the real wire payload -- Moshi serializes
 * the actual cardNo field directly via reflection, untouched by this.
 */
fun maskCardNo(cardNo: String?): String? {
    if (cardNo == null) return null
    if (cardNo.length <= 10) return "*".repeat(cardNo.length)
    return cardNo.take(6) + "*".repeat(cardNo.length - 10) + cardNo.takeLast(4)
}
