package com.kspay.forwarder.sync

import android.content.Context
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ReconciliationSchedulingTest {

    // The test WorkManager runs enqueued work synchronously by default -- ReconciliationWorker
    // itself needs real repository/api args, so a trivial no-op factory lets the periodic run
    // complete (and settle back to ENQUEUED) without needing that setup for a scheduling-only test.
    private val noOpWorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = object : CoroutineWorker(appContext, workerParameters) {
            override suspend fun doWork(): Result = Result.success()
        }
    }

    @Before
    fun setUp() {
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(noOpWorkerFactory).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(RuntimeEnvironment.getApplication(), config)
    }

    @Test
    fun `scheduleReconciliation enqueues exactly one ENQUEUED periodic work item under the expected name`() {
        WorkManager.getInstance(RuntimeEnvironment.getApplication()).scheduleReconciliation()

        val workInfos = WorkManager.getInstance(RuntimeEnvironment.getApplication())
            .getWorkInfosForUniqueWork("reconciliation").get()

        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.first().state)
    }

    @Test
    fun `calling scheduleReconciliation twice does not enqueue a second periodic work item`() {
        val workManager = WorkManager.getInstance(RuntimeEnvironment.getApplication())
        workManager.scheduleReconciliation()
        workManager.scheduleReconciliation()

        val workInfos = workManager.getWorkInfosForUniqueWork("reconciliation").get()

        assertEquals(1, workInfos.size)
    }
}
