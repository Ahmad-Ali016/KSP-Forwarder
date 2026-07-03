package com.kspay.forwarder.data

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Uses a real file-backed database (not in-memory) so closing and reopening it genuinely
 * simulates the OS killing the process and Android relaunching it fresh.
 */
@RunWith(RobolectricTestRunner::class)
class ProcessDeathPersistenceTest {

    private val context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `state survives closing and reopening the database`() = runTest {
        val db1 = Room.databaseBuilder(context, ForwarderDatabase::class.java, DB_NAME).build()
        val repository1 = TransactionRepository(db1.localTransactionDao(), OutTradeNoGenerator("dev01"))
        val draft = repository1.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository1.updateState(draft, TransactionState.SALE_SENT)
        db1.close()

        val db2 = Room.databaseBuilder(context, ForwarderDatabase::class.java, DB_NAME).build()
        val reloaded = db2.localTransactionDao().findByOutTradeNo(draft.outTradeNo)
        db2.close()

        assertEquals(TransactionState.SALE_SENT, reloaded?.state)
        assertEquals(draft.outTradeNo, reloaded?.outTradeNo)
        assertEquals(draft.payAmountCents, reloaded?.payAmountCents)
    }

    private companion object {
        const val DB_NAME = "process-death-test-db"
    }
}
