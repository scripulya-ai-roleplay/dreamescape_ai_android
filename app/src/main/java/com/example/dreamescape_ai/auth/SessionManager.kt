package com.example.dreamescape_ai.auth

import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Process-wide owner of the server-issued access token and the identity it
 * carries, replacing the old client-side self-signed JWT flow (GITHUB-80 made
 * the signing secret server-only).
 *
 * The backend's `POST /api/v1/auth/login` exchanges `{username, password}` for
 * `{access_token, token_type: "bearer"}`. The token payload is
 * `{sub, user_id, role, exp}` and carries **no** `iat`, so client clock skew can
 * no longer produce "The token is not yet valid" 401s — all time validation
 * happens server-side against the server clock.
 *
 * Styled after [com.example.dreamescape_ai.data.BackendConfig]: a singleton
 * configured once by `DreamescapeApplication.onCreate` before the first API
 * call. ViewModels read [userId] synchronously (no network); the OkHttp
 * [AuthInterceptor] and [TokenAuthenticator] call [currentToken] on their own
 * threads, where blocking is fine.
 */
object SessionManager {

    /**
     * Identity used before the first successful login: the dev-seeded `mobile`
     * user (`scripts/init.sql`), i.e. the same user the old self-signed token
     * hardcoded as its subject — so ownership filters behave exactly as before
     * on a fresh install.
     */
    val DEFAULT_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    /**
     * How many seconds before `exp` a cached token is considered stale and
     * proactively re-issued, so a normal request never races the expiry. A
     * token that slips through anyway is handled reactively by
     * [TokenAuthenticator].
     */
    const val REFRESH_MARGIN_SECONDS: Long = 60

    @Volatile
    var userId: UUID = DEFAULT_USER_ID
        private set

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpiresAt: Instant? = null

    @Volatile
    private var username: String = ""

    @Volatile
    private var password: String = ""

    @Volatile
    private var login: ((username: String, password: String) -> String)? = null

    @Volatile
    private var clock: () -> Instant = Instant::now

    @Volatile
    private var onUserIdResolved: ((UUID) -> Unit)? = null

    /**
     * Human-readable reason the last login attempt failed, or null when the
     * session is healthy (never logged in yet, or last login succeeded).
     *
     * [AuthInterceptor] deliberately sends requests unauthenticated when the
     * login fails, and optional-auth endpoints then answer 200 with public
     * data only — so a broken login looks like "no images yet" rather than an
     * error. Screens that can show private data surface this so the real
     * cause is visible.
     */
    @Volatile
    var lastLoginError: String? = null
        private set

    private val lock = Any()

    /**
     * Wires the session with persisted credentials and the login transport.
     * Fully resets any previous state, so it is also what makes the singleton
     * reusable across unit tests.
     *
     * @param initialUserId last-known identity persisted from a previous
     *   process, so "my content" screens are correct even before the first
     *   login completes; `null` falls back to [DEFAULT_USER_ID].
     * @param onUserIdResolved invoked (on the caller of [currentToken]'s
     *   thread) whenever a login reveals a different identity, so the app can
     *   persist it for the next cold start.
     */
    fun configure(
        username: String,
        password: String,
        login: (username: String, password: String) -> String,
        clock: () -> Instant = Instant::now,
        initialUserId: UUID? = null,
        onUserIdResolved: ((UUID) -> Unit)? = null
    ) {
        synchronized(lock) {
            this.username = username
            this.password = password
            this.login = login
            this.clock = clock
            this.onUserIdResolved = onUserIdResolved
            this.accessToken = null
            this.tokenExpiresAt = null
            this.userId = initialUserId ?: DEFAULT_USER_ID
            this.lastLoginError = null
        }
    }

    /**
     * Swaps the account without reconfiguring the transport (Advanced settings
     * uses this). Drops any cached token so the next request logs in with the
     * new credentials.
     */
    fun updateCredentials(username: String, password: String) {
        synchronized(lock) {
            this.username = username
            this.password = password
            invalidateTokenLocked()
        }
    }

    /**
     * Returns a usable access token, logging in first when there is no cached
     * one or it is within [REFRESH_MARGIN_SECONDS] of expiry.
     *
     * Blocking (may perform a network login); must be called from a background
     * thread — OkHttp interceptor/authenticator threads qualify. Throws when
     * not configured or when the login attempt fails; callers decide whether
     * that is fatal ([AuthInterceptor] just sends the request unauthenticated
     * and lets the server's 401 speak).
     */
    fun currentToken(): String {
        val cached = accessToken
        if (cached != null && !isStale()) return cached

        synchronized(lock) {
            val token = accessToken
            if (token != null && !isStale()) return token
            return performLoginLocked()
        }
    }

    /**
     * Drops the cached token (keeping the credentials), forcing the next
     * [currentToken] to log in again. Called by [TokenAuthenticator] after the
     * server rejected a token we believed was valid.
     */
    fun invalidate() {
        synchronized(lock) {
            invalidateTokenLocked()
        }
    }

    private fun invalidateTokenLocked() {
        accessToken = null
        tokenExpiresAt = null
    }

    /**
     * A token is stale when its `exp` is known and closer than the refresh
     * margin. An unparseable `exp` is treated as "never proactively refresh"
     * (the reactive 401 path still covers it) rather than logging in on every
     * request.
     */
    private fun isStale(): Boolean {
        val expiresAt = tokenExpiresAt ?: return false
        return !clock().plusSeconds(REFRESH_MARGIN_SECONDS).isBefore(expiresAt)
    }

    private fun performLoginLocked(): String {
        val login = this.login
            ?: throw IllegalStateException("SessionManager is not configured")
        val token = try {
            login(username, password)
        } catch (e: Exception) {
            lastLoginError = e.message ?: "Login failed"
            throw e
        }
        lastLoginError = null

        val claims = TokenClaims.parse(token)
        accessToken = token
        tokenExpiresAt = claims?.expiresAt

        val resolvedId = claims?.userId
        if (resolvedId != null && resolvedId != userId) {
            userId = resolvedId
            onUserIdResolved?.invoke(resolvedId)
        }
        return token
    }
}

/**
 * Reads the claims the app cares about out of a compact-serialized JWT payload.
 *
 * Deliberately dependency-free (base64url + regex): the producer is our own
 * backend with a fixed payload shape, and the token already arrived over TLS
 * from the server, so there is nothing to verify — only to read. Anything
 * unexpected yields `null`, which [SessionManager] maps to "rely on the
 * reactive 401 path".
 */
internal object TokenClaims {

    private val expRegex = Regex(""""exp"\s*:\s*(\d+)""")
    private val userIdRegex = Regex(""""user_id"\s*:\s*"([^"]+)"""")
    private val subRegex = Regex(""""sub"\s*:\s*"([^"]+)"""")

    data class Claims(val expiresAt: Instant?, val userId: UUID?)

    fun parse(token: String): Claims? {
        val payload = runCatching {
            val json = token.split(".")[1]
            String(Base64.getUrlDecoder().decode(json), Charsets.UTF_8)
        }.getOrNull() ?: return null

        val expiresAt = expRegex.find(payload)?.groupValues?.get(1)?.toLongOrNull()
            ?.let(Instant::ofEpochSecond)

        val rawId = userIdRegex.find(payload)?.groupValues?.get(1)
            ?: subRegex.find(payload)?.groupValues?.get(1)
        val userId = rawId?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        return Claims(expiresAt = expiresAt, userId = userId)
    }
}
