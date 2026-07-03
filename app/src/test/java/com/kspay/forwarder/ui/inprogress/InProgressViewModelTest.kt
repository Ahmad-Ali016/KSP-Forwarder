package com.kspay.forwarder.ui.inprogress

import androidx.room.Room
import com.kspay.forwarder.data.ForwarderDatabase
import com.kspay.forwarder.data.OutTradeNoGenerator
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
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
class InProgressViewModelTest {

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
    fun `observing an unknown outTradeNo stays Loading`() = runTest {
        val viewModel = InProgressViewModel(repository)

        viewModel.observe("missing")

        assertEquals(InProgressUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `SALE_SENT is rendered as Sending`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository.updateState(draft, TransactionState.SALE_SENT)
        val viewModel = InProgressViewModel(repository)

        viewModel.observe(draft.outTradeNo)

        assertEquals(InProgressUiState.Sending, viewModel.uiState.first { it != InProgressUiState.Loading })
    }

    @Test
    fun `a live state change from SALE_SENT to POLLING updates uiState without re-observing`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        val sent = repository.updateState(draft, TransactionState.SALE_SENT)
        val viewModel = InProgressViewModel(repository)
        viewModel.observe(draft.outTradeNo)
        viewModel.uiState.first { it != InProgressUiState.Loading }

        repository.updateState(sent, TransactionState.POLLING)

        assertEquals(InProgressUiState.Polling, viewModel.uiState.first { it != InProgressUiState.Sending })
    }

    @Test
    fun `a terminal state is rendered as Finished with the transaction`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        val polling = repository.updateState(draft, TransactionState.POLLING)
        val viewModel = InProgressViewModel(repository)
        viewModel.observe(draft.outTradeNo)
        viewModel.uiState.first { it != InProgressUiState.Loading }

        repository.updateState(polling, TransactionState.SUCCEEDED)

        val finished = viewModel.uiState.first { it is InProgressUiState.Finished } as InProgressUiState.Finished
        assertEquals(TransactionState.SUCCEEDED, finished.transaction.state)
        assertEquals(draft.outTradeNo, finished.transaction.outTradeNo)
    }
}
