package com.kspay.forwarder.crypto

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Converts between BigDecimal dollar amounts and KPay's 12-digit zero-padded cent-string wire
 * format (e.g. "000000010000" = $100.00). BigDecimal only — never Double/Float for money.
 */
object Money {

    private const val CENT_STRING_LENGTH = 12

    fun toKpayCents(amount: BigDecimal): String {
        require(amount.signum() >= 0) { "Amount must be non-negative: $amount" }
        val cents = amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).toBigIntegerExact()
        val digits = cents.toString()
        require(digits.length <= CENT_STRING_LENGTH) {
            "Amount does not fit in $CENT_STRING_LENGTH digits: $amount"
        }
        return digits.padStart(CENT_STRING_LENGTH, '0')
    }

    fun fromKpayCents(centString: String): BigDecimal {
        require(centString.length == CENT_STRING_LENGTH) {
            "Expected a $CENT_STRING_LENGTH-digit cent string, got ${centString.length} chars: $centString"
        }
        return BigDecimal(centString).movePointLeft(2)
    }
}
