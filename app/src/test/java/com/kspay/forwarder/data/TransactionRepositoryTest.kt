package com.kspay.forwarder.data

import androidx.room.Room
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TransactionRepositoryTest {

    private lateinit var db: ForwarderDatabase
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ForwarderDatabase::class.java).build()
        repository = TransactionRepository(db.localTransactionDao(), OutTradeNoGenerator(devicePrefix = "dev01"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `createDraft persists a DRAFT row with a generated outTradeNo and a real id`() = runTest {
        val transaction = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)

        assertTrue(transaction.id > 0)
        assertTrue(transaction.outTradeNo.isNotBlank())
        assertEquals(TransactionState.DRAFT, transaction.state)
        assertEquals(transaction, repository.findByOutTradeNo(transaction.outTradeNo))
    }

    @Test
    fun `updateState persists the new state and bumps updatedAt`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)

        val updated = repository.updateState(draft, TransactionState.SALE_SENT)

        assertEquals(TransactionState.SALE_SENT, updated.state)
        assertTrue(updated.updatedAt >= draft.updatedAt)
        assertEquals(TransactionState.SALE_SENT, repository.findByOutTradeNo(draft.outTradeNo)?.state)
    }

    @Test
    fun `findByState returns only matching rows`() = runTest {
        val a = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository.createDraft(payAmountCents = "000000000200", currency = "036", paymentType = 1)
        repository.updateState(a, TransactionState.SUCCEEDED)

        val succeeded = repository.findByState(TransactionState.SUCCEEDED)
        val drafts = repository.findByState(TransactionState.DRAFT)

        assertEquals(1, succeeded.size)
        assertEquals(1, drafts.size)
        assertNotNull(succeeded.first())
    }

    @Test
    fun `deleteTransactionsOlderThanRetention removes aged FORWARDED but keeps aged ANOMALY`() = runTest {
        val old = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(91)
        val dao = db.localTransactionDao()
        dao.insert(
            LocalTransaction(
                outTradeNo = "OLD_FORWARDED", state = TransactionState.FORWARDED,
                payAmountCents = "000000000100", currency = "036", paymentType = 1,
                createdAt = old, updatedAt = old,
            ),
        )
        dao.insert(
            LocalTransaction(
                outTradeNo = "OLD_ANOMALY", state = TransactionState.ANOMALY,
                payAmountCents = "000000000100", currency = "036", paymentType = 1,
                createdAt = old, updatedAt = old,
            ),
        )

        val deleted = repository.deleteTransactionsOlderThanRetention()

        assertEquals(1, deleted)
        assertNull(repository.findByOutTradeNo("OLD_FORWARDED"))
        assertNotNull(repository.findByOutTradeNo("OLD_ANOMALY"))
    }
}
