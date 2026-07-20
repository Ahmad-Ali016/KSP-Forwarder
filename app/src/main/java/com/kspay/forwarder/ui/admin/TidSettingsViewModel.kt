package com.kspay.forwarder.ui.admin

import androidx.lifecycle.ViewModel
import com.kspay.forwarder.kpay.AdminPasswordStore
import com.kspay.forwarder.kpay.TerminalInfoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val TID_FORMAT = Regex("\\d{8}")

/**
 * Auth state is in-memory only and resets to Locked every time the screen is opened -- no
 * "stay logged in". AdminPasswordStore/TerminalInfoStore access is synchronous (plain
 * EncryptedSharedPreferences/SharedPreferences reads), so no coroutine scope is needed here.
 */
class TidSettingsViewModel(
    private val adminPasswordStore: AdminPasswordStore,
    private val terminalInfoStore: TerminalInfoStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TidSettingsUiState>(TidSettingsUiState.Locked())
    val uiState: StateFlow<TidSettingsUiState> = _uiState.asStateFlow()

    fun unlock(password: String) {
        _uiState.value = if (adminPasswordStore.verify(password)) {
            TidSettingsUiState.Unlocked(currentTid = terminalInfoStore.getTid())
        } else {
            TidSettingsUiState.Locked(error = "Incorrect password")
        }
    }

    fun saveTid(tid: String) {
        if (!TID_FORMAT.matches(tid)) {
            setUnlockedMessage("TID must be exactly 8 digits")
            return
        }
        terminalInfoStore.saveTid(tid)
        _uiState.value = TidSettingsUiState.Unlocked(currentTid = tid, message = "TID saved")
    }

    fun changePassword(current: String, new: String) {
        val ok = adminPasswordStore.changePassword(current, new)
        setUnlockedMessage(if (ok) "Password changed" else "Current password is incorrect")
    }

    private fun setUnlockedMessage(message: String) {
        val state = _uiState.value as? TidSettingsUiState.Unlocked ?: return
        _uiState.value = state.copy(message = message)
    }
}
