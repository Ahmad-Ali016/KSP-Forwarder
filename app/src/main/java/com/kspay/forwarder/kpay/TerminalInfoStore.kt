package com.kspay.forwarder.kpay

import android.content.Context

interface TerminalInfoStore {
    fun getTid(): String?
    fun saveTid(tid: String)
}

/**
 * Plain (unencrypted) SharedPreferences-backed. Unlike WorkingKeyStore, the TID is not sensitive
 * -- it's already printed openly on paper receipts -- so this doesn't need AndroidKeyStore/
 * EncryptedSharedPreferences, and can be constructed eagerly (Robolectric can simulate plain
 * SharedPreferences fine; see CLAUDE.md's testing-strategy notes on why WorkingKeyStore can't be).
 */
class DefaultTerminalInfoStore(context: Context) : TerminalInfoStore {
    private val prefs = context.getSharedPreferences("terminal_info", Context.MODE_PRIVATE)

    override fun getTid(): String? = prefs.getString(KEY_TID, null)

    override fun saveTid(tid: String) {
        prefs.edit().putString(KEY_TID, tid).apply()
    }

    private companion object {
        const val KEY_TID = "tid"
    }
}
