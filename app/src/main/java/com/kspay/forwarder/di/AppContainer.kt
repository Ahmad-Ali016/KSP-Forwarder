package com.kspay.forwarder.di

import android.content.Context
import android.provider.Settings
import androidx.room.Room
import com.kspay.forwarder.BuildConfig
import com.kspay.forwarder.data.ForwarderDatabase
import com.kspay.forwarder.data.OutTradeNoGenerator
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.kpay.DefaultWorkingKeyStore
import com.kspay.forwarder.kpay.KposClientFactory
import com.kspay.forwarder.kpay.SaleController
import com.kspay.forwarder.kpay.SignedRequestInterceptor
import com.kspay.forwarder.kpay.signInWithFixedKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Root dependency container (manual DI, no Hilt/Dagger — keeps the APK lean for KPay vetting).
 * Populated incrementally as crypto/, kpay/, data/, net/, sync/ are built in later phases.
 */
interface AppContainer {
    val transactionRepository: TransactionRepository
    val saleController: SaleController
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val database = Room.databaseBuilder(context, ForwarderDatabase::class.java, "forwarder.db").build()

    private val devicePrefix =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device"

    override val transactionRepository =
        TransactionRepository(database.localTransactionDao(), OutTradeNoGenerator(devicePrefix))

    // Lazy: DefaultWorkingKeyStore touches AndroidKeyStore, which Robolectric can't simulate
    // (see CLAUDE.md's testing-strategy notes) — eagerly building it here would crash every
    // Robolectric test that boots this Application via RuntimeEnvironment.getApplication(),
    // not just ones that actually exercise a sale.
    private val workingKeyStore by lazy { DefaultWorkingKeyStore(context) }
    private val unsignedKposApi by lazy { KposClientFactory.create() }
    private val signedKposApi by lazy {
        KposClientFactory.create(
            client = OkHttpClient.Builder()
                .addInterceptor(
                    SignedRequestInterceptor(
                        appId = BuildConfig.KPAY_APP_ID,
                        appSecret = BuildConfig.KPAY_APP_SECRET,
                        workingKeyStore = workingKeyStore,
                        signIn = unsignedKposApi::signInWithFixedKeys,
                    ),
                )
                .build(),
        )
    }

    // Application-lifetime, not tied to any screen's ViewModel — a sale/poll in flight must
    // survive navigating away from InProgress or the app backgrounding.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val saleController by lazy {
        SaleController(
            repository = transactionRepository,
            unsignedApi = unsignedKposApi,
            signedApi = signedKposApi,
            workingKeyStore = workingKeyStore,
            appId = BuildConfig.KPAY_APP_ID,
            appSecret = BuildConfig.KPAY_APP_SECRET,
            scope = applicationScope,
        )
    }
}
