package com.kspay.forwarder.kpay

/**
 * Fixes a documented KPay sign-in bug (their own FAQ, "The signature verification tool
 * cannot encrypt data normally"): platformPublicKey/appPrivateKey can come back with their
 * Base64 padding double-escaped, so the parsed string contains the literal 6-character
 * backslash-u-zero-zero-three-d sequence instead of a real "=" character. A compliant JSON
 * parser would never produce this on its own, so this is a defensive string-level cleanup
 * applied after JSON parsing.
 */
object KposKeyFixup {

    private const val ESCAPED_EQUALS_LOWER = "\\u003d"
    private const val ESCAPED_EQUALS_UPPER = "\\u003D"

    fun fix(rawKey: String): String =
        rawKey.replace(ESCAPED_EQUALS_LOWER, "=").replace(ESCAPED_EQUALS_UPPER, "=")
}
