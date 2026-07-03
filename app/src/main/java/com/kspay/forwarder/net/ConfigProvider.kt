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
            enableRefund = prefs[KEY_ENABLE_REFUND] ?: FeatureConfig.SAFE_DEFAULTS.enableRefund,
            enableVoid = prefs[KEY_ENABLE_VOID] ?: FeatureConfig.SAFE_DEFAULTS.enableVoid,
            pollIntervalSeconds = prefs[KEY_POLL_INTERVAL] ?: FeatureConfig.SAFE_DEFAULTS.pollIntervalSeconds,
            backendIngestUrl = prefs[KEY_BACKEND_URL] ?: FeatureConfig.SAFE_DEFAULTS.backendIngestUrl,
        )
    }

    private suspend fun persist(config: FeatureConfig) {
        context.featureConfigDataStore.edit { prefs ->
            prefs[KEY_ENABLE_REFUND] = config.enableRefund
            prefs[KEY_ENABLE_VOID] = config.enableVoid
            prefs[KEY_POLL_INTERVAL] = config.pollIntervalSeconds
            config.backendIngestUrl?.let { prefs[KEY_BACKEND_URL] = it }
        }
    }

    private companion object {
        val KEY_ENABLE_REFUND = booleanPreferencesKey("enable_refund")
        val KEY_ENABLE_VOID = booleanPreferencesKey("enable_void")
        val KEY_POLL_INTERVAL = intPreferencesKey("poll_interval_s")
        val KEY_BACKEND_URL = stringPreferencesKey("backend_ingest_url")
    }
}
