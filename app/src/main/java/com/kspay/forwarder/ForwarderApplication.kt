package com.kspay.forwarder

import android.app.Application
import androidx.work.Configuration
import com.kspay.forwarder.di.AppContainer
import com.kspay.forwarder.di.DefaultAppContainer

class ForwarderApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(container.workerFactory).build()
}
