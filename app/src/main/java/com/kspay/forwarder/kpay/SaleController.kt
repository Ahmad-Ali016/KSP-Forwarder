package com.kspay.forwarder.kpay

import android.util.Log
import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap

private const val PAYMENT_TYPE_CARD = 1
private const val TAG = "SaleController"

/**
 * Orchestrates a Charge tap end to end: persists the DRAFT, then runs sign-in (only if no
 * working key exists yet — KPOS sign-in is otherwise purely reactive, on a 40004 mid-call) ->
 * SaleUseCase -> PollUseCase in the background, returning the outTradeNo immediately so the
 * caller can navigate to InProgress without waiting for the sale to finish.
 *
 * Runs on the given scope, not any one screen's ViewModel, so navigating away or backgrounding
 * the app doesn't cancel an in-flight sale — InProgressScreen only ever observes the Room Flow
 * this writes to, regardless of who's still watching.
 */
class SaleController(
    private val repository: TransactionRepository,
    private val unsignedApi: KposApi,
    private val signedApi: KposApi,
    private val workingKeyStore: WorkingKeyStore,
    private val terminalInfoStore: TerminalInfoStore,
    private val appId: String,
    private val appSecret: String,
    private val scope: CoroutineScope,
    /** Notified with the outTradeNo once a transaction reaches SUCCEEDED (real or simulated). */
    private val onSucceeded: suspend (String) -> Unit = {},
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val queryResponseAdapter = moshi.adapter(QueryResponse::class.java)
    private val printRequestAdapter = moshi.adapter(PrintRequest::class.java)

    // Tracks the background runSale() job per outTradeNo so abort() can stop it before writing
    // ABORTED -- otherwise the still-running PollUseCase loop can win the race and overwrite
    // abort's state with whatever KPOS's next query result happens to finalize as.
    private val activeSales = ConcurrentHashMap<String, Job>()

    suspend fun charge(payAmountCents: String, currency: String = "036", paymentType: Int = PAYMENT_TYPE_CARD): String {
        val draft = repository.createDraft(payAmountCents, currency, paymentType)
        val job = scope.launch { runSale(draft) }
        activeSales[draft.outTradeNo] = job
        job.invokeOnCompletion { activeSales.remove(draft.outTradeNo) }
        return draft.outTradeNo
    }

    private suspend fun runSale(draft: LocalTransaction) {
        var current = draft
        try {
            ensureSignedIn()
            current = SaleUseCase(signedApi, repository).execute(current)
            current = PollUseCase(signedApi, repository).execute(current)
            if (current.state == TransactionState.SUCCEEDED) onSucceeded(current.outTradeNo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            repository.updateState(current.copy(lastError = e.message), TransactionState.NON_SUCCESS)
        }
    }

    /**
     * Debug-only: aborts a still-pending (POLLING) sale via KPay's close endpoint. No-ops for any
     * other state -- KPay itself rejects close on an already-completed sale, and there is nothing
     * to abort before a sale has been sent. On success marks ABORTED.
     *
     * Stops the in-flight runSale() job (which owns the concurrent PollUseCase loop) *before*
     * touching the transaction -- otherwise that loop can keep polling after we've decided to
     * abort and overwrite our result with whatever it next resolves to. Since that also means
     * nothing is polling anymore, a rejected close() (observed live: code 20010, "already
     * completed" -- these test sales tend to resolve almost immediately) triggers one manual
     * follow-up query() to fetch and record the real result via QueryResultFinalizer, rather than
     * leaving the transaction stuck in POLLING with nothing left to resolve it.
     */
    suspend fun abort(outTradeNo: String) {
        val transaction = repository.findByOutTradeNo(outTradeNo) ?: return
        if (transaction.state != TransactionState.POLLING) return
        Log.d(TAG, "abort($outTradeNo): cancelling active poll job")
        activeSales[outTradeNo]?.cancelAndJoin()
        Log.d(TAG, "abort($outTradeNo): poll job cancelled")

        // Re-read: the poll loop may have already reached a terminal state in the moment before
        // cancellation took effect -- if so, that result is real and must not be overwritten.
        val current = repository.findByOutTradeNo(outTradeNo) ?: return
        if (current.state != TransactionState.POLLING) return

        try {
            Log.d(TAG, "abort($outTradeNo): calling close()")
            val response = signedApi.close(CloseRequest(outTradeNo))
            Log.d(TAG, "abort($outTradeNo): close() responded code=${response.code} message=${response.message}")
            if (response.isSuccess) {
                repository.updateState(current, TransactionState.ABORTED)
                return
            }

            // KPay rejected the close -- most commonly because the sale already reached a final
            // result (e.g. code 20010, "already completed") before our close() request arrived.
            // We already stopped our own poll loop above, so nothing else will ever discover that
            // real result -- query once ourselves instead of leaving the transaction stuck in
            // POLLING forever.
            Log.d(TAG, "abort($outTradeNo): close() rejected, querying for the real result")
            val queried = signedApi.query(outTradeNo).data
            val finalized = queried?.let { QueryResultFinalizer.apply(repository, current, it) }
            Log.d(TAG, "abort($outTradeNo): follow-up query resolved to ${finalized?.state}")
            if (finalized != null) {
                if (finalized.state == TransactionState.SUCCEEDED) onSucceeded(finalized.outTradeNo)
            } else {
                val message = "Abort failed: code=${response.code} message=${response.message}"
                repository.updateState(current.copy(lastError = message), current.state)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "abort($outTradeNo): close() threw ${e.message}", e)
            repository.updateState(current.copy(lastError = "Abort failed: ${e.message}"), current.state)
        }
    }

    /**
     * Prints a passenger receipt for a completed transaction via KPay's custom-print endpoint.
     * KPOS never auto-prints for transactions the forwarder drives -- includeReceipt=true on
     * query() (required for deviceID/commitTime) suppresses KPOS's own auto-print, per KPay's
     * docs -- so this is the only way a passenger gets a receipt. Fetches and caches the
     * terminal ID (TID) once via querySettlement(), since KPay only returns it there, never
     * per-transaction. Failures are recorded as lastError rather than thrown, so a failed print
     * doesn't disrupt the transaction's own state -- mirrors abort()'s error handling.
     */
    suspend fun printReceipt(outTradeNo: String) {
        val transaction = repository.findByOutTradeNo(outTradeNo) ?: return
        val result = transaction.rawSaleResultJson?.let(queryResponseAdapter::fromJson) ?: return
        try {
            val tid = terminalInfoStore.getTid() ?: fetchAndCacheTid()
            val printRequest = PrintRequest(ReceiptFormatter.buildSteps(transaction, result, tid))
            Log.d(TAG, "printReceipt($outTradeNo): sending ${printRequestAdapter.toJson(printRequest)}")
            val response = signedApi.print(printRequest)
            if (!response.isSuccess) {
                Log.w(TAG, "printReceipt($outTradeNo): print() rejected code=${response.code} message=${response.message}")
                val message = "Print failed: code=${response.code} message=${response.message}"
                repository.updateState(transaction.copy(lastError = message), transaction.state)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // HttpException.message is just "HTTP 500 Internal Server Error" -- the real
            // diagnostic (if KPOS's server sent one) is in the response body Retrofit otherwise
            // discards on a non-2xx.
            val errorBody = (e as? HttpException)?.let { runCatching { it.response()?.errorBody()?.string() }.getOrNull() }
            Log.w(TAG, "printReceipt($outTradeNo): print() threw ${e.message} body=$errorBody", e)
            repository.updateState(transaction.copy(lastError = "Print failed: ${e.message}"), transaction.state)
        }
    }

    private suspend fun fetchAndCacheTid(): String? {
        val tid = signedApi.querySettlement().extra?.kpayTerminalNo
        if (tid != null) terminalInfoStore.saveTid(tid)
        return tid
    }

    private suspend fun ensureSignedIn() {
        if (workingKeyStore.get() != null) return
        val response = unsignedApi.signInWithFixedKeys(appId, appSecret)
        check(response.isSuccess) { "Sign-in failed: code=${response.code} message=${response.message}" }
        workingKeyStore.save(response.data ?: error("Sign-in succeeded with no data"))
    }

    /**
     * Debug-only: fabricates a realistic SUCCEEDED transaction without calling KPOS at all, so
     * ForwardWorker's normal forward path can be exercised end to end before a physical terminal
     * exists. Never calls into KPay's LAN API or the working-key/sign-in path.
     */
    suspend fun simulateSuccess(payAmountCents: String, currency: String = "036", paymentType: Int = PAYMENT_TYPE_CARD): String {
        val draft = repository.createDraft(payAmountCents, currency, paymentType)
        val fakeResult = QueryResponse(
            outTradeNO = draft.outTradeNo,
            payResult = QueryResultFinalizer.SUCCESS_RESULT,
            transactionNo = "SIM-${draft.outTradeNo}",
            refNo = "SIM-REF-${draft.outTradeNo}",
            payAmount = payAmountCents,
            tipsAmount = "000000000000",
            payCurrency = currency,
            transactionType = paymentType,
            payMethod = paymentType,
            discountAmount = "000000000000",
            orderAmount = payAmountCents,
            surchargeAmount = "000000000000",
            kpayOutTradeNo = "SIM-KPAY-${draft.outTradeNo}",
            cardNo = "123456******1234",
            authCode = "SIM000",
            batchNo = "000001",
            traceNo = "000001",
            commitTime = System.currentTimeMillis(),
            appVersion = "simulated",
            terminalType = "simulated",
            deviceID = "simulated-device",
        )
        val succeeded = repository.updateState(
            draft.copy(rawSaleResultJson = queryResponseAdapter.toJson(fakeResult)),
            TransactionState.SUCCEEDED,
        )
        onSucceeded(succeeded.outTradeNo)
        return succeeded.outTradeNo
    }
}
