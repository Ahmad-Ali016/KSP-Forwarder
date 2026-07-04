package com.kspay.forwarder

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented, not Robolectric: AppContainer.workerFactory's lazy chain builds signedKposApi,
 * which needs the real AndroidKeyStore-backed WorkingKeyStore that Robolectric cannot simulate.
 * Guards the WorkManager on-demand-init wiring: ForwarderApplication implements
 * Configuration.Provider (backed by AppContainer.workerFactory), and the manifest disables the
 * default androidx-startup initializer to match -- if either half is missing, real
 * WorkManager.getInstance() throws at first use instead of this test catching it here.
 */
@RunWith(AndroidJUnit4::class)
class ForwarderApplicationTest {

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ForwarderApplication

    @Test
    fun workManagerConfigurationUsesTheContainersWorkerFactory() {
        val config = app.workManagerConfiguration

        assertSame(app.container.workerFactory, config.workerFactory)
    }

    @Test
    fun workManagerOnDemandInitializationSucceedsViaTheAppsConfigurationProvider() {
        val workManager = WorkManager.getInstance(app)

        assertNotNull(workManager)
    }
}
