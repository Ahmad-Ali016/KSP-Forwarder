package com.kspay.forwarder.ui.history

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
class HistoryViewModelTest {

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
    fun `starts empty when there are no transactions`() = runTest {
        val viewModel = HistoryViewModel(repository)

        assertEquals(emptyList<Any>(), viewModel.transactions.value)
    }

    @Test
    fun `lists transactions newest first`() = runTest {
        val first = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        val second = repository.createDraft(payAmountCents = "000000000200", currency = "036", paymentType = 1)
        repository.updateState(second, TransactionState.SUCCEEDED)
        val viewModel = HistoryViewModel(repository)

        val rows = viewModel.transactions.first { it.size == 2 }

        assertEquals(listOf(second.outTradeNo, first.outTradeNo), rows.map { it.outTradeNo })
    }
}
