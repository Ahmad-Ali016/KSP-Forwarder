package com.kspay.forwarder.sync

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val RECONCILIATION_WORK_NAME = "reconciliation"
private const val RECONCILIATION_INTERVAL_MINUTES = 15L

/**
 * Schedules the periodic reconciliation safety net if it isn't already scheduled. Safe to call
 * repeatedly (e.g. every Charge tap) -- ExistingPeriodicWorkPolicy.KEEP makes every call after
 * the first a cheap no-op rather than restarting the interval clock.
 */
fun WorkManager.scheduleReconciliation() {
    val request = PeriodicWorkRequestBuilder<ReconciliationWorker>(
        RECONCILIATION_INTERVAL_MINUTES, TimeUnit.MINUTES,
    ).build()
    enqueueUniquePeriodicWork(RECONCILIATION_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}
