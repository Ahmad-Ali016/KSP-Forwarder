package com.kspay.forwarder.kpay

import androidx.room.Room
import com.kspay.forwarder.data.ForwarderDatabase
import com.kspay.forwarder.data.OutTradeNoGenerator
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SaleUseCaseTest {

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

    @Test
    fun `sends the sale body with no callbackUrl and transitions DRAFT to SALE_SENT`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        server.enqueue(MockResponse().setBody("""{"code":10000,"data":{}}"""))

        val result = SaleUseCase(api, repository).execute(draft)

        assertEquals(TransactionState.SALE_SENT, result.state)
        assertEquals(TransactionState.SALE_SENT, repository.findByOutTradeNo(draft.outTradeNo)?.state)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains(""""outTradeNo":"${draft.outTradeNo}""""))
        assertTrue(body.contains(""""payAmount":"000000000100""""))
        assertTrue(body.contains(""""tipsAmount":"000000000000""""))
        assertTrue(body.contains(""""payCurrency":"036""""))
        assertFalse(body.contains("callbackUrl"))
    }

    @Test(expected = IllegalStateException::class)
    fun `a non-10000 response throws and leaves the transaction in DRAFT`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        server.enqueue(MockResponse().setBody("""{"code":20002,"message":"Incorrect amount"}"""))

        try {
            SaleUseCase(api, repository).execute(draft)
        } finally {
            assertEquals(TransactionState.DRAFT, repository.findByOutTradeNo(draft.outTradeNo)?.state)
        }
    }
}
