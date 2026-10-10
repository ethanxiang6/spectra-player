package com.spectra.player.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** RFC 7636 helpers (Proof Key for Code Exchange). Pure JVM so it is unit-testable. */
object Pkce {
    private const val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private val random = SecureRandom()

    /** High-entropy code verifier, 43..128 chars from the unreserved set. */
    fun newVerifier(length: Int = 96): String {
        require(length in 43..128) { "verifier length must be 43..128" }
        return randomString(length)
    }

    /** Opaque anti-CSRF value echoed back by the authorization server. */
    fun newState(): String = randomString(32)

    /** BASE64URL(SHA256(verifier)) without padding. */
    fun challengeFor(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun randomString(length: Int): String = buildString(length) {
        repeat(length) { append(UNRESERVED[random.nextInt(UNRESERVED.length)]) }
    }
}
