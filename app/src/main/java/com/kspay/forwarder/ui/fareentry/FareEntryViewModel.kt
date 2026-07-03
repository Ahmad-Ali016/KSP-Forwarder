package com.kspay.forwarder.ui.fareentry

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FareEntryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FareEntryUiState())
    val uiState: StateFlow<FareEntryUiState> = _uiState.asStateFlow()

    fun onDigit(digit: Char) {
        val current = _uiState.value.digits
        if (current.length >= MAX_DIGITS) return
        _uiState.value = FareEntryUiState(if (current == "0") digit.toString() else current + digit)
    }

    fun onBackspace() {
        _uiState.value = FareEntryUiState(_uiState.value.digits.dropLast(1))
    }

    fun onClear() {
        _uiState.value = FareEntryUiState()
    }

    private companion object {
        const val MAX_DIGITS = 10 // up to $99,999,999.99
    }
}
