package com.example.dreamescape_ai.auth

import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generates JSON Web Tokens (JWT) that authenticate the app against the backend.
 *
 * Only symmetric HMAC algorithms are supported (HS256/HS384/HS512), matching the
 * backend's `JWT_ALGORITHM = "HS256"` setting. The token is signed with the
 * shared [secretKey] (`JWT_SECRET_KEY`) so the backend can verify it with the
 * same secret.
 *
 * The implementation is dependency-free (only [Mac] and [Base64] from the JDK),
 * which keeps it portable and easy to unit-test.
 */
class JwtTokenProvider(
    private val secretKey: String = JwtConfig.JWT_SECRET_KEY,
    private val algorithm: String = JwtConfig.JWT_ALGORITHM,
    private val subject: String = JwtConfig.TOKEN_SUBJECT,
    private val tokenTtl: Duration = JwtConfig.TOKEN_TTL,
    private val clock: () -> Instant = Instant::now
) {

    init {
        require(secretKey.isNotEmpty()) {
            "A non-empty JWT secret key is required to sign $algorithm tokens."
        }
    }

    /**
     * The authenticated user's id, i.e. the value carried by the `sub` claim of
     * every token this provider issues.
     *
     * The backend treats the `sub` claim as the owner of any resource created by
     * a request (it parses it via `UUID(payload.get("sub"))`). Callers that need
     * to stamp an owner id onto a request body must therefore use this value
     * rather than generating a fresh [UUID], otherwise the persisted owner would
     * not match the authenticated identity.
     *
     * @throws IllegalArgumentException if [subject] is not a valid UUID string.
     */
    val userId: UUID
        get() = UUID.fromString(subject)

    /**
     * Builds a freshly signed, compact-serialized JWT.
     *
     * The token carries the standard `sub`, `iat` and `exp` claims. A new token
     * is produced on every call (with up-to-date timestamps) so it never expires
     * while the app is running.
     */
    fun createToken(): String {
        val macAlgorithm = macAlgorithmFor(algorithm)

        val issuedAt = clock()
        val expiresAt = issuedAt.plus(tokenTtl)

        val header = """{"alg":"$algorithm","typ":"JWT"}"""
        val payload = buildPayload(
            subject = subject,
            issuedAtEpochSeconds = issuedAt.epochSecond,
            expiresAtEpochSeconds = expiresAt.epochSecond
        )

        val encodedHeader = base64Url(header.toByteArray(Charsets.UTF_8))
        val encodedPayload = base64Url(payload.toByteArray(Charsets.UTF_8))
        val signingInput = "$encodedHeader.$encodedPayload"

        val signature = sign(signingInput, macAlgorithm)
        return "$signingInput.${base64Url(signature)}"
    }

    private fun sign(signingInput: String, macAlgorithm: String): ByteArray {
        val mac = Mac.getInstance(macAlgorithm)
        mac.init(SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), macAlgorithm))
        return mac.doFinal(signingInput.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private val urlEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

        private fun base64Url(bytes: ByteArray): String = urlEncoder.encodeToString(bytes)

        private fun macAlgorithmFor(jwtAlgorithm: String): String =
            when (jwtAlgorithm.uppercase()) {
                "HS256" -> "HmacSHA256"
                "HS384" -> "HmacSHA384"
                "HS512" -> "HmacSHA512"
                else -> throw UnsupportedOperationException(
                    "Unsupported JWT algorithm '$jwtAlgorithm'. Only HMAC algorithms " +
                        "(HS256/HS384/HS512) are supported by this client."
                )
            }

        private fun buildPayload(
            subject: String,
            issuedAtEpochSeconds: Long,
            expiresAtEpochSeconds: Long
        ): String =
            """{"sub":"${escapeJson(subject)}","iat":$issuedAtEpochSeconds,"exp":$expiresAtEpochSeconds}"""

        private fun escapeJson(value: String): String {
            val sb = StringBuilder(value.length)
            for (ch in value) {
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    else -> if (ch < '\u0020') {
                        sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
                }
            }
            return sb.toString()
        }
    }
}
