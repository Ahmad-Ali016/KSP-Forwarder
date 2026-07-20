package com.kspay.forwarder.sync

import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.kspay.forwarder.data.ForwarderDatabase
import com.kspay.forwarder.data.OutTradeNoGenerator
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import com.kspay.forwarder.kpay.KposClientFactory
import com.kspay.forwarder.kpay.TerminalInfoStore
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

@RunWith(RobolectricTestRunner::class)
class ReconciliationWorkerTest {

    private lateinit var server: MockWebServer
    private lateinit var db: ForwarderDatabase
    private lateinit var repository: TransactionRepository
    private lateinit var context: Context
    private lateinit var factory: ForwarderWorkerFactory

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, ForwarderDatabase::class.java).build()
        repository = TransactionRepository(db.localTransactionDao(), OutTradeNoGenerator("dev01"))
        factory = ForwarderWorkerFactory(
            repository,
            KspayClientFactory.create(baseUrl = server.url("/").toString()),
            KposClientFactory.create(baseUrl = server.url("/").toString()),
            appId = "202xxx",
            forwarderVersion = "1.0",
            deviceToken = "token",
            terminalInfoStore = object : TerminalInfoStore {
                override fun getTid(): String? = "00000524"
                override fun saveTid(tid: String) {}
            },
        )

        // The factory must be registered here too: ReconciliationWorker enqueues a real
        // ForwardWorker through the actual WorkManager singleton, which otherwise falls back
        // to reflection-based construction and fails immediately (no no-arg constructor).
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun buildWorker(): ReconciliationWorker =
        TestListenableWorkerBuilder<ReconciliationWorker>(context)
            .setWorkerFactory(factory)
            .build()

    @Test
    fun `a stuck POLLING transaction that now shows success moves to SUCCEEDED`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        val polling = repository.updateState(draft, TransactionState.POLLING)
        server.enqueue(
            MockResponse().setBody(
                """{"code":10000,"data":{"outTradeNO":"${polling.outTradeNo}","payResult":2,
                   |"payAmount":"000000000100","orderAmount":"000000000100"}}""".trimMargin(),
            ),
        )

        buildWorker().doWork()

        assertEquals(TransactionState.SUCCEEDED, repository.findByOutTradeNo(polling.outTradeNo)?.state)
    }

    @Test
    fun `a stuck POLLING transaction that now shows success but is missing payAmount moves to ANOMALY, not SUCCEEDED`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        val polling = repository.updateState(draft, TransactionState.POLLING)
        server.enqueue(
            MockResponse().setBody("""{"code":10000,"data":{"outTradeNO":"${polling.outTradeNo}","payResult":2}}"""),
        )

        buildWorker().doWork()

        assertEquals(TransactionState.ANOMALY, repository.findByOutTradeNo(polling.outTradeNo)?.state)
        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("forward-${polling.outTradeNo}").get()
        assertEquals(0, workInfos.size)
    }

    @Test
    fun `a stuck POLLING transaction still pending stays in POLLING`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        val polling = repository.updateState(draft, TransactionState.POLLING)
        server.enqueue(MockResponse().setBody("""{"code":10000,"data":{"outTradeNO":"${polling.outTradeNo}","payResult":1}}"""))

        buildWorker().doWork()

        assertEquals(TransactionState.POLLING, repository.findByOutTradeNo(polling.outTradeNo)?.state)
    }

    @Test
    fun `an unforwarded SUCCEEDED transaction gets a ForwardWorker enqueued`() = runTest {
        val draft = repository.createDraft(payAmountCents = "000000000100", currency = "036", paymentType = 1)
        repository.updateState(
            draft.copy(rawSaleResultJson = """{"outTradeNO":"${draft.outTradeNo}","payResult":2}"""),
            TransactionState.SUCCEEDED,
        )
        server.enqueue(MockResponse().setResponseCode(200)) // the enqueued ForwardWorker runs synchronously

        buildWorker().doWork()

        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("forward-${draft.outTradeNo}").get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos.first().state != WorkInfo.State.FAILED)
    }
}
