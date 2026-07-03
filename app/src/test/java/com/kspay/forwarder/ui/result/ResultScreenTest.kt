package com.kspay.forwarder.ui.result

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import com.kspay.forwarder.data.ForwarderDatabase
import com.kspay.forwarder.data.OutTradeNoGenerator
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import com.kspay.forwarder.kpay.QueryResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
class ResultScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: ForwarderDatabase
    private lateinit var repository: TransactionRepository
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(QueryResponse::class.java)

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
    fun `SUCCEEDED renders the amount and ref`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000012345", currency = "036", paymentType = 1)
        val withResult = draft.copy(
            rawSaleResultJson = adapter.toJson(QueryResponse(outTradeNO = draft.outTradeNo, payResult = 2, refNo = "REF123")),
        )
        repository.updateState(withResult, TransactionState.SUCCEEDED)
        val viewModel = ResultViewModel(repository)

        composeRule.setContent { ResultScreen(draft.outTradeNo, viewModel) }

        composeRule.onNodeWithText("$123.45", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Ref: REF123", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `NON_SUCCESS renders the failure message`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        val withResult = draft.copy(
            rawSaleResultJson = adapter.toJson(
                QueryResponse(outTradeNO = draft.outTradeNo, payResult = 3, reason = "Card declined"),
            ),
        )
        repository.updateState(withResult, TransactionState.NON_SUCCESS)
        val viewModel = ResultViewModel(repository)

        composeRule.setContent { ResultScreen(draft.outTradeNo, viewModel) }

        composeRule.onNodeWithText("Payment Not Successful", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Card declined", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `tapping Done invokes the callback`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository.updateState(draft, TransactionState.SUCCEEDED)
        var doneCalled = false
        val viewModel = ResultViewModel(repository)

        composeRule.setContent { ResultScreen(draft.outTradeNo, viewModel, onDone = { doneCalled = true }) }
        composeRule.onNodeWithText("Done").performClick()

        assert(doneCalled)
    }
}
