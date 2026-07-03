package com.kspay.forwarder.ui.fareentry

import java.math.BigDecimal

/** Digits typed on the keypad, interpreted as cents (e.g. "123" -> $1.23). */
data class FareEntryUiState(val digits: String = "") {

    val amount: BigDecimal
        get() = if (digits.isEmpty()) BigDecimal.ZERO else BigDecimal(digits).movePointLeft(2)

    val displayText: String
        get() = "$" + amount.setScale(2).toPlainString()

    val isValid: Boolean
        get() = amount > BigDecimal.ZERO
}
