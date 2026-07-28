package com.example.dreamescape_ai.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites the origin (scheme + host + port) of every image request to a
 * configured MinIO override, leaving the path and query string untouched.
 *
 * Installed on Coil's plain OkHttp client (see
 * [com.example.dreamescape_ai.DreamescapeApplication]) so that MinIO presigned or
 * public URLs advertised by the backend with an internal host — unreachable,
 * for instance, when the device is behind a VPN — can be retargeted at an
 * address the device can actually reach.
 *
 * The override is read live via [overrideProvider] (backed by
 * `DreamescapeApplication.MINIO_BASE_URL`), so a change in Advanced settings
 * takes effect on the next image request with no restart. When it returns a
 * blank value, requests pass through unchanged and the backend's URL is used
 * verbatim.
 *
 * Only the origin changes: a presigned URL's signature lives in the query
 * string, which is preserved exactly.
 */
class MinioHostInterceptor(
    private val overrideProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val override = overrideProvider().trim()
        if (override.isBlank()) return chain.proceed(original)

        val base = override.toHttpUrlOrNull() ?: return chain.proceed(original)
        val rewritten = retarget(original.url, base)
        // Same origin already — nothing to do.
        if (rewritten == original.url) return chain.proceed(original)

        return chain.proceed(original.newBuilder().url(rewritten).build())
    }

    /**
     * Returns a copy of [original] whose scheme/host/port come from [base],
     * keeping the path, query, and fragment. Falls back to [original] if it
     * isn't an http(s) URL (Coil only routes http(s) through OkHttp, so this is
     * purely defensive — e.g. local content URIs never reach here).
     */
    private fun retarget(original: HttpUrl, base: HttpUrl): HttpUrl {
        if (original.scheme != "http" && original.scheme != "https") return original
        return original.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
    }
}
