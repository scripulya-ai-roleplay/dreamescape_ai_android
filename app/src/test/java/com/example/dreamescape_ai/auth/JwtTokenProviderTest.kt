package com.example.dreamescape_ai.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class JwtTokenProviderTest {

    private val secret = "dev-secret-change-me"
    private val fixedInstant = Instant.ofEpochSecond(1_000_000L)

    private fun provider(
        secretKey: String = secret,
        algorithm: String = "HS256",
        subject: String = "00000000-0000-0000-0000-000000000001",
        role: String = "api",
        ttl: Duration = Duration.ofHours(1)
    ): JwtTokenProvider = JwtTokenProvider(
        secretKey = secretKey,
        algorithm = algorithm,
        subject = subject,
        role = role,
        tokenTtl = ttl,
        clock = { fixedInstant }
    )

    private fun decode(part: String): String =
        String(Base64.getUrlDecoder().decode(part), Charsets.UTF_8)

    @Test
    fun `token has three non-empty base64url parts`() {
        val parts = provider().createToken().split(".")

        assertEquals(3, parts.size)
        parts.forEach { assertTrue("part should not be empty", it.isNotEmpty()) }
    }

    @Test
    fun `header declares HS256 algorithm and JWT type`() {
        val header = decode(provider().createToken().split(".")[0])

        assertEquals("""{"alg":"HS256","typ":"JWT"}""", header)
    }

    @Test
    fun `payload mirrors the backend agreed token shape`() {
        val payload = decode(provider().createToken().split(".")[1])

        // Mirrors the backend's {sub, user_id, role, exp} (jwt_service.py) plus
        // iat. iat = fixed instant (1_000_000), exp = iat + 1h (3600s) = 1_003_600.
        assertEquals(
            """{"sub":"00000000-0000-0000-0000-000000000001","user_id":"00000000-0000-0000-0000-000000000001","role":"api","iat":1000000,"exp":1003600}""",
            payload
        )
    }

    @Test
    fun `payload carries a role the backend accepts`() {
        // The backend parses role with UserRole(payload["role"]) and 401s without
        // it; ``api`` is a valid UserRole (least privilege, matches UserRole.API).
        val role = Regex("\"role\":\"([^\"]*)\"")
            .find(decode(JwtTokenProvider().createToken().split(".")[1]))!!
            .groupValues[1]

        assertEquals(JwtConfig.TOKEN_ROLE, role)
        assertTrue("role must be a backend UserRole value", role in listOf("admin", "api", "developer"))
    }

    @Test
    fun `user_id claim duplicates sub for the backend fallback`() {
        // verify_token reads user_id-or-sub; both must carry the same UUID.
        val payload = decode(provider().createToken().split(".")[1])
        val sub = Regex("\"sub\":\"([^\"]*)\"").find(payload)!!.groupValues[1]
        val userId = Regex("\"user_id\":\"([^\"]*)\"").find(payload)!!.groupValues[1]

        assertEquals(sub, userId)
    }

    @Test
    fun `default sub claim is a valid UUID as the backend requires`() {
        // The backend parses the sub claim with UUID(payload.get("sub")), so the
        // production default subject must be a syntactically valid UUID string.
        val token = JwtTokenProvider(clock = { fixedInstant }).createToken()
        val payload = decode(token.split(".")[1])

        val sub = Regex("\"sub\":\"([^\"]*)\"").find(payload)!!.groupValues[1]
        // Must not throw: mirrors the backend's UUID(payload.get("sub")).
        assertEquals(sub, UUID.fromString(sub).toString())
    }

    @Test
    fun `userId returns the subject parsed as a UUID`() {
        val subject = "11111111-2222-3333-4444-555555555555"

        val userId = provider(subject = subject).userId

        assertEquals(UUID.fromString(subject), userId)
    }

    @Test
    fun `default userId matches the backend subject the token is signed with`() {
        // The owner id stamped on created resources must equal the sub claim the
        // backend reads from the token, i.e. JwtConfig.TOKEN_SUBJECT.
        val userId = JwtTokenProvider().userId

        assertEquals(UUID.fromString(JwtConfig.TOKEN_SUBJECT), userId)
    }

    @Test
    fun `userId rejects a non-UUID subject`() {
        assertThrows(IllegalArgumentException::class.java) {
            provider(subject = "not-a-uuid").userId
        }
    }

    @Test
    fun `signature is a valid HMAC-SHA256 over header and payload`() {
        val parts = provider().createToken().split(".")
        val signingInput = "${parts[0]}.${parts[1]}"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val expectedSignature = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(signingInput.toByteArray(Charsets.UTF_8)))

        assertEquals(expectedSignature, parts[2])
    }

    @Test
    fun `tokens generated with a fixed clock are deterministic`() {
        assertEquals(provider().createToken(), provider().createToken())
    }

    @Test
    fun `unsupported asymmetric algorithm is rejected`() {
        assertThrows(UnsupportedOperationException::class.java) {
            provider(algorithm = "RS256").createToken()
        }
    }

    @Test
    fun `empty secret key is rejected at construction time`() {
        assertThrows(IllegalArgumentException::class.java) {
            provider(secretKey = "")
        }
    }

    @Test
    fun `empty role is rejected at construction time`() {
        assertThrows(IllegalArgumentException::class.java) {
            provider(role = "")
        }
    }
}
