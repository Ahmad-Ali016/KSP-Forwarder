package com.kspay.forwarder.sync

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/** Enqueues (or no-ops if already enqueued/running) a ForwardWorker run for one transaction. */
fun WorkManager.enqueueForward(outTradeNo: String) {
    val request = OneTimeWorkRequestBuilder<ForwardWorker>()
        .setInputData(workDataOf(ForwardWorker.KEY_OUT_TRADE_NO to outTradeNo))
        .build()
    enqueueUniqueWork("forward-$outTradeNo", ExistingWorkPolicy.KEEP, request)
}
