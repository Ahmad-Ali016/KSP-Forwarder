package com.kspay.forwarder.ui.inprogress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InProgressViewModel(private val repository: TransactionRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<InProgressUiState>(InProgressUiState.Loading)
    val uiState: StateFlow<InProgressUiState> = _uiState.asStateFlow()

    /** Collects Room's live updates for this outTradeNo as SaleUseCase/PollUseCase write state. */
    fun observe(outTradeNo: String) {
        viewModelScope.launch {
            repository.observe(outTradeNo).collect { transaction ->
                _uiState.value = toUiState(transaction)
            }
        }
    }

    private fun toUiState(transaction: LocalTransaction?): InProgressUiState = when {
        transaction == null -> InProgressUiState.Loading
        transaction.state == TransactionState.DRAFT || transaction.state == TransactionState.SALE_SENT ->
            InProgressUiState.Sending
        transaction.state == TransactionState.POLLING -> InProgressUiState.Polling
        else -> InProgressUiState.Finished(transaction)
    }
}
