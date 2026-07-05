package com.kspay.forwarder.sync

import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.kspay.forwarder.data.ForwarderDatabase
import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.OutTradeNoGenerator
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import com.kspay.forwarder.net.KspayClientFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class ForwardWorkerTest {

    private lateinit var server: MockWebServer
    private lateinit var db: ForwarderDatabase
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ForwarderDatabase::class.java).build()
        repository = TransactionRepository(db.localTransactionDao(), OutTradeNoGenerator("dev01"))
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private suspend fun succeededTransaction(): LocalTransaction {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        return repository.updateState(
            draft.copy(rawSaleResultJson = """{"outTradeNO":"${draft.outTradeNo}","payResult":2}"""),
            TransactionState.SUCCEEDED,
        )
    }

    private fun buildWorker(outTradeNo: String): ForwardWorker {
        val kspayApi = KspayClientFactory.create(baseUrl = server.url("/").toString())
        val kposApi = com.kspay.forwarder.kpay.KposClientFactory.create(baseUrl = server.url("/").toString())
        val factory = ForwarderWorkerFactory(
            repository,
            kspayApi,
            kposApi,
            appId = "202xxx",
            forwarderVersion = "1.0",
            deviceToken = "token",
        )
        return TestListenableWorkerBuilder<ForwardWorker>(RuntimeEnvironment.getApplication())
            .setInputData(workDataOf(ForwardWorker.KEY_OUT_TRADE_NO to outTradeNo))
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun `a 2xx response marks the transaction FORWARDED`() = runTest {
        val transaction = succeededTransaction()
        server.enqueue(MockResponse().setResponseCode(200))

        val result = buildWorker(transaction.outTradeNo).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(TransactionState.FORWARDED, repository.findByOutTradeNo(transaction.outTradeNo)?.state)
    }

    @Test
    fun `a QUARANTINED status in the response body still marks FORWARDED, and is only logged`() = runTest {
        val transaction = succeededTransaction()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"success":true,"data":{"out_trade_no":"${transaction.outTradeNo}",
                   |"transaction_id":"txn-1","status":"QUARANTINED"}}""".trimMargin(),
            ),
        )

        val result = buildWorker(transaction.outTradeNo).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(TransactionState.FORWARDED, repository.findByOutTradeNo(transaction.outTradeNo)?.state)
        assertTrue(ShadowLog.getLogsForTag("ForwardWorker").any { it.msg.contains("QUARANTINED") })
    }

    @Test
    fun `a missing or empty response body never breaks the worker`() = runTest {
        val transaction = succeededTransaction()
        server.enqueue(MockResponse().setResponseCode(200)) // no body at all

        val result = buildWorker(transaction.outTradeNo).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(TransactionState.FORWARDED, repository.findByOutTradeNo(transaction.outTradeNo)?.state)
    }

    @Test
    fun `a 5xx response retries and leaves the transaction SUCCEEDED`() = runTest {
        val transaction = succeededTransaction()
        server.enqueue(MockResponse().setResponseCode(503))

        val result = buildWorker(transaction.outTradeNo).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(TransactionState.SUCCEEDED, repository.findByOutTradeNo(transaction.outTradeNo)?.state)
    }

    @Test
    fun `a 4xx response fails without retrying`() = runTest {
        val transaction = succeededTransaction()
        server.enqueue(MockResponse().setResponseCode(400))

        val result = buildWorker(transaction.outTradeNo).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(TransactionState.SUCCEEDED, repository.findByOutTradeNo(transaction.outTradeNo)?.state)
    }

    @Test
    fun `a non-SUCCEEDED transaction is a no-op and makes no request`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)

        val result = buildWorker(draft.outTradeNo).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an unknown outTradeNo fails`() = runTest {
        val result = buildWorker("does-not-exist").doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }
}
