package com.kspay.forwarder.ui.result

import java.math.BigDecimal

sealed class ResultUiState {
    data object Loading : ResultUiState()

    data class Success(val amount: BigDecimal, val refNo: String?) : ResultUiState() {
        val amountDisplay: String get() = "$" + amount.setScale(2).toPlainString()
    }

    data class NonSuccess(val message: String) : ResultUiState()
}
