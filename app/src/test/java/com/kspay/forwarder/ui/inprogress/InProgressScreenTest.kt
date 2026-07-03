package com.kspay.forwarder.ui.inprogress

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import com.kspay.forwarder.data.ForwarderDatabase
import com.kspay.forwarder.data.LocalTransaction
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InProgressScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
    fun `SALE_SENT renders the sending message`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository.updateState(draft, TransactionState.SALE_SENT)
        val viewModel = InProgressViewModel(repository)

        composeRule.setContent { InProgressScreen(draft.outTradeNo, viewModel) }

        composeRule.onNodeWithText("Sending sale...").assertExists()
    }

    @Test
    fun `POLLING renders the processing message`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository.updateState(draft, TransactionState.POLLING)
        val viewModel = InProgressViewModel(repository)

        composeRule.setContent { InProgressScreen(draft.outTradeNo, viewModel) }

        composeRule.onNodeWithText("Processing payment...").assertExists()
    }

    @Test
    fun `a terminal state invokes onFinished with the transaction`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository.updateState(draft, TransactionState.SUCCEEDED)
        val viewModel = InProgressViewModel(repository)
        var finished: LocalTransaction? = null

        composeRule.setContent {
            InProgressScreen(draft.outTradeNo, viewModel, onFinished = { finished = it })
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { finished != null }
        assert(finished?.state == TransactionState.SUCCEEDED) { "expected SUCCEEDED, got ${finished?.state}" }
    }

    @Test
    fun `back press is blocked while a sale is in flight`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository.updateState(draft, TransactionState.POLLING)
        val viewModel = InProgressViewModel(repository)

        composeRule.setContent { InProgressScreen(draft.outTradeNo, viewModel) }

        assert(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun `back press is allowed once the transaction reaches a terminal state`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository.updateState(draft, TransactionState.SUCCEEDED)
        val viewModel = InProgressViewModel(repository)

        composeRule.setContent { InProgressScreen(draft.outTradeNo, viewModel) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()
        }
    }
}
