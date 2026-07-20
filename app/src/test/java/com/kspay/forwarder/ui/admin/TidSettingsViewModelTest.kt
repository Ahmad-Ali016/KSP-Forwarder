package com.kspay.forwarder.ui.admin

import com.kspay.forwarder.kpay.AdminPasswordStore
import com.kspay.forwarder.kpay.TerminalInfoStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAdminPasswordStore(initial: String = "seed-password") : AdminPasswordStore {
    private var current = initial
    override fun verify(password: String): Boolean = password == current
    override fun changePassword(current: String, new: String): Boolean {
        if (!verify(current)) return false
        this.current = new
        return true
    }
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

    @Test
    fun `changePassword rejects a wrong current password`() {
        val adminPasswordStore = FakeAdminPasswordStore("real-password")
        val viewModel = TidSettingsViewModel(adminPasswordStore, FakeTerminalInfoStore())
        viewModel.unlock("real-password")

        viewModel.changePassword("wrong-current", "new-password")

        val state = viewModel.uiState.value as TidSettingsUiState.Unlocked
        assertEquals("Current password is incorrect", state.message)
        assertTrue(adminPasswordStore.verify("real-password"))
    }

    @Test
    fun `changePassword accepts a correct current password and the old password stops working`() {
        val adminPasswordStore = FakeAdminPasswordStore("real-password")
        val viewModel = TidSettingsViewModel(adminPasswordStore, FakeTerminalInfoStore())
        viewModel.unlock("real-password")

        viewModel.changePassword("real-password", "new-password")

        val state = viewModel.uiState.value as TidSettingsUiState.Unlocked
        assertEquals("Password changed", state.message)
        assertTrue(adminPasswordStore.verify("new-password"))
        assertTrue(!adminPasswordStore.verify("real-password"))
    }
}
