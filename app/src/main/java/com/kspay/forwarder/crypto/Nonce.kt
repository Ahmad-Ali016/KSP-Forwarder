package com.kspay.forwarder.crypto

import java.security.SecureRandom

/**
 * Generates the 32-character nonceStr required on every signed KPay request.
 */
object Nonce {

    private const val LENGTH = 32
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    fun generate(): String =
        buildString(LENGTH) {
            repeat(LENGTH) {
                append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }
}
