package com.kspay.forwarder.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kspay.forwarder.crypto.Money
import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import com.kspay.forwarder.kpay.QueryResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val SUCCESS_STATES =
    setOf(TransactionState.SUCCEEDED, TransactionState.FORWARDING, TransactionState.FORWARDED)

class ResultViewModel(private val repository: TransactionRepository) : ViewModel() {

    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(QueryResponse::class.java)

    private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    fun observe(outTradeNo: String) {
        viewModelScope.launch {
            repository.observe(outTradeNo).collect { transaction ->
                _uiState.value = toUiState(transaction)
            }
        }
    }

    private fun toUiState(transaction: LocalTransaction?): ResultUiState {
        if (transaction == null || transaction.state !in TERMINAL_STATES) return ResultUiState.Loading

        val result = transaction.rawSaleResultJson?.let(adapter::fromJson)
        return when {
            transaction.state in SUCCESS_STATES ->
                ResultUiState.Success(Money.fromKpayCents(transaction.payAmountCents), result?.refNo)
            transaction.state == TransactionState.ANOMALY ->
                ResultUiState.Anomaly(Money.fromKpayCents(transaction.payAmountCents))
            else -> ResultUiState.NonSuccess(result?.reason ?: "Payment was not successful")
        }
    }

    private companion object {
        val TERMINAL_STATES =
            SUCCESS_STATES + setOf(TransactionState.NON_SUCCESS, TransactionState.ABORTED, TransactionState.ANOMALY)
    }
}
