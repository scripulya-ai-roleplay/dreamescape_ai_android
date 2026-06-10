package com.example.dreamescape_ai

import android.app.Application
import com.example.dreamescape_ai.auth.JwtAuthInterceptor
import org.openapitools.client.infrastructure.ApiClient

/**
 * Application entry point.
 *
 * Configures the generated OpenAPI client before any API call is made:
 *  * the base URL is pointed at the host backend (0.0.0.0:8000), reachable from
 *    the Android emulator through the special alias 10.0.2.2;
 *  * a [JwtAuthInterceptor] is installed so every request carries a JWT Bearer
 *    token, satisfying the backend's HTTP Bearer security scheme.
 */
class DreamescapeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        System.setProperty(ApiClient.baseUrlKey, BACKEND_BASE_URL)
        registerJwtAuthentication()
    }

    /**
     * Installs a single [JwtAuthInterceptor] on the shared OkHttp builder used
     * by the generated API client, so every request is authorized with a JWT.
     *
     * It must be added before [ApiClient.defaultClient] is first built, which
     * happens on the first API call and therefore always after [onCreate]. The
     * registration is idempotent to stay safe if the process is reused.
     */
    private fun registerJwtAuthentication() {
        val alreadyRegistered = ApiClient.builder.interceptors().any { it is JwtAuthInterceptor }
        if (!alreadyRegistered) {
            ApiClient.builder.addInterceptor(JwtAuthInterceptor())
        }
    }

    companion object {
        /**
         * Base URL of the backend API as seen from the Android emulator.
         *
         * 10.0.2.2 is the emulator alias that maps to the host machine's
         * loopback interface (0.0.0.0 / localhost / 127.0.0.1).
         */
        const val BACKEND_BASE_URL: String = "http://10.0.2.2:8000"
    }
}
