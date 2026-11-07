package app.unlimitedmessaging.android.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Proof Key for Code Exchange (RFC 7636), S256 method only.
 *
 * The app is a public client: it cannot keep a client secret, since anyone can
 * unpack the APK. PKCE binds the authorization code to a secret generated for
 * this one sign-in, so a code intercepted on its way back to the app is
 * useless to whoever intercepted it.
 */
object Pkce {
    private val random = SecureRandom()
    private val base64Url = Base64.getUrlEncoder().withoutPadding()

    /** 32 random bytes, base64url: a 43-character verifier (the RFC allows 43 to 128). */
    fun newVerifier(): String = randomToken(32)

    /** `BASE64URL(SHA256(ASCII(verifier)))`. */
    fun challengeFor(verifier: String): String =
        base64Url.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
        )

    /** Opaque value echoed back by the server, checked to reject forged redirects (CSRF). */
    fun newState(): String = randomToken(16)

    private fun randomToken(bytes: Int): String =
        base64Url.encodeToString(ByteArray(bytes).also(random::nextBytes))
}
