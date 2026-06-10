package com.example.dreamescape_ai.auth

import java.time.Duration

/**
 * JWT configuration mirroring the backend ("Gemini Chat") settings.
 *
 * Every backend endpoint is protected by an HTTP Bearer security scheme and the
 * token is verified with these settings:
 *
 * ```
 * JWT_SECRET_KEY: str = "your-secret-key-change-in-production"
 * JWT_PUBLIC_KEY: str = ""
 * JWT_ALGORITHM: str = "HS256"
 * ```
 *
 * Because the algorithm is symmetric (HS256) the same secret key is used to
 * both sign and verify the token, so the client can self-sign a token with the
 * shared secret and send it as `Authorization: Bearer <token>`. The (empty)
 * public key is only relevant for asymmetric algorithms (e.g. RS256) and is
 * kept here for parity with the backend configuration.
 */
object JwtConfig {

    /** Shared secret used to sign/verify HS256 tokens. */
    const val JWT_SECRET_KEY: String = "your-secret-key-change-in-production"

    /** Public key, only used for asymmetric algorithms (unused with HS256). */
    const val JWT_PUBLIC_KEY: String = ""

    /** Signing algorithm; matches the backend's `JWT_ALGORITHM`. */
    const val JWT_ALGORITHM: String = "HS256"

    /**
     * Subject (`sub`) claim identifying the calling client.
     *
     * The backend interprets this claim as the authenticated user's id and
     * parses it as a UUID (`UUID(payload.get("sub"))`). It therefore **must** be
     * a syntactically valid UUID string, otherwise the request is rejected with
     * `InvalidTokenError: Invalid token payload` (HTTP 500). A stable, hard-coded
     * UUID is used so the app keeps a consistent identity across requests and
     * restarts.
     */
    const val TOKEN_SUBJECT: String = "00000000-0000-0000-0000-000000000001"

    /** Lifetime of a generated token before it expires. */
    val TOKEN_TTL: Duration = Duration.ofHours(1)
}
