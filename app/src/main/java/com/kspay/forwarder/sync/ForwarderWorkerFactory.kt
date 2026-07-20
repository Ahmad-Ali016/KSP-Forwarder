package com.kspay.forwarder.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.kpay.KposApi
import com.kspay.forwarder.kpay.TerminalInfoStore
import com.kspay.forwarder.net.KspayApi

class ForwarderWorkerFactory(
    private val repository: TransactionRepository,
    private val kspayApi: KspayApi,
    private val kposApi: KposApi,
    private val appId: String,
    private val forwarderVersion: String,
    private val deviceToken: String,
    private val terminalInfoStore: TerminalInfoStore,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        ForwardWorker::class.java.name ->
            ForwardWorker(appContext, workerParameters, repository, kspayApi, appId, forwarderVersion, deviceToken, terminalInfoStore)
        ReconciliationWorker::class.java.name ->
            ReconciliationWorker(appContext, workerParameters, repository, kposApi)
        else -> null
    }
}
