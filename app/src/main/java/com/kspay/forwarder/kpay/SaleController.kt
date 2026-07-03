package com.kspay.forwarder.kpay

import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
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
) {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            repository.updateState(current.copy(lastError = e.message), TransactionState.NON_SUCCESS)
        }
    }

    private suspend fun ensureSignedIn() {
        if (workingKeyStore.get() != null) return
        val response = unsignedApi.signInWithFixedKeys(appId, appSecret)
        check(response.isSuccess) { "Sign-in failed: code=${response.code} message=${response.message}" }
        workingKeyStore.save(response.data ?: error("Sign-in succeeded with no data"))
    }
}
