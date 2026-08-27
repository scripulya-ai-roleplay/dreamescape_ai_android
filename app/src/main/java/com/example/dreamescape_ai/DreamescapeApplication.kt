package com.example.dreamescape_ai

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.example.dreamescape_ai.auth.AuthInterceptor
import com.example.dreamescape_ai.auth.LoginClient
import com.example.dreamescape_ai.auth.SessionManager
import com.example.dreamescape_ai.auth.TokenAuthenticator
import com.example.dreamescape_ai.data.BackendConfig
import com.example.dreamescape_ai.data.MinioHostInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.openapitools.client.infrastructure.ApiClient

/**
 * Application entry point.
 *
 * Configures the generated OpenAPI client before any API call is made:
 *  * the base URL is loaded from [BackendConfig] (user-editable in Advanced
 *    settings, defaulting to the host backend 0.0.0.0:8000 reachable from the
 *    Android emulator through the alias 10.0.2.2) and pushed into the
 *    `org.openapitools.client.baseUrl` system property the generated clients read;
 *  * [SessionManager] is configured with the persisted login account, and an
 *    [AuthInterceptor] + [TokenAuthenticator] pair is installed so every
 *    request carries a server-issued Bearer token (obtained via
 *    `POST /api/v1/auth/login`, proactively refreshed before expiry).
 *
 * Coil's shared [ImageLoader] uses a *plain* OkHttp client (no auth interceptor):
 * media URLs are MinIO presigned/public URLs, and adding an Authorization header
 * to a presigned URL makes MinIO reject it (S3 forbids mixing query-string and
 * header auth).
 */
class DreamescapeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Synchronously bootstrap the base URL before any API call can run.
        // Each generated API class reads the property lazily once (cached), so
        // it must be correct from the very first call.
        applyBaseUrl(BackendConfig.readBaseUrlBlocking(this))
        applyMinioBaseUrl(BackendConfig.readMinioBaseUrlBlocking(this))
        configureSession()
        registerAuthentication()
        configureImageLoader()
    }

    /**
     * Configures [SessionManager] with the persisted account before any API
     * call can run. The login transport posts to the *live* base URL (the
     * system property was just set), so a mid-session base-URL change without
     * restart still logs in against the new backend on next token need.
     *
     * The last-known user id observed at login is persisted asynchronously;
     * [SessionManager.userId] is read synchronously by ViewModels, so this
     * never blocks the first frame on DataStore.
     */
    private fun configureSession() {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val account = BackendConfig.readAccountBlocking(this)
        val lastKnownUserId = runCatching {
            runBlocking { BackendConfig.lastKnownUserIdFlow(this@DreamescapeApplication).first() }
        }.getOrNull()

        SessionManager.configure(
            username = account.username,
            password = account.password,
            login = { user, pass ->
                LoginClient.login(BACKEND_BASE_URL, user, pass, loginClient)
            },
            initialUserId = lastKnownUserId
        ) { resolved ->
            appScope.launch {
                BackendConfig.setLastKnownUserId(this@DreamescapeApplication, resolved)
            }
        }
    }

    /**
     * Standalone OkHttp client used only for the login POST: it must NOT carry
     * [AuthInterceptor]/[TokenAuthenticator], which would recurse (login needs
     * a token, the token needs login). Built from the shared builder with the
     * auth pieces skipped, so proxy/timeouts stay consistent.
     */
    private val loginClient: OkHttpClient by lazy { OkHttpClient() }

    /**
     * Installs the auth pair on the shared OkHttp builder used by the generated
     * API client: the interceptor stamps the token on every request, the
     * authenticator retries once with a fresh one after an unexpected 401.
     *
     * Both must be added before [ApiClient.defaultClient] is first built, which
     * happens on the first API call and therefore always after [onCreate]. The
     * registration is idempotent to stay safe if the process is reused.
     */
    private fun registerAuthentication() {
        val alreadyRegistered = ApiClient.builder.interceptors().any { it is AuthInterceptor }
        if (!alreadyRegistered) {
            ApiClient.builder
                .addInterceptor(AuthInterceptor())
                .authenticator(TokenAuthenticator())
        }
    }

    /**
     * Configures Coil's shared [ImageLoader] with a plain OkHttp client — NOT the
     * authed [ApiClient.defaultClient]. Media URLs are MinIO presigned (private)
     * or public URLs; a presigned URL carries its signature in the query string,
     * and MinIO rejects the request (HTTP 400) if an `Authorization` header is
     * also present. So image requests must carry no Authorization header.
     *
     * A [MinioHostInterceptor] is added so that, when the user has set a MinIO
     * override in Advanced settings, image URLs are retargeted at the reachable
     * address (e.g. over a VPN). It reads [MINIO_BASE_URL] live, so a change
     * applies to the next image fetch with no restart.
     */
    private fun configureImageLoader() {
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient {
                    OkHttpClient.Builder()
                        .addInterceptor(MinioHostInterceptor { MINIO_BASE_URL })
                        .build()
                }
                .build()
        )
    }

    companion object {
        /**
         * Live base URL of the backend API. Mutable so the non-generated
         * consumers ([MediaUploader] and the chat SSE stream) pick up a runtime
         * change immediately; the generated API clients read it through the
         * system property set by [applyBaseUrl] (and need a restart to switch,
         * because they cache the value lazily).
         *
         * Read by reference (no qualifier change needed) wherever the old
         * `const` was used. Defaults to the emulator alias 10.0.2.2 → host loopback.
         */
        @Volatile
        var BACKEND_BASE_URL: String = BackendConfig.DEFAULT_BACKEND_BASE_URL

        /**
         * Sets the active backend [url] everywhere: the system property the
         * generated clients read, and the [BACKEND_BASE_URL] field the direct
         * consumers read. Called once at startup ([onCreate]) and again whenever
         * the user changes the address in Advanced settings.
         */
        fun applyBaseUrl(url: String) {
            val normalized = url.trim().trimEnd('/')
            BACKEND_BASE_URL = normalized
            System.setProperty(ApiClient.baseUrlKey, normalized)
        }

        /**
         * Live override origin for MinIO/image URLs, or "" to use the URLs the
         * backend returns verbatim. Read at intercept-time by Coil's
         * [MinioHostInterceptor], so a change from Advanced settings takes effect
         * on the next image request with **no** restart.
         */
        @Volatile
        var MINIO_BASE_URL: String = BackendConfig.DEFAULT_MINIO_BASE_URL

        /** Pushes the MinIO override [url] live; blank disables the override. */
        fun applyMinioBaseUrl(url: String) {
            MINIO_BASE_URL = url.trim().trimEnd('/')
        }
    }
}
