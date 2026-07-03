package com.kspay.forwarder

import android.app.Application
import com.kspay.forwarder.di.AppContainer
import com.kspay.forwarder.di.DefaultAppContainer

class ForwarderApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
