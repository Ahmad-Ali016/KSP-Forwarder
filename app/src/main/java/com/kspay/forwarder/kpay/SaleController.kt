package com.kspay.forwarder.kpay

import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val PAYMENT_TYPE_CARD = 1

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
    private val appId: String,
    private val appSecret: String,
    private val scope: CoroutineScope,
    /** Notified with the outTradeNo once a transaction reaches SUCCEEDED (real or simulated). */
    private val onSucceeded: suspend (String) -> Unit = {},
) {
    private val queryResponseAdapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(QueryResponse::class.java)

    suspend fun charge(payAmountCents: String, currency: String = "036", paymentType: Int = PAYMENT_TYPE_CARD): String {
        val draft = repository.createDraft(payAmountCents, currency, paymentType)
        scope.launch { runSale(draft) }
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
     * to abort before a sale has been sent. On success marks ABORTED; on rejection (e.g. KPay's
     * 2-in-1-background-mode caveat, or the sale completing in the meantime) records lastError and
     * leaves the transaction in its current state rather than falsely marking it ABORTED.
     */
    suspend fun abort(outTradeNo: String) {
        val transaction = repository.findByOutTradeNo(outTradeNo) ?: return
        if (transaction.state != TransactionState.POLLING) return
        try {
            val response = signedApi.close(CloseRequest(outTradeNo))
            if (response.isSuccess) {
                repository.updateState(transaction, TransactionState.ABORTED)
            } else {
                val message = "Abort failed: code=${response.code} message=${response.message}"
                repository.updateState(transaction.copy(lastError = message), transaction.state)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            repository.updateState(transaction.copy(lastError = "Abort failed: ${e.message}"), transaction.state)
        }
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
