package com.example.dreamescape_ai.auth

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * OkHttp interceptor that attaches the server-issued access token as a Bearer
 * header on every outgoing request.
 *
 * [SessionManager.currentToken] returns the cached token when it is fresh,
 * logging in first only when needed; when even that fails (backend down, bad
 * credentials) the request goes out without the header so the server's own
 * 401/403 — not a client-side crash — is what surfaces.
 */
class AuthInterceptor(
    private val session: SessionManager = SessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(authorize(chain.request()))

    /**
     * Returns [request] unchanged when it already carries an `Authorization`
     * header, otherwise a copy with `Authorization: Bearer <token>` added.
     */
    internal fun authorize(request: Request): Request {
        if (request.header(HEADER_AUTHORIZATION) != null) return request

        val token = runCatching { session.currentToken() }.getOrNull()
            ?: return request

        return request.newBuilder()
            .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$token")
            .build()
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}

/**
 * OkHttp authenticator: retries exactly once with a fresh token when the
 * server rejects one we believed was valid (expired mid-flight, secret
 * rotated, clock edge) — the reactive half of [SessionManager]'s proactive
 * refresh.
 *
 * Returning `null` from `authenticate` gives up (OkHttp delivers the 401 to
 * the caller), so this never loops: we retry only when the token actually
 * changed compared to the one the rejected response carried.
 */
class TokenAuthenticator(
    private val session: SessionManager = SessionManager
) : okhttp3.Authenticator {

    override fun authenticate(route: okhttp3.Route?, response: Response): Request? {
        val failedToken = response.request.header(HEADER_AUTHORIZATION)
            ?.removePrefix(BEARER_PREFIX)
            ?: return null

        session.invalidate()

        val freshToken = runCatching { session.currentToken() }.getOrNull()
            ?: return null

        if (freshToken == failedToken) return null

        return response.request.newBuilder()
            .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$freshToken")
            .build()
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
