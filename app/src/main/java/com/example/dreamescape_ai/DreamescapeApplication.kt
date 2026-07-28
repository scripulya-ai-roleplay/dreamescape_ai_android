package com.example.dreamescape_ai

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.example.dreamescape_ai.auth.JwtAuthInterceptor
import com.example.dreamescape_ai.auth.JwtTokenProvider
import com.example.dreamescape_ai.data.BackendConfig
import com.example.dreamescape_ai.data.MinioHostInterceptor
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
 *  * a [JwtAuthInterceptor] is installed so every request carries a JWT Bearer
 *    token (signed with the secret configured in Advanced settings), satisfying
 *    the backend's HTTP Bearer security scheme.
 *
 * Coil's shared [ImageLoader] uses a *plain* OkHttp client (no JWT interceptor):
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
        registerJwtAuthentication()
        configureImageLoader()
    }

    /**
     * Installs a single [JwtAuthInterceptor] on the shared OkHttp builder used
     * by the generated API client, so every request is authorized with a JWT.
     *
     * It must be added before [ApiClient.defaultClient] is first built, which
     * happens on the first API call and therefore always after [onCreate]. The
     * registration is idempotent to stay safe if the process is reused.
     *
     * The token provider is built with the JWT secret persisted in Advanced
     * settings ([BackendConfig.readJwtSecretBlocking]), defaulting to the dev
     * secret when unset; changing it needs a process restart.
     */
    private fun registerJwtAuthentication() {
        val alreadyRegistered = ApiClient.builder.interceptors().any { it is JwtAuthInterceptor }
        if (!alreadyRegistered) {
            val secret = BackendConfig.readJwtSecretBlocking(this)
            ApiClient.builder.addInterceptor(JwtAuthInterceptor(JwtTokenProvider(secretKey = secret)))
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
