package com.kspay.forwarder.kpay

import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
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
    suspend fun execute(transaction: LocalTransaction): LocalTransaction {
        val polling = repository.updateState(transaction, TransactionState.POLLING)

        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(INTERVAL)

            val data = api.query(polling.outTradeNo).data ?: return@repeat
            QueryResultFinalizer.apply(repository, polling, data)?.let { return it }
        }

        return polling
    }

    private companion object {
        val INTERVAL = 5.seconds
        const val MAX_ATTEMPTS = 18 // ~90s budget at a 5s interval
    }
}
