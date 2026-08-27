package com.example.dreamescape_ai.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.openapitools.client.infrastructure.Serializer

/**
 * Minimal typed client for `POST /api/v1/auth/login`, hand-built for the same
 * reason [com.example.dreamescape_ai.MediaUploader] builds its multipart by
 * hand: the generated OpenAPI surface does not (yet) cover the auth paths.
 *
 * Runs on whatever thread [SessionManager.currentToken] was called from (an
 * OkHttp interceptor or authenticator thread) and blocks until the server
 * answers. Only the raw OkHttp client is used — deliberately NOT the shared
 * authed [org.openapitools.client.infrastructure.ApiClient.defaultClient],
 * which would recurse through [AuthInterceptor] → login → authenticator.
 */
object LoginClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    class LoginFailedException(message: String, val code: Int) : Exception(message)

    /**
     * Exchanges credentials for an access token. Throws [LoginFailedException]
     * on any non-2xx (401 invalid credentials, 5xx, …) so callers can tell a
     * login failure from a network failure.
     */
    fun login(baseUrl: String, username: String, password: String, client: OkHttpClient): String {
        val body = Serializer.moshi.adapter(LoginPayload::class.java)
            .toJson(LoginPayload(username = username, password = password))
            .toRequestBody(JSON)

        val request = Request.Builder()
            .url("$baseUrl/api/v1/auth/login")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw LoginFailedException(
                    "Login failed: HTTP ${response.code} ${raw.take(200)}",
                    response.code
                )
            }
            return Serializer.moshi.adapter(TokenPayload::class.java).fromJson(raw)?.access_token
                ?: throw LoginFailedException("Login response had no access_token", response.code)
        }
    }

    private data class LoginPayload(val username: String, val password: String)

    private data class TokenPayload(val access_token: String)
}
