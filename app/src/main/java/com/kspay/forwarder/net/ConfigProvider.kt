package com.kspay.forwarder.net

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.featureConfigDataStore by preferencesDataStore(name = "feature_config")

/**
 * Fetches feature config from the KSPay backend and persists it to DataStore, so config
 * toggles (refund/void/poll interval) survive a restart and the app keeps working with the
 * last-known (or safe-default) values if the backend is unreachable.
 */
class ConfigProvider(private val context: Context, private val api: ConfigApi) {

    /** Fetches fresh config and persists it. On failure, returns whatever is already cached. */
    suspend fun refresh(deviceToken: String): FeatureConfig {
        val fetched = try {
            api.fetchConfig(deviceToken)
        } catch (e: IOException) {
            return current()
        }
        persist(fetched)
        return fetched
    }

    /** The last persisted config, or FeatureConfig.SAFE_DEFAULTS if nothing has been fetched yet. */
    suspend fun current(): FeatureConfig {
        val prefs = context.featureConfigDataStore.data.first()
        return FeatureConfig(
            enableSale = prefs[KEY_ENABLE_SALE] ?: FeatureConfig.SAFE_DEFAULTS.enableSale,
            enableAbort = prefs[KEY_ENABLE_ABORT] ?: FeatureConfig.SAFE_DEFAULTS.enableAbort,
            enableVoid = prefs[KEY_ENABLE_VOID] ?: FeatureConfig.SAFE_DEFAULTS.enableVoid,
            enableRefund = prefs[KEY_ENABLE_REFUND] ?: FeatureConfig.SAFE_DEFAULTS.enableRefund,
            pollIntervalSeconds = prefs[KEY_POLL_INTERVAL] ?: FeatureConfig.SAFE_DEFAULTS.pollIntervalSeconds,
            pollTimeoutSeconds = prefs[KEY_POLL_TIMEOUT] ?: FeatureConfig.SAFE_DEFAULTS.pollTimeoutSeconds,
            reconciliationIntervalMinutes = prefs[KEY_RECONCILIATION_INTERVAL]
                ?: FeatureConfig.SAFE_DEFAULTS.reconciliationIntervalMinutes,
            ingestPath = prefs[KEY_INGEST_PATH] ?: FeatureConfig.SAFE_DEFAULTS.ingestPath,
        )
    }

    private suspend fun persist(config: FeatureConfig) {
        context.featureConfigDataStore.edit { prefs ->
            prefs[KEY_ENABLE_SALE] = config.enableSale
            prefs[KEY_ENABLE_ABORT] = config.enableAbort
            prefs[KEY_ENABLE_VOID] = config.enableVoid
            prefs[KEY_ENABLE_REFUND] = config.enableRefund
            prefs[KEY_POLL_INTERVAL] = config.pollIntervalSeconds
            prefs[KEY_POLL_TIMEOUT] = config.pollTimeoutSeconds
            prefs[KEY_RECONCILIATION_INTERVAL] = config.reconciliationIntervalMinutes
            config.ingestPath?.let { prefs[KEY_INGEST_PATH] = it }
        }
    }

    private companion object {
        val KEY_ENABLE_SALE = booleanPreferencesKey("enable_sale")
        val KEY_ENABLE_ABORT = booleanPreferencesKey("enable_abort")
        val KEY_ENABLE_VOID = booleanPreferencesKey("enable_void")
        val KEY_ENABLE_REFUND = booleanPreferencesKey("enable_refund")
        val KEY_POLL_INTERVAL = intPreferencesKey("poll_interval_seconds")
        val KEY_POLL_TIMEOUT = intPreferencesKey("poll_timeout_seconds")
        val KEY_RECONCILIATION_INTERVAL = intPreferencesKey("reconciliation_interval_minutes")
        val KEY_INGEST_PATH = stringPreferencesKey("ingest_path")
    }
}
