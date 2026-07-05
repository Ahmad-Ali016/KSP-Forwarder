package com.kspay.forwarder.kpay

import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Turns one /v2/pos/query response into a state transition, storing the raw result JSON.
 * Shared by PollUseCase (its own poll loop) and ReconciliationWorker (one attempt per pass).
 * Returns null if the result is still pending (payResult 1) — the caller decides what to do.
 */
object QueryResultFinalizer {

    const val SUCCESS_RESULT = 2
    const val PENDING_RESULT = 1

    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(QueryResponse::class.java)

    suspend fun apply(
        repository: TransactionRepository,
        transaction: LocalTransaction,
        data: QueryResponse,
    ): LocalTransaction? {
        val state = when {
            data.payResult == PENDING_RESULT -> return null
            data.payResult == SUCCESS_RESULT && hasRequiredAmounts(data) -> TransactionState.SUCCEEDED
            data.payResult == SUCCESS_RESULT -> TransactionState.ANOMALY
            else -> TransactionState.NON_SUCCESS
        }
        val updated = transaction.copy(rawSaleResultJson = adapter.toJson(data))
        return repository.updateState(
            if (state == TransactionState.ANOMALY) {
                updated.copy(
                    lastError = "KPay reported payResult=2 (success) but payAmount/orderAmount was " +
                        "missing from the query result -- held for manual review, not forwarded",
                )
            } else {
                updated
            },
            state,
        )
    }

    /** KSPay's ingest schema requires both present on every request -- see TransactionState.ANOMALY. */
    private fun hasRequiredAmounts(data: QueryResponse): Boolean =
        !data.payAmount.isNullOrBlank() && !data.orderAmount.isNullOrBlank()
}
