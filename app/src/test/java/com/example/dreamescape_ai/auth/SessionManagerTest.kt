package com.example.dreamescape_ai.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

class SessionManagerTest {

    private val fixedNow = Instant.ofEpochSecond(10_000L)

    /** Advances by [offsetSeconds] every read, to simulate time passing. */
    private class MutableClock(var now: Instant = Instant.ofEpochSecond(0)) {
        fun read(): Instant = now
    }

    private fun token(expiryEpochSeconds: Long, userId: String = "11111111-1111-1111-1111-111111111111"): String {
        val payload = """{"sub":"$userId","user_id":"$userId","role":"api","exp":$expiryEpochSeconds}"""
        val encodedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "header.$encodedPayload.signature"
    }

    @Before
    fun reset() {
        SessionManager.configure(
            username = "mobile",
            password = "password",
            login = { _, _ -> throw IllegalStateException("not configured in this test") },
            clock = { fixedNow }
        )
    }

    @Test
    fun `logs in when no token is cached and caches the result`() {
        var calls = 0
        SessionManager.configure(
            username = "mobile",
            password = "password",
            login = { user, pass ->
                calls++
                assertEquals("mobile", user)
                assertEquals("password", pass)
                token(expiryEpochSeconds = fixedNow.epochSecond + 30 * 60)
            },
            clock = { fixedNow }
        )

        val first = SessionManager.currentToken()
        val second = SessionManager.currentToken()

        assertEquals(1, calls)
        assertEquals(first, second)
    }

    @Test
    fun `re-logins when the cached token is within the refresh margin`() {
        val clock = MutableClock(fixedNow)
        var calls = 0
        SessionManager.configure(
            username = "mobile",
            password = "password",
            login = { _, _ ->
                calls++
                token(expiryEpochSeconds = clock.read().epochSecond + 30 * 60)
            },
            clock = clock::read
        )

        SessionManager.currentToken()
        clock.now = fixedNow.plusSeconds(30 * 60 - SessionManager.REFRESH_MARGIN_SECONDS)
        SessionManager.currentToken()

        assertEquals(2, calls)
    }

    @Test
    fun `serves the cached token while it is comfortably valid`() {
        val clock = MutableClock(fixedNow)
        var calls = 0
        SessionManager.configure(
            username = "mobile",
            password = "password",
            login = { _, _ ->
                calls++
                token(expiryEpochSeconds = clock.read().epochSecond + 30 * 60)
            },
            clock = clock::read
        )

        SessionManager.currentToken()
        clock.now = fixedNow.plusSeconds(60)
        SessionManager.currentToken()

        assertEquals(1, calls)
    }

    @Test
    fun `invalidate forces the next currentToken to login again`() {
        var calls = 0
        SessionManager.configure(
            username = "mobile",
            password = "password",
            login = { _, _ ->
                calls++
                token(expiryEpochSeconds = fixedNow.epochSecond + 30 * 60)
            },
            clock = { fixedNow }
        )

        SessionManager.currentToken()
        SessionManager.invalidate()
        SessionManager.currentToken()

        assertEquals(2, calls)
    }

    @Test
    fun `updateCredentials drops the cached token`() {
        var calls = 0
        var lastPassword = ""
        SessionManager.configure(
            username = "mobile",
            password = "password",
            login = { _, pass ->
                calls++
                lastPassword = pass
                token(expiryEpochSeconds = fixedNow.epochSecond + 30 * 60)
            },
            clock = { fixedNow }
        )

        SessionManager.currentToken()
        SessionManager.updateCredentials("mobile", "new-password")
        SessionManager.currentToken()

        assertEquals(2, calls)
        assertEquals("new-password", lastPassword)
    }

    @Test
    fun `userId starts at the configured initial id`() {
        val custom = UUID.fromString("22222222-2222-2222-2222-222222222222")
        SessionManager.configure(
            username = "mobile",
            password = "password",
            login = { _, _ -> token(expiryEpochSeconds = fixedNow.epochSecond + 600) },
            clock = { fixedNow },
            initialUserId = custom
        )

        assertEquals(custom, SessionManager.userId)
    }

    @Test
    fun `userId falls back to the dev-seed default`() {
        assertEquals(SessionManager.DEFAULT_USER_ID, SessionManager.userId)
    }

    @Test
    fun `login updates userId from the token user_id claim and reports it`() {
        val observed = mutableListOf<UUID>()
        val resolved = UUID.fromString("33333333-3333-3333-3333-333333333333")
        SessionManager.configure(
            username = "mobile",
            password = "password",
            login = { _, _ -> token(expiryEpochSeconds = fixedNow.epochSecond + 600, userId = resolved.toString()) },
            clock = { fixedNow },
            initialUserId = SessionManager.DEFAULT_USER_ID,
            onUserIdResolved = { observed.add(it) }
        )

        SessionManager.currentToken()

        assertEquals(resolved, SessionManager.userId)
        assertEquals(listOf(resolved), observed)
    }

    @Test
    fun `same identity re-login does not re-report the id`() {
        val observed = mutableListOf<UUID>()
        SessionManager.configure(
            username = "mobile",
            password = "password",
            login = { _, _ -> token(expiryEpochSeconds = fixedNow.epochSecond + 600) },
            clock = { fixedNow },
            onUserIdResolved = { observed.add(it) }
        )

        SessionManager.currentToken()
        SessionManager.invalidate()
        SessionManager.currentToken()

        assertEquals(1, observed.size)
    }

    @Test
    fun `token without parseable exp still works and never proactively refreshes`() {
        val unparseable = "header.not-base64!.signature"
        var calls = 0
        SessionManager.configure(
            username = "mobile",
            password = "password",
            login = { _, _ ->
                calls++
                unparseable
            },
            clock = { fixedNow }
        )

        SessionManager.currentToken()
        val second = SessionManager.currentToken()

        assertEquals(1, calls)
        assertEquals(unparseable, second)
    }

    @Test
    fun `currentToken throws when not configured`() {
        SessionManagerResetHelper.reset()

        assertThrows(IllegalStateException::class.java) {
            SessionManager.currentToken()
        }
    }

    @Test
    fun `login failure propagates to the caller`() {
        SessionManager.configure(
            username = "mobile",
            password = "wrong",
            login = { _, _ -> throw LoginClient.LoginFailedException("HTTP 401", 401) },
            clock = { fixedNow }
        )

        assertThrows(LoginClient.LoginFailedException::class.java) {
            SessionManager.currentToken()
        }
    }
}

/** Reflection-free escape hatch: reconfigure with a login that always throws. */
internal object SessionManagerResetHelper {
    fun reset() {
        SessionManager.configure(
            username = "",
            password = "",
            login = { _, _ -> throw IllegalStateException("SessionManager is not configured") }
        )
    }
}
