package com.kspay.forwarder.ui.fareentry

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.math.BigDecimal
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FareEntryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `charge is disabled and shows zero when nothing has been entered`() {
        composeRule.setContent { FareEntryScreen() }

        composeRule.onNodeWithText("$0.00").assertExists()
        composeRule.onNodeWithText("Charge").assertIsNotEnabled()
    }

    @Test
    fun `entering digits updates the display and enables Charge`() {
        composeRule.setContent { FareEntryScreen() }

        composeRule.onNodeWithText("5").performClick()
        composeRule.onNodeWithText("0").performClick()

        composeRule.onNodeWithText("$0.50").assertExists()
        composeRule.onNodeWithText("Charge").assertIsEnabled()
    }

    @Test
    fun `tapping Charge invokes the callback with the entered amount`() {
        var charged: BigDecimal? = null
        composeRule.setContent { FareEntryScreen(onCharge = { charged = it }) }

        composeRule.onNodeWithText("2").performClick()
        composeRule.onNodeWithText("5").performClick()
        composeRule.onNodeWithText("Charge").performClick()

        assert(charged == BigDecimal("0.25")) { "expected 0.25, got $charged" }
    }

    @Test
    fun `tapping History (debug) invokes the callback`() {
        var historyOpened = false
        composeRule.setContent { FareEntryScreen(onViewHistory = { historyOpened = true }) }

        composeRule.onNodeWithText("History (debug)").performScrollTo().performClick()

        assert(historyOpened)
    }

    @Test
    fun `tapping TID invokes the callback`() {
        var tidSettingsOpened = false
        composeRule.setContent { FareEntryScreen(onTidSettings = { tidSettingsOpened = true }) }

        composeRule.onNodeWithText("TID").performScrollTo().performClick()

        assert(tidSettingsOpened)
    }

    @Test
    fun `clear resets the display back to zero and disables Charge`() {
        composeRule.setContent { FareEntryScreen() }
        composeRule.onNodeWithText("9").performClick()

        composeRule.onNodeWithText("C").performClick()

        composeRule.onNodeWithText("$0.00").assertExists()
        composeRule.onNodeWithText("Charge").assertIsNotEnabled()
    }
}
