package app.unlimitedmessaging.android.service

import kotlin.random.Random

/**
 * Exponential backoff with jitter for the outbox loop: 2 s, 4 s, 8 s... up
 * to [maxMillis]. The jitter keeps a fleet of phones from reconnecting in
 * lockstep after a server restart.
 */
class Backoff(
    private val initialMillis: Long = 2_000,
    private val maxMillis: Long = 5 * 60_000,
    private val random: Random = Random.Default,
) {
    private var attempt = 0

    fun next(): Long {
        val base = (initialMillis shl attempt.coerceAtMost(20)).coerceAtMost(maxMillis)
        attempt++
        // Between 75% and 100% of the base delay.
        return base - (random.nextDouble() * base * 0.25).toLong()
    }

    fun reset() {
        attempt = 0
    }
}
