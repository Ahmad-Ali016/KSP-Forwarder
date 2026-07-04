package com.kspay.forwarder.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import com.kspay.forwarder.kpay.KposApi
import com.kspay.forwarder.kpay.QueryResultFinalizer
import java.io.IOException

/**
 * Periodic safety net (~15 min in production wiring). Re-queries transactions stuck in
 * POLLING one attempt at a time (PollUseCase's own 90s budget already tried harder), and
 * re-enqueues ForwardWorker for any SUCCEEDED transaction that never got forwarded.
 */
class ReconciliationWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: TransactionRepository,
    private val api: KposApi,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        reconcileStuckPolling()
        reconcileUnforwardedSucceeded()
        return Result.success()
    }

    private suspend fun reconcileStuckPolling() {
        val workManager = WorkManager.getInstance(applicationContext)
        for (transaction in repository.findByState(TransactionState.POLLING)) {
            val data = try {
                api.query(transaction.outTradeNo).data
            } catch (e: IOException) {
                null // try again next reconciliation pass
            } ?: continue
            val finalized: LocalTransaction? = QueryResultFinalizer.apply(repository, transaction, data)
            if (finalized?.state == TransactionState.SUCCEEDED) workManager.enqueueForward(finalized.outTradeNo)
        }
    }

    private suspend fun reconcileUnforwardedSucceeded() {
        val workManager = WorkManager.getInstance(applicationContext)
        for (transaction in repository.findByState(TransactionState.SUCCEEDED)) {
            workManager.enqueueForward(transaction.outTradeNo)
        }
    }
}
