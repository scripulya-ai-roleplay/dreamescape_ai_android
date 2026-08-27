package com.example.dreamescape_ai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.UUID

/** Per-process DataStore remembering the backend base URL and the login account. */
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
 *
 * The login account (username + password) is persisted the same way and read
 * once at startup to configure [com.example.dreamescape_ai.auth.SessionManager];
 * changing it likewise needs a process restart.
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

    /**
     * Built-in default account: the backend's dev seed (`scripts/init.sql`),
     * matching the identity the old self-signed-token flow hardcoded.
     */
    const val DEFAULT_USERNAME: String = "mobile"
    const val DEFAULT_PASSWORD: String = "password"

    private val usernameKey = stringPreferencesKey("login_username")
    private val passwordKey = stringPreferencesKey("login_password")
    private val userIdKey = stringPreferencesKey("last_known_user_id")

    /**
     * Login account (username + password). The password is stored in plain
     * DataStore — the same trust level the app already gives the base URL and
     * the old JWT secret; the backend's tokens are short-lived and revocable by
     * rotating credentials.
     */
    data class Account(val username: String, val password: String)

    /** Emits the persisted account, or the dev seed when unset. */
    fun accountFlow(context: Context): Flow<Account> =
        context.backendDataStore.data.map { prefs ->
            Account(
                username = prefs[usernameKey]?.takeIf { it.isNotBlank() } ?: DEFAULT_USERNAME,
                password = prefs[passwordKey]?.takeIf { it.isNotBlank() } ?: DEFAULT_PASSWORD
            )
        }

    /** Persists the login account (blank fields fall back to the defaults). */
    suspend fun setAccount(context: Context, username: String, password: String) {
        context.backendDataStore.edit {
            it[usernameKey] = username.trim()
            it[passwordKey] = password
        }
    }

    /**
     * Reads the persisted account **synchronously**, for the single bootstrap
     * read in [DreamescapeApplication.onCreate] so [SessionManager] is
     * configured before any API call can run.
     */
    fun readAccountBlocking(context: Context): Account =
        runCatching { runBlocking { accountFlow(context).first() } }.getOrDefault(
            Account(DEFAULT_USERNAME, DEFAULT_PASSWORD)
        )

    /**
     * Emits the user id observed in the last successful login, or `null` when
     * no login has revealed one yet (first run: [SessionManager] falls back to
     * its dev-seed default).
     */
    fun lastKnownUserIdFlow(context: Context): Flow<UUID?> =
        context.backendDataStore.data.map { prefs ->
            prefs[userIdKey]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        }

    /** Persists the id observed at login, for correct pre-login identity. */
    suspend fun setLastKnownUserId(context: Context, userId: UUID) {
        context.backendDataStore.edit { it[userIdKey] = userId.toString() }
    }

    /**
     * Built-in default for the MinIO/image-storage override: blank, meaning
     * *no override* — image URLs returned by the backend are used verbatim. Set
     * a value (scheme + host [+ port]) to rewrite every image URL to that
     * origin, e.g. to reach MinIO from behind a VPN when the backend advertises
     * an internal host the device can't resolve.
     *
     * Unlike the base URL and JWT secret, this is read live by Coil's
     * [com.example.dreamescape_ai.data.MinioHostInterceptor] at request time, so
     * changing it needs **no** process restart.
     */
    const val DEFAULT_MINIO_BASE_URL: String = ""

    private val minioKey = stringPreferencesKey("minio_base_url")

    /** Emits the persisted MinIO override, or "" (disabled) when unset. */
    fun minioBaseUrlFlow(context: Context): Flow<String> =
        context.backendDataStore.data.map { prefs ->
            prefs[minioKey]?.takeIf { it.isNotBlank() } ?: DEFAULT_MINIO_BASE_URL
        }

    /** Persists [url] as the MinIO override (blank clears it). */
    suspend fun setMinioBaseUrl(context: Context, url: String) {
        context.backendDataStore.edit { it[minioKey] = url.trim().trimEnd('/') }
    }

    /**
     * Reads the persisted MinIO override **synchronously**, for the bootstrap
     * read in [DreamescapeApplication.onCreate] so the image-loader interceptor
     * starts with the right value before any image is fetched.
     */
    fun readMinioBaseUrlBlocking(context: Context): String =
        runCatching { runBlocking { minioBaseUrlFlow(context).first() } }
            .getOrDefault(DEFAULT_MINIO_BASE_URL)
}
