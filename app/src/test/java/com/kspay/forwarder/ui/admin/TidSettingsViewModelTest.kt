package com.kspay.forwarder.ui.admin

import com.kspay.forwarder.kpay.AdminPasswordStore
import com.kspay.forwarder.kpay.TerminalInfoStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAdminPasswordStore(private val password: String = "seed-password") : AdminPasswordStore {
    override fun verify(password: String): Boolean = password == this.password
}

private class FakeTerminalInfoStore(initial: String? = null) : TerminalInfoStore {
    private var current = initial
    override fun getTid(): String? = current
    override fun saveTid(tid: String) { current = tid }
}

class TidSettingsViewModelTest {

    @Test
    fun `starts locked`() {
        val viewModel = TidSettingsViewModel(FakeAdminPasswordStore(), FakeTerminalInfoStore())

        assertTrue(viewModel.uiState.value is TidSettingsUiState.Locked)
    }

    @Test
    fun `wrong password stays locked with an error`() {
        val viewModel = TidSettingsViewModel(FakeAdminPasswordStore("real-password"), FakeTerminalInfoStore())

        viewModel.unlock("wrong-password")

        val state = viewModel.uiState.value as TidSettingsUiState.Locked
        assertEquals("Incorrect password", state.error)
    }

    @Test
    fun `correct password unlocks and shows the current TID`() {
        val viewModel = TidSettingsViewModel(FakeAdminPasswordStore("real-password"), FakeTerminalInfoStore("00000524"))

        viewModel.unlock("real-password")

        val state = viewModel.uiState.value as TidSettingsUiState.Unlocked
        assertEquals("00000524", state.currentTid)
    }

    @Test
    fun `unlocking with no TID set shows null`() {
        val viewModel = TidSettingsViewModel(FakeAdminPasswordStore("real-password"), FakeTerminalInfoStore(null))

        viewModel.unlock("real-password")

        val state = viewModel.uiState.value as TidSettingsUiState.Unlocked
        assertNull(state.currentTid)
    }

    @Test
    fun `saveTid persists an 8-digit TID via TerminalInfoStore`() {
        val terminalInfoStore = FakeTerminalInfoStore()
        val viewModel = TidSettingsViewModel(FakeAdminPasswordStore("real-password"), terminalInfoStore)
        viewModel.unlock("real-password")

        viewModel.saveTid("00000524")

        assertEquals("00000524", terminalInfoStore.getTid())
        val state = viewModel.uiState.value as TidSettingsUiState.Unlocked
        assertEquals("00000524", state.currentTid)
        assertEquals("TID saved", state.message)
    }

    @Test
    fun `saveTid rejects a TID that is not exactly 8 digits`() {
        val terminalInfoStore = FakeTerminalInfoStore()
        val viewModel = TidSettingsViewModel(FakeAdminPasswordStore("real-password"), terminalInfoStore)
        viewModel.unlock("real-password")

        viewModel.saveTid("123")

        assertNull(terminalInfoStore.getTid())
        val state = viewModel.uiState.value as TidSettingsUiState.Unlocked
        assertEquals("TID must be exactly 8 digits", state.message)
    }
}
