package com.kspay.forwarder.kpay

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kspay.forwarder.BuildConfig

interface AdminPasswordStore {
    fun verify(password: String): Boolean
}

/**
 * General-purpose admin password, reusable for any future admin-only feature (not just TID
 * locking). EncryptedSharedPreferences-backed, same security posture as DefaultWorkingKeyStore --
 * a password is sensitive, unlike TerminalInfoStore's TID.
 *
 * Deliberately fixed -- there is no in-app way to change it (see BUILD_PROGRESS.md's 2026-07-20
 * "TID admin password" entry). Without a backend/account system there's no safe way to recover a
 * forgotten changed password without wiping the whole app's local data (losing any
 * not-yet-forwarded transactions in the process), so the password is a build-time constant
 * instead, same trust model as KPAY_APP_ID/KPAY_APP_SECRET -- changing it is a deliberate
 * local.properties + rebuild + redeploy action, not something adjustable from the running app.
 * BuildConfig.ADMIN_DEFAULT_PASSWORD is only a seed: on the first-ever verify() call (nothing
 * persisted yet), the default is written to encrypted storage and used for that check; every
 * check after that only ever looks at the persisted value, so changing the BuildConfig default
 * post-launch has no effect on an already-provisioned device (a fresh install is required).
 */
class DefaultAdminPasswordStore(context: Context) : AdminPasswordStore {

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

    override fun verify(password: String): Boolean = password == currentPassword()

    private fun currentPassword(): String {
        val stored = prefs.getString(KEY_PASSWORD, null)
        if (stored != null) return stored
        prefs.edit { putString(KEY_PASSWORD, BuildConfig.ADMIN_DEFAULT_PASSWORD) }
        return BuildConfig.ADMIN_DEFAULT_PASSWORD
    }

    private companion object {
        const val PREFS_NAME = "admin_password"
        const val KEY_PASSWORD = "password"
    }
}
