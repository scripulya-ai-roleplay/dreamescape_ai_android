package com.example.dreamescape_ai.auth

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * OkHttp interceptor that attaches a freshly signed JWT as a Bearer token to
 * every outgoing request, satisfying the backend's HTTP Bearer security scheme.
 *
 * A new token is requested from [tokenProvider] per request, so the token never
 * expires mid-session. An existing `Authorization` header is never overwritten.
 */
class JwtAuthInterceptor(
    private val tokenProvider: JwtTokenProvider = JwtTokenProvider()
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(authorize(chain.request()))

    /**
     * Returns [request] unchanged when it already carries an `Authorization`
     * header, otherwise a copy with a `Authorization: Bearer <token>` header.
     */
    internal fun authorize(request: Request): Request {
        if (request.header(HEADER_AUTHORIZATION) != null) {
            return request
        }
        return request.newBuilder()
            .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX${tokenProvider.createToken()}")
            .build()
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
