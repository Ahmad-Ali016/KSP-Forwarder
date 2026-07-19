package com.kspay.forwarder.ui.history

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import com.kspay.forwarder.data.ForwarderDatabase
import com.kspay.forwarder.data.OutTradeNoGenerator
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: ForwarderDatabase
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ForwarderDatabase::class.java).build()
        repository = TransactionRepository(db.localTransactionDao(), OutTradeNoGenerator(devicePrefix = "dev01"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `no transactions renders the empty message`() = runTest {
        val viewModel = HistoryViewModel(repository)

        composeRule.setContent { HistoryScreen(viewModel) }

        composeRule.onNodeWithText("No transactions yet").assertExists()
    }

    @Test
    fun `a transaction renders its amount, state, and outTradeNo`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000012345", currency = "036", paymentType = 1)
        repository.updateState(draft, TransactionState.SUCCEEDED)
        val viewModel = HistoryViewModel(repository)

        composeRule.setContent { HistoryScreen(viewModel) }

        composeRule.onNodeWithText("$123.45  ·  SUCCEEDED", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(draft.outTradeNo, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a transaction renders its last-updated timestamp`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000012345", currency = "036", paymentType = 1)
        val updated = repository.updateState(draft, TransactionState.SUCCEEDED)
        val viewModel = HistoryViewModel(repository)

        composeRule.setContent { HistoryScreen(viewModel) }

        composeRule.onNodeWithText(TIMESTAMP_FORMAT.format(Date(updated.updatedAt)), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `tapping Back invokes the callback`() = runTest {
        val viewModel = HistoryViewModel(repository)
        var backCalled = false

        composeRule.setContent { HistoryScreen(viewModel, onBack = { backCalled = true }) }
        composeRule.onNodeWithText("Back").performClick()

        assert(backCalled)
    }
}
