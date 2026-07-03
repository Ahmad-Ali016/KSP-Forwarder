package com.kspay.forwarder.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(repository: TransactionRepository) : ViewModel() {

    val transactions: StateFlow<List<LocalTransaction>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
