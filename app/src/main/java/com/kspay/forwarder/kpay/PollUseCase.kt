package com.kspay.forwarder.kpay

import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * GET /v2/pos/query every 5s under a ~90s budget (18 attempts, well above KPOS's 800ms
 * same-interface floor). payResult 2 = SUCCEEDED (raw result stored); any other final result
 * = NON_SUCCESS; budget exceeded leaves the transaction in POLLING for reconciliation.
 */
class PollUseCase(
    private val api: KposApi,
    private val repository: TransactionRepository,
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    suspend fun execute(transaction: LocalTransaction): LocalTransaction {
        val polling = repository.updateState(transaction, TransactionState.POLLING)

        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(INTERVAL)

            val response = api.query(polling.outTradeNo)
            when (response.data?.payResult) {
                SUCCESS_RESULT -> return finalize(polling, TransactionState.SUCCEEDED, response.data)
                null, PENDING_RESULT -> Unit // no final result yet, keep polling
                else -> return finalize(polling, TransactionState.NON_SUCCESS, response.data)
            }
        }

        return polling
    }

    private suspend fun finalize(
        transaction: LocalTransaction,
        state: TransactionState,
        data: QueryResponse?,
    ): LocalTransaction {
        val rawJson = data?.let { moshi.adapter(QueryResponse::class.java).toJson(it) }
        return repository.updateState(transaction.copy(rawSaleResultJson = rawJson), state)
    }

    private companion object {
        val INTERVAL = 5.seconds
        const val MAX_ATTEMPTS = 18 // ~90s budget at a 5s interval
        const val SUCCESS_RESULT = 2
        const val PENDING_RESULT = 1
    }
}
