package com.kspay.forwarder.kpay

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds the KPay working key pair from /v2/pos/sign in memory for fast access, backed by
 * EncryptedSharedPreferences so it survives process death without a fresh sign-in.
 */
class DefaultWorkingKeyStore(context: Context) : WorkingKeyStore {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Volatile
    private var cached: SignInResponse? = null

    override fun save(keys: SignInResponse) {
        cached = keys
        prefs.edit()
            .putString(KEY_PLATFORM_PUBLIC_KEY, keys.platformPublicKey)
            .putString(KEY_APP_PRIVATE_KEY, keys.appPrivateKey)
            .apply()
    }

    override fun get(): SignInResponse? {
        cached?.let { return it }
        val platformPublicKey = prefs.getString(KEY_PLATFORM_PUBLIC_KEY, null)
        val appPrivateKey = prefs.getString(KEY_APP_PRIVATE_KEY, null)
        if (platformPublicKey == null || appPrivateKey == null) return null
        return SignInResponse(platformPublicKey, appPrivateKey).also { cached = it }
    }

    override fun clear() {
        cached = null
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "kpay_working_key"
        const val KEY_PLATFORM_PUBLIC_KEY = "platform_public_key"
        const val KEY_APP_PRIVATE_KEY = "app_private_key"
    }
}
