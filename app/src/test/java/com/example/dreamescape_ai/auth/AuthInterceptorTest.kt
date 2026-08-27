package com.example.dreamescape_ai.auth

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

class AuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun request(): Request =
        Request.Builder().url(server.url("/api/v1/scenes/")).build()

    private fun tokenWithValue(value: String): String = value

    private fun configureSession(vararg tokens: String) {
        val issued = ArrayDeque(tokens.toList())
        SessionManager.configure(
            username = "mobile",
            password = "password",
            login = { _, _ -> issued.removeFirstOrNull() ?: error("no more tokens") },
            clock = { Instant.now() }
        )
    }

    @Test
    fun `adds bearer token when authorization header is absent`() {
        configureSession(tokenWithValue("first-token"))

        val authorized = AuthInterceptor().authorize(request())

        assertEquals("Bearer first-token", authorized.header("Authorization"))
    }

    @Test
    fun `does not overwrite an existing authorization header`() {
        configureSession(tokenWithValue("first-token"))
        val original = request().newBuilder()
            .header("Authorization", "Bearer existing-token")
            .build()

        val result = AuthInterceptor().authorize(original)

        assertEquals("Bearer existing-token", result.header("Authorization"))
    }

    @Test
    fun `login failure leaves the request unauthenticated`() {
        SessionManager.configure(
            username = "mobile",
            password = "bad",
            login = { _, _ -> throw LoginClient.LoginFailedException("HTTP 401", 401) },
            clock = { Instant.now() }
        )

        val authorized = AuthInterceptor().authorize(request())

        assertNull(authorized.header("Authorization"))
    }

    @Test
    fun `authenticator retries once with a fresh token after a 401`() {
        configureSession("stale-token", "fresh-token")
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .authenticator(TokenAuthenticator())
            .build()

        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = client.newCall(request()).execute()

        assertTrue(response.isSuccessful)
        assertEquals(2, server.requestCount)
        assertEquals("Bearer stale-token", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer fresh-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `authenticator gives up when the fresh login fails`() {
        configureSession("stale-token")
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .authenticator(TokenAuthenticator())
            .build()

        server.enqueue(MockResponse().setResponseCode(401))

        val response = client.newCall(request()).execute()

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `authenticator gives up when the fresh token equals the rejected one`() {
        configureSession("same-token", "same-token")
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .authenticator(TokenAuthenticator())
            .build()

        server.enqueue(MockResponse().setResponseCode(401))

        val response = client.newCall(request()).execute()

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `full login round trip against a mock server`() {
        val userId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val payload = """{"sub":"$userId","user_id":"$userId","role":"api","exp":9999999999}"""
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"access_token":"header.$encoded.signature","token_type":"bearer"}""")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        SessionManager.configure(
            username = "mobile",
            password = "password",
            login = { user, pass ->
                LoginClient.login(server.url("/").toString().trimEnd('/'), user, pass, OkHttpClient())
            },
            clock = { Instant.now() }
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .build()

        val response = client.newCall(request()).execute()

        assertTrue(response.isSuccessful)
        assertEquals(userId, SessionManager.userId)
        val loginRequest = server.takeRequest()
        assertEquals("/api/v1/auth/login", loginRequest.path)
        assertTrue(loginRequest.body.readUtf8().contains("\"username\":\"mobile\""))
    }
}
