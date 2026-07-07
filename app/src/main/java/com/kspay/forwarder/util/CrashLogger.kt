package com.kspay.forwarder.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions to a local, app-private file -- no external crash-reporting SDK,
 * per V1 scope. Always chains to whatever handler was previously installed (the OS default) so a
 * real crash still surfaces/terminates normally; this only records it locally first.
 */
object CrashLogger {
    private const val DIR_NAME = "crash_logs"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(appContext, thread, throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        File(dir, "crash-$timestamp.txt").writeText(
            "Thread: ${thread.name}\n${throwable.stackTraceToString()}",
        )
    }
}
