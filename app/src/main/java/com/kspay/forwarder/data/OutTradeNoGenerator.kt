package com.kspay.forwarder.data

import java.security.SecureRandom

/**
 * Generates outTradeNo: devicePrefix + monotonic time + random, <=32 chars, letters/digits
 * only (KPay: "avoid using special symbols").
 */
class OutTradeNoGenerator(private val devicePrefix: String) {

    private val random = SecureRandom()

    fun generate(): String {
        val prefix = devicePrefix.filter { it.isLetterOrDigit() }.take(MAX_PREFIX_LENGTH)
        val time = System.currentTimeMillis().toString(36)
        val randomSuffix = (1..RANDOM_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return (prefix + time + randomSuffix).take(MAX_LENGTH)
    }

    private companion object {
        const val MAX_LENGTH = 32
        const val MAX_PREFIX_LENGTH = 8
        const val RANDOM_LENGTH = 6
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    }
}
