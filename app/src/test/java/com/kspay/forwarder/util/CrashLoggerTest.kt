package com.kspay.forwarder.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CrashLoggerTest {

    @Test
    fun `writes a local crash log and still chains to the previous handler`() {
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "crash_logs").deleteRecursively()

        var previousHandlerInvoked = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> previousHandlerInvoked = true }
        CrashLogger.install(context)

        val thread = Thread.currentThread()
        val error = RuntimeException("boom")
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(thread, error)

        assertTrue("expected the previously-installed handler to still run", previousHandlerInvoked)

        val files = File(context.filesDir, "crash_logs").listFiles()
        assertEquals(1, files?.size)
        val content = files!![0].readText()
        assertTrue(content.contains(thread.name))
        assertTrue(content.contains("boom"))
    }
}
