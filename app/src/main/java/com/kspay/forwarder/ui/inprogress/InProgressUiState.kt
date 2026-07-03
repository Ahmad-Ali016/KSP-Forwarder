package com.kspay.forwarder.ui.inprogress

import com.kspay.forwarder.data.LocalTransaction

/** Mirrors the in-flight TransactionState values this screen is shown for. */
sealed class InProgressUiState {
    data object Loading : InProgressUiState()
    data object Sending : InProgressUiState()
    data object Polling : InProgressUiState()
    data class Finished(val transaction: LocalTransaction) : InProgressUiState()
}
