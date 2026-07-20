package com.kspay.forwarder.ui.admin

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.kspay.forwarder.kpay.AdminPasswordStore
import com.kspay.forwarder.kpay.TerminalInfoStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun fakeAdminPasswordStore(expected: String = "real-password") = object : AdminPasswordStore {
    override fun verify(password: String): Boolean = password == expected
}

private fun fakeTerminalInfoStore(initial: String? = null) = object : TerminalInfoStore {
    private var current = initial
    override fun getTid(): String? = current
    override fun saveTid(tid: String) { current = tid }
}

@RunWith(RobolectricTestRunner::class)
class TidSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `starts on the password gate`() {
        val viewModel = TidSettingsViewModel(fakeAdminPasswordStore(), fakeTerminalInfoStore())

        composeRule.setContent { TidSettingsScreen(viewModel) }

        composeRule.onNodeWithText("Admin Access").assertExists()
    }

    @Test
    fun `wrong password shows an error and stays locked`() {
        val viewModel = TidSettingsViewModel(fakeAdminPasswordStore("real-password"), fakeTerminalInfoStore())

        composeRule.setContent { TidSettingsScreen(viewModel) }
        composeRule.onNodeWithText("Admin password").performTextInput("wrong-password")
        composeRule.onNodeWithText("Unlock").performClick()

        composeRule.onNodeWithText("Incorrect password").assertExists()
    }

    @Test
    fun `correct password unlocks the TID field`() {
        val viewModel = TidSettingsViewModel(fakeAdminPasswordStore("real-password"), fakeTerminalInfoStore())

        composeRule.setContent { TidSettingsScreen(viewModel) }
        composeRule.onNodeWithText("Admin password").performTextInput("real-password")
        composeRule.onNodeWithText("Unlock").performClick()

        composeRule.onNodeWithText("Terminal ID").assertExists()
    }

    @Test
    fun `saving a TID persists it and shows a confirmation`() {
        val terminalInfoStore = fakeTerminalInfoStore()
        val viewModel = TidSettingsViewModel(fakeAdminPasswordStore("real-password"), terminalInfoStore)

        composeRule.setContent { TidSettingsScreen(viewModel) }
        composeRule.onNodeWithText("Admin password").performTextInput("real-password")
        composeRule.onNodeWithText("Unlock").performClick()
        composeRule.onNodeWithText("8-digit TID").performTextInput("00000524")
        composeRule.onNodeWithText("Save TID").performClick()

        composeRule.onNodeWithText("TID saved").assertExists()
        assert(terminalInfoStore.getTid() == "00000524")
    }

    @Test
    fun `tapping Back invokes the callback`() {
        val viewModel = TidSettingsViewModel(fakeAdminPasswordStore(), fakeTerminalInfoStore())
        var backCalled = false

        composeRule.setContent { TidSettingsScreen(viewModel, onBack = { backCalled = true }) }
        composeRule.onNodeWithText("Back").performClick()

        assert(backCalled)
    }
}
