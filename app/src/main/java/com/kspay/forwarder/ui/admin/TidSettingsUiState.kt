package com.kspay.forwarder.ui.admin

sealed class TidSettingsUiState {
    data class Locked(val error: String? = null) : TidSettingsUiState()
    data class Unlocked(val currentTid: String?, val message: String? = null) : TidSettingsUiState()
}
