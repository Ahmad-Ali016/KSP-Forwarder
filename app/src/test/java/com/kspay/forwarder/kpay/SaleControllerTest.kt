package com.kspay.forwarder.kpay

import androidx.room.Room
import com.kspay.forwarder.data.ForwarderDatabase
import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.OutTradeNoGenerator
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.security.KeyPairGenerator
import java.util.Base64

private class FakeWorkingKeyStore(initial: SignInResponse? = null) : WorkingKeyStore {
    private var current = initial
    override fun save(keys: SignInResponse) { current = keys }
    override fun get(): SignInResponse? = current
    override fun clear() { current = null }
}

/**
 * SaleController runs the sale+poll flow on its own background CoroutineScope by design (see
 * SaleController's KDoc) — a real scope here, not runTest's TestScope, so these tests exercise
 * genuine cross-thread completion the same way production does, verified via a bounded real-time
 * poll rather than advanceUntilIdle() (which only drives virtual time/TestDispatcher-queued work,
 * not real OkHttp I/O on a real scope).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SaleControllerTest {

    private lateinit var server: MockWebServer
    private lateinit var db: ForwarderDatabase
    private lateinit var repository: TransactionRepository
    private lateinit var unsignedApi: KposApi
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun generatePrivateKeyBase64() =
        Base64.getEncoder().encodeToString(
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private.encoded,
        )

    private fun signedApi(store: WorkingKeyStore) = KposClientFactory.create(
        baseUrl = server.url("/").toString(),
        client = OkHttpClient.Builder()
            .addInterceptor(
                SignedRequestInterceptor(
                    appId = "test-app-id",
                    appSecret = "test-app-secret",
                    workingKeyStore = store,
                    signIn = unsignedApi::signInWithFixedKeys,
                ),
            )
            .build(),
    )

    private fun controller(store: WorkingKeyStore, onSucceeded: suspend (String) -> Unit = {}) =
        SaleController(
            repository,
            unsignedApi,
            signedApi(store),
            store,
            "test-app-id",
            "test-app-secret",
            backgroundScope,
            onSucceeded,
        )

    private val nonTerminalStates =
        setOf(TransactionState.DRAFT, TransactionState.SALE_SENT, TransactionState.POLLING)

    private suspend fun awaitTerminal(outTradeNo: String, timeoutMs: Long = 5_000): LocalTransaction? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = repository.findByOutTradeNo(outTradeNo)
            if (current != null && current.state !in nonTerminalStates) return current
            withContext(Dispatchers.IO) { Thread.sleep(20) }
        }
        return repository.findByOutTradeNo(outTradeNo)
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ForwarderDatabase::class.java).build()
        repository = TransactionRepository(db.localTransactionDao(), OutTradeNoGenerator("dev01"))
        unsignedApi = KposClientFactory.create(baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun saleBody() = """{"code":10000,"data":{}}"""
    private fun querySucceededBody() = """{"code":10000,"data":{"outTradeNO":"OT","payResult":2,"payAmount":"000000000100"}}"""
    private fun signInBody(privateKey: String) =
        """{"code":10000,"data":{"platformPublicKey":"pub","appPrivateKey":"$privateKey"}}"""

    @Test
    fun `signs in when no working key exists, then sells and polls to SUCCEEDED`() = runTest {
        val store = FakeWorkingKeyStore()
        server.enqueue(MockResponse().setBody(signInBody(generatePrivateKeyBase64())))
        server.enqueue(MockResponse().setBody(saleBody()))
        server.enqueue(MockResponse().setBody(querySucceededBody()))

        val outTradeNo = controller(store).charge(payAmountCents = "000000000100")
        val persisted = awaitTerminal(outTradeNo)

        assertEquals(TransactionState.SUCCEEDED, persisted?.state)
        assertNotNull(store.get())
        assertEquals(3, server.requestCount) // sign-in + sale + query
        assertEquals("/v2/pos/sign", server.takeRequest().path)
    }

    @Test
    fun `skips sign-in when a working key already exists`() = runTest {
        val store = FakeWorkingKeyStore(SignInResponse("pub", generatePrivateKeyBase64()))
        server.enqueue(MockResponse().setBody(saleBody()))
        server.enqueue(MockResponse().setBody(querySucceededBody()))

        val outTradeNo = controller(store).charge(payAmountCents = "000000000100")
        val persisted = awaitTerminal(outTradeNo)

        assertEquals(TransactionState.SUCCEEDED, persisted?.state)
        assertEquals(2, server.requestCount) // sale + query only
        assertEquals("/v2/pos/sales", server.takeRequest().path)
    }

    @Test
    fun `a sign-in failure marks the transaction NON_SUCCESS with lastError instead of hanging`() = runTest {
        val store = FakeWorkingKeyStore()
        server.enqueue(MockResponse().setBody("""{"code":40001,"data":null,"message":"bad credentials"}"""))

        val outTradeNo = controller(store).charge(payAmountCents = "000000000100")
        val persisted = awaitTerminal(outTradeNo)

        assertEquals(TransactionState.NON_SUCCESS, persisted?.state)
        assertNotNull(persisted?.lastError)
        assertNull(persisted?.rawSaleResultJson)
        assertNull(store.get())
    }

    @Test
    fun `charge returns the outTradeNo immediately without waiting for the sale to finish`() = runTest {
        val store = FakeWorkingKeyStore(SignInResponse("pub", generatePrivateKeyBase64()))
        server.enqueue(MockResponse().setBody(saleBody()))
        server.enqueue(MockResponse().setBody(querySucceededBody()))

        val outTradeNo = controller(store).charge(payAmountCents = "000000000100")

        val justAfterCharge = repository.findByOutTradeNo(outTradeNo)
        assertEquals(TransactionState.DRAFT, justAfterCharge?.state)
    }

    @Test
    fun `onSucceeded fires once with the outTradeNo when a real sale reaches SUCCEEDED`() = runTest {
        val store = FakeWorkingKeyStore(SignInResponse("pub", generatePrivateKeyBase64()))
        server.enqueue(MockResponse().setBody(saleBody()))
        server.enqueue(MockResponse().setBody(querySucceededBody()))
        val notified = mutableListOf<String>()

        val outTradeNo = controller(store, onSucceeded = { notified.add(it) }).charge(payAmountCents = "000000000100")
        awaitTerminal(outTradeNo)

        assertEquals(listOf(outTradeNo), notified)
    }

    @Test
    fun `onSucceeded is not called when the sale ends NON_SUCCESS`() = runTest {
        val store = FakeWorkingKeyStore()
        server.enqueue(MockResponse().setBody("""{"code":40001,"data":null,"message":"bad credentials"}"""))
        val notified = mutableListOf<String>()

        val outTradeNo = controller(store, onSucceeded = { notified.add(it) }).charge(payAmountCents = "000000000100")
        awaitTerminal(outTradeNo)

        assertEquals(emptyList<String>(), notified)
    }

    @Test
    fun `simulateSuccess fabricates a SUCCEEDED transaction without any KPOS network calls`() = runTest {
        val store = FakeWorkingKeyStore()
        val notified = mutableListOf<String>()

        val outTradeNo = controller(store, onSucceeded = { notified.add(it) }).simulateSuccess(payAmountCents = "000000000100")

        val persisted = repository.findByOutTradeNo(outTradeNo)
        assertEquals(TransactionState.SUCCEEDED, persisted?.state)
        assertNotNull(persisted?.rawSaleResultJson)
        assertEquals(listOf(outTradeNo), notified)
        assertEquals(0, server.requestCount)
        assertNull(store.get())
    }
}
