package com.kspay.forwarder.ui.fareentry

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FareEntryViewModelTest {

    @Test
    fun `no digits entered is empty and invalid`() {
        val viewModel = FareEntryViewModel()

        val state = viewModel.uiState.value

        assertEquals("$0.00", state.displayText)
        assertFalse(state.isValid)
    }

    @Test
    fun `digits are interpreted as cents`() {
        val viewModel = FareEntryViewModel()

        "1234".forEach(viewModel::onDigit)

        val state = viewModel.uiState.value
        assertEquals(BigDecimal("12.34"), state.amount)
        assertEquals("$12.34", state.displayText)
        assertTrue(state.isValid)
    }

    @Test
    fun `all-zero digits are invalid`() {
        val viewModel = FareEntryViewModel()

        "000".forEach(viewModel::onDigit)

        assertFalse(viewModel.uiState.value.isValid)
    }

    @Test
    fun `backspace removes the last digit`() {
        val viewModel = FareEntryViewModel()
        "12".forEach(viewModel::onDigit)

        viewModel.onBackspace()

        assertEquals("$0.01", viewModel.uiState.value.displayText)
    }

    @Test
    fun `backspace on empty input stays empty and does not crash`() {
        val viewModel = FareEntryViewModel()

        viewModel.onBackspace()

        assertEquals("$0.00", viewModel.uiState.value.displayText)
    }

    @Test
    fun `clear resets to empty`() {
        val viewModel = FareEntryViewModel()
        "999".forEach(viewModel::onDigit)

        viewModel.onClear()

        assertEquals("$0.00", viewModel.uiState.value.displayText)
    }

    @Test
    fun `a leading zero is replaced by the next digit instead of accumulating`() {
        val viewModel = FareEntryViewModel()

        viewModel.onDigit('0')
        viewModel.onDigit('5')

        assertEquals("$0.05", viewModel.uiState.value.displayText)
    }

    @Test
    fun `digit entry is capped so the amount cannot grow without bound`() {
        val viewModel = FareEntryViewModel()

        "12345678901234".forEach(viewModel::onDigit) // way more than the 10-digit cap

        assertEquals(10, viewModel.uiState.value.digits.length)
    }
}
