package com.example.dreamescape_ai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dreamescape_ai.auth.JwtConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/** Per-process DataStore remembering the user-chosen backend base URL and JWT secret. */
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
 * The JWT signing secret is persisted the same way and read once at startup to
 * build the token provider; changing it likewise needs a process restart.
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
     * Built-in default signing secret: the backend's `JWT_SECRET_KEY`
     * (`dev-secret-change-me` in dev). Used when the user has not overridden it.
     */
    const val DEFAULT_JWT_SECRET: String = JwtConfig.JWT_SECRET_KEY

    private val jwtSecretKey = stringPreferencesKey("jwt_secret_key")

    /** Emits the persisted JWT signing secret, or [DEFAULT_JWT_SECRET] when unset. */
    fun jwtSecretFlow(context: Context): Flow<String> =
        context.backendDataStore.data.map { prefs ->
            prefs[jwtSecretKey]?.takeIf { it.isNotBlank() } ?: DEFAULT_JWT_SECRET
        }

    /** Persists [secret] as the JWT signing key (blank clears the override). */
    suspend fun setJwtSecret(context: Context, secret: String) {
        context.backendDataStore.edit { it[jwtSecretKey] = secret.trim() }
    }

    /**
     * Reads the persisted JWT secret **synchronously**, for the single bootstrap
     * read in [DreamescapeApplication.onCreate] so the token provider is built
     * with the right key before any API call can run.
     */
    fun readJwtSecretBlocking(context: Context): String =
        runCatching { runBlocking { jwtSecretFlow(context).first() } }
            .getOrDefault(DEFAULT_JWT_SECRET)

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
