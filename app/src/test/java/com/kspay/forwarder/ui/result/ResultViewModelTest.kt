package com.kspay.forwarder.ui.result

import androidx.room.Room
import com.kspay.forwarder.data.ForwarderDatabase
import com.kspay.forwarder.data.OutTradeNoGenerator
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import com.kspay.forwarder.kpay.QueryResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ResultViewModelTest {

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

    private fun queryResponseJson(outTradeNo: String, payResult: Int, refNo: String? = null, reason: String? = null) =
        adapter.toJson(
            QueryResponse(outTradeNO = outTradeNo, payResult = payResult, refNo = refNo, reason = reason),
        )

    @Test
    fun `observing an unknown outTradeNo stays Loading`() = runTest {
        val viewModel = ResultViewModel(repository)

        viewModel.observe("missing")

        assertEquals(ResultUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `a non-terminal state stays Loading`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository.updateState(draft, TransactionState.POLLING)
        val viewModel = ResultViewModel(repository)

        viewModel.observe(draft.outTradeNo)

        assertEquals(ResultUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `SUCCEEDED renders the amount and refNo from the stored KPay result`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000012345", currency = "036", paymentType = 1)
        val withResult = draft.copy(rawSaleResultJson = queryResponseJson(draft.outTradeNo, payResult = 2, refNo = "REF123"))
        repository.updateState(withResult, TransactionState.SUCCEEDED)
        val viewModel = ResultViewModel(repository)

        viewModel.observe(draft.outTradeNo)

        val success = viewModel.uiState.first { it != ResultUiState.Loading } as ResultUiState.Success
        assertEquals(BigDecimal("123.45"), success.amount)
        assertEquals("REF123", success.refNo)
    }

    @Test
    fun `FORWARDED still renders as Success`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository.updateState(draft, TransactionState.FORWARDED)
        val viewModel = ResultViewModel(repository)

        viewModel.observe(draft.outTradeNo)

        assertEquals(ResultUiState.Success::class, (viewModel.uiState.first { it != ResultUiState.Loading })::class)
    }

    @Test
    fun `NON_SUCCESS renders the reason from the stored KPay result`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        val withResult =
            draft.copy(rawSaleResultJson = queryResponseJson(draft.outTradeNo, payResult = 3, reason = "Card declined"))
        repository.updateState(withResult, TransactionState.NON_SUCCESS)
        val viewModel = ResultViewModel(repository)

        viewModel.observe(draft.outTradeNo)

        val nonSuccess = viewModel.uiState.first { it != ResultUiState.Loading } as ResultUiState.NonSuccess
        assertEquals("Card declined", nonSuccess.message)
    }

    @Test
    fun `ANOMALY renders the amount without a failure message`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000012345", currency = "036", paymentType = 1)
        val withResult = draft.copy(rawSaleResultJson = queryResponseJson(draft.outTradeNo, payResult = 2))
        repository.updateState(withResult, TransactionState.ANOMALY)
        val viewModel = ResultViewModel(repository)

        viewModel.observe(draft.outTradeNo)

        val anomaly = viewModel.uiState.first { it != ResultUiState.Loading } as ResultUiState.Anomaly
        assertEquals(BigDecimal("123.45"), anomaly.amount)
    }

    @Test
    fun `NON_SUCCESS with no stored reason falls back to a generic message`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository.updateState(draft, TransactionState.NON_SUCCESS)
        val viewModel = ResultViewModel(repository)

        viewModel.observe(draft.outTradeNo)

        val nonSuccess = viewModel.uiState.first { it != ResultUiState.Loading } as ResultUiState.NonSuccess
        assertEquals("Payment was not successful", nonSuccess.message)
    }
}
