package com.example.dreamescape_ai.auth

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class JwtAuthInterceptorTest {

    // Fixed clock => deterministic token, so the expected header is reproducible.
    private val tokenProvider = JwtTokenProvider(clock = { Instant.ofEpochSecond(1_000_000L) })
    private val interceptor = JwtAuthInterceptor(tokenProvider)

    private fun request(): Request =
        Request.Builder().url("http://10.0.2.2:8000/api/v1/scenes/").build()

    @Test
    fun `adds bearer token when authorization header is absent`() {
        val authorized = interceptor.authorize(request())

        assertEquals("Bearer ${tokenProvider.createToken()}", authorized.header("Authorization"))
    }

    @Test
    fun `injected token is a well-formed JWT`() {
        val authorized = interceptor.authorize(request())
        val header = authorized.header("Authorization")!!

        assertTrue("header should start with the Bearer prefix", header.startsWith("Bearer "))
        assertEquals(3, header.removePrefix("Bearer ").split(".").size)
    }

    @Test
    fun `does not overwrite an existing authorization header`() {
        val original = request().newBuilder()
            .header("Authorization", "Bearer existing-token")
            .build()

        val result = interceptor.authorize(original)

        assertEquals("Bearer existing-token", result.header("Authorization"))
    }
}
