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
        val state = when (data.payResult) {
            PENDING_RESULT -> return null
            SUCCESS_RESULT -> TransactionState.SUCCEEDED
            else -> TransactionState.NON_SUCCESS
        }
        return repository.updateState(transaction.copy(rawSaleResultJson = adapter.toJson(data)), state)
    }
}
