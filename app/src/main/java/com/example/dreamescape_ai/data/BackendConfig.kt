package com.example.dreamescape_ai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/** Per-process DataStore remembering the user-chosen backend base URL. */
private val Context.backendDataStore by preferencesDataStore(name = "backend_settings")

/**
 * Persists the backend base URL locally so it can be changed at runtime from
 * Advanced settings, instead of being a compile-time constant.
 *
 * The generated API clients read `org.openapitools.client.baseUrl` (a system
 * property) — [DreamescapeApplication] copies the value stored here into that
 * property at startup and whenever the user changes it. Because each generated
 * API class reads the property lazily once (cached), a change still needs a
 * process restart to reach already-loaded clients; see [AdvancedSettingsActivity].
 */
object BackendConfig {

    /**
     * Built-in default: the host backend as seen from the Android emulator.
     *
     * 10.0.2.2 is the emulator alias for the host machine's loopback, where the
     * backend is served on 0.0.0.0:8000.
     */
    const val DEFAULT_BACKEND_BASE_URL: String = "http://10.0.2.2:8000"

    private val key = stringPreferencesKey("backend_base_url")

    /** Emits the persisted base URL, or [DEFAULT_BACKEND_BASE_URL] when unset. */
    fun baseUrlFlow(context: Context): Flow<String> =
        context.backendDataStore.data.map { prefs ->
            prefs[key]?.takeIf { it.isNotBlank() } ?: DEFAULT_BACKEND_BASE_URL
        }

    /** Persists [url] as the active backend base URL (trailing slash trimmed). */
    suspend fun setBaseUrl(context: Context, url: String) {
        context.backendDataStore.edit { it[key] = url.trim().trimEnd('/') }
    }

    /**
     * Reads the persisted base URL **synchronously**. Intended for the single
     * bootstrap read in [DreamescapeApplication.onCreate] so the system property
     * is set before any API call can run. A one-key DataStore read is fast.
     */
    fun readBaseUrlBlocking(context: Context): String =
        runCatching { runBlocking { baseUrlFlow(context).first() } }
            .getOrDefault(DEFAULT_BACKEND_BASE_URL)
}
