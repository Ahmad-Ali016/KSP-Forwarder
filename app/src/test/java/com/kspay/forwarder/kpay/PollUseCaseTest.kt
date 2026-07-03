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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PollUseCaseTest {

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

    private fun queryBody(payResult: Int) = """{"code":10000,"data":{"outTradeNO":"OT1","payResult":$payResult}}"""

    @Test
    fun `immediate success takes exactly one attempt with no delay`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        server.enqueue(MockResponse().setBody(queryBody(payResult = 2)))

        val result = PollUseCase(api, repository).execute(draft)

        assertEquals(TransactionState.SUCCEEDED, result.state)
        assertEquals(1, server.requestCount)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `every query request sets includeReceipt=true so deviceID and commitTime come back`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        server.enqueue(MockResponse().setBody(queryBody(payResult = 2)))

        PollUseCase(api, repository).execute(draft)

        val recorded = server.takeRequest()
        assertEquals("true", recorded.requestUrl?.queryParameter("includeReceipt"))
    }

    @Test
    fun `immediate non-success takes exactly one attempt`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        server.enqueue(MockResponse().setBody(queryBody(payResult = 3)))

        val result = PollUseCase(api, repository).execute(draft)

        assertEquals(TransactionState.NON_SUCCESS, result.state)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `pending then success waits one 5s interval between the two attempts`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        server.enqueue(MockResponse().setBody(queryBody(payResult = 1)))
        server.enqueue(MockResponse().setBody(queryBody(payResult = 2)))

        val result = PollUseCase(api, repository).execute(draft)

        assertEquals(TransactionState.SUCCEEDED, result.state)
        assertEquals(2, server.requestCount)
        assertEquals(5_000L, currentTime)
    }

    @Test
    fun `always pending exhausts the 18-attempt budget and stays in POLLING`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repeat(18) { server.enqueue(MockResponse().setBody(queryBody(payResult = 1))) }

        val result = PollUseCase(api, repository).execute(draft)

        assertEquals(TransactionState.POLLING, result.state)
        assertEquals(18, server.requestCount)
        assertEquals(17 * 5_000L, currentTime)
    }
}
