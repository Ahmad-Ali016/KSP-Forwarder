package com.kspay.forwarder.kpay

import androidx.room.Room
import com.kspay.forwarder.data.ForwarderDatabase
import com.kspay.forwarder.data.OutTradeNoGenerator
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

/**
 * The full offline-provable money path: DRAFT -> (SaleUseCase) -> SALE_SENT ->
 * (PollUseCase) -> a final state, persisted in Room end to end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MoneyPathScenarioTest {

    private lateinit var server: MockWebServer
    private lateinit var db: ForwarderDatabase
    private lateinit var repository: TransactionRepository
    private lateinit var api: KposApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ForwarderDatabase::class.java).build()
        repository = TransactionRepository(db.localTransactionDao(), OutTradeNoGenerator("dev01"))
        api = KposClientFactory.create(baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun saleBody() = """{"code":10000,"data":{}}"""
    private fun queryBody(payResult: Int) =
        """{"code":10000,"data":{"outTradeNO":"OT","payResult":$payResult,"payAmount":"000000000100","orderAmount":"000000000100"}}"""

    private suspend fun runFullFlow(): LocalResult {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        val saleSent = SaleUseCase(api, repository).execute(draft)
        val finalTransaction = PollUseCase(api, repository).execute(saleSent)
        val persisted = repository.findByOutTradeNo(draft.outTradeNo)
        return LocalResult(finalTransaction, persisted)
    }

    private data class LocalResult(
        val returned: com.kspay.forwarder.data.LocalTransaction,
        val persisted: com.kspay.forwarder.data.LocalTransaction?,
    )

    @Test
    fun `sale then pending then pending then success ends SUCCEEDED with raw result stored`() = runTest {
        server.enqueue(MockResponse().setBody(saleBody()))
        server.enqueue(MockResponse().setBody(queryBody(1)))
        server.enqueue(MockResponse().setBody(queryBody(1)))
        server.enqueue(MockResponse().setBody(queryBody(2)))

        val result = runFullFlow()

        assertEquals(TransactionState.SUCCEEDED, result.returned.state)
        assertEquals(TransactionState.SUCCEEDED, result.persisted?.state)
        assertNotNull(result.persisted?.rawSaleResultJson)
        assertTrue(result.persisted!!.rawSaleResultJson!!.contains("\"payResult\":2"))
        assertEquals(4, server.requestCount) // 1 sale + 3 queries
        assertEquals(2 * 5_000L, currentTime) // delay before query 2 and query 3
    }

    @Test
    fun `sale then immediate success ends SUCCEEDED`() = runTest {
        server.enqueue(MockResponse().setBody(saleBody()))
        server.enqueue(MockResponse().setBody(queryBody(2)))

        val result = runFullFlow()

        assertEquals(TransactionState.SUCCEEDED, result.persisted?.state)
        assertEquals(2, server.requestCount) // 1 sale + 1 query
    }

    @Test
    fun `sale then a failed query result ends NON_SUCCESS with raw result stored`() = runTest {
        server.enqueue(MockResponse().setBody(saleBody()))
        server.enqueue(MockResponse().setBody(queryBody(3)))

        val result = runFullFlow()

        assertEquals(TransactionState.NON_SUCCESS, result.persisted?.state)
        assertNotNull(result.persisted?.rawSaleResultJson)
    }

    @Test
    fun `sale then budget-timeout leaves the transaction in POLLING with no raw result`() = runTest {
        server.enqueue(MockResponse().setBody(saleBody()))
        repeat(18) { server.enqueue(MockResponse().setBody(queryBody(1))) }

        val result = runFullFlow()

        assertEquals(TransactionState.POLLING, result.persisted?.state)
        assertNull(result.persisted?.rawSaleResultJson)
        assertEquals(19, server.requestCount) // 1 sale + 18 queries
    }
}
