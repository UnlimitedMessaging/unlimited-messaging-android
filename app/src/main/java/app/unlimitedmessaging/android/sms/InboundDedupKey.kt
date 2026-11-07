package app.unlimitedmessaging.android.sms

import java.security.MessageDigest

/**
 * Identifies one received SMS across retries and duplicate broadcasts.
 *
 * SMS have no global id. The SIM, sender, network timestamp and text
 * together are unique in practice: the same sender cannot send two
 * different SMS with the same network timestamp to the same SIM, and an
 * identical resend gets a new timestamp. Hashed so the key carries no
 * content.
 */
fun inboundDedupKey(subscriptionId: Int, from: String, sentAtMillis: Long, text: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$subscriptionId|$from|$sentAtMillis|$text".toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
