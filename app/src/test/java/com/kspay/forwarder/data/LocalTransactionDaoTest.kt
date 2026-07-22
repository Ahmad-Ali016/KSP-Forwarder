package com.kspay.forwarder.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
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
class LocalTransactionDaoTest {

    private lateinit var db: ForwarderDatabase
    private lateinit var dao: LocalTransactionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ForwarderDatabase::class.java).build()
        dao = db.localTransactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sample(outTradeNo: String = "OT1", state: TransactionState = TransactionState.DRAFT) = LocalTransaction(
        outTradeNo = outTradeNo,
        state = state,
        payAmountCents = "000000000100",
        currency = "036",
        paymentType = 1,
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun `insert then find by outTradeNo returns the same row`() = runTest {
        dao.insert(sample())

        val found = dao.findByOutTradeNo("OT1")

        assertEquals("OT1", found?.outTradeNo)
        assertEquals(TransactionState.DRAFT, found?.state)
    }

    @Test
    fun `duplicate outTradeNo violates the unique constraint`() = runTest {
        dao.insert(sample())

        var thrown: Throwable? = null
        try {
            dao.insert(sample())
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }

        assertTrue(thrown is SQLiteConstraintException)
    }

    @Test
    fun `findByOutTradeNo returns null when not found`() = runTest {
        assertNull(dao.findByOutTradeNo("missing"))
    }

    @Test
    fun `findByState filters correctly`() = runTest {
        dao.insert(sample("OT1"))
        dao.insert(sample("OT2", TransactionState.SUCCEEDED))

        val draft = dao.findByState(TransactionState.DRAFT)

        assertEquals(1, draft.size)
        assertEquals("OT1", draft.first().outTradeNo)
    }

    @Test
    fun `deleteOlderThan removes only aged FORWARDED NON_SUCCESS ABORTED rows`() = runTest {
        val cutoff = 1_000L
        val aged = cutoff - 1
        val recent = cutoff + 1
        dao.insert(sample("OLD_FORWARDED", TransactionState.FORWARDED).copy(updatedAt = aged))
        dao.insert(sample("OLD_NON_SUCCESS", TransactionState.NON_SUCCESS).copy(updatedAt = aged))
        dao.insert(sample("OLD_ABORTED", TransactionState.ABORTED).copy(updatedAt = aged))
        dao.insert(sample("OLD_ANOMALY", TransactionState.ANOMALY).copy(updatedAt = aged))
        dao.insert(sample("OLD_POLLING", TransactionState.POLLING).copy(updatedAt = aged))
        dao.insert(sample("RECENT_FORWARDED", TransactionState.FORWARDED).copy(updatedAt = recent))

        val deleted = dao.deleteOlderThan(cutoff)

        assertEquals(3, deleted)
        assertNull(dao.findByOutTradeNo("OLD_FORWARDED"))
        assertNull(dao.findByOutTradeNo("OLD_NON_SUCCESS"))
        assertNull(dao.findByOutTradeNo("OLD_ABORTED"))
        assertNotNull(dao.findByOutTradeNo("OLD_ANOMALY"))
        assertNotNull(dao.findByOutTradeNo("OLD_POLLING"))
        assertNotNull(dao.findByOutTradeNo("RECENT_FORWARDED"))
    }
}
