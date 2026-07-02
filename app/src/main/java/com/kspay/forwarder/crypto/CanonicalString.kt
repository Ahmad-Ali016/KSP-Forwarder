package com.kspay.forwarder.crypto

/**
 * Builds the exact byte sequence KPay signs: METHOD, URI, timestamp, nonceStr, and (for POST)
 * the raw JSON body, each on its own line joined by \n, with a mandatory trailing \n after the
 * last line. GET requests are 4 lines (no body); POST requests are 5 lines. If the last field's
 * content itself ends in \n, the trailing-\n rule naturally produces a double newline — no
 * special-casing needed, it falls out of straight concatenation.
 */
object CanonicalString {

    fun build(method: String, uri: String, timestamp: String, nonce: String, body: String? = null): String {
        val lines = if (body != null) {
            listOf(method, uri, timestamp, nonce, body)
        } else {
            listOf(method, uri, timestamp, nonce)
        }
        return lines.joinToString(separator = "\n") + "\n"
    }
}
