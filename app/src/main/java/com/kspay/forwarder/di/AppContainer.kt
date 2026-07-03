package com.kspay.forwarder.di

import android.content.Context
import android.provider.Settings
import androidx.room.Room
import com.kspay.forwarder.data.ForwarderDatabase
import com.kspay.forwarder.data.OutTradeNoGenerator
import com.kspay.forwarder.data.TransactionRepository

/**
 * Root dependency container (manual DI, no Hilt/Dagger — keeps the APK lean for KPay vetting).
 * Populated incrementally as crypto/, kpay/, data/, net/, sync/ are built in later phases.
 */
interface AppContainer {
    val transactionRepository: TransactionRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val database = Room.databaseBuilder(context, ForwarderDatabase::class.java, "forwarder.db").build()

    private val devicePrefix =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device"

    override val transactionRepository =
        TransactionRepository(database.localTransactionDao(), OutTradeNoGenerator(devicePrefix))
}
