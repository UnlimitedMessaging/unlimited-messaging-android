package app.unlimitedmessaging.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BackoffTest {

    @Test
    fun `doubles up to the cap, with at most 25 percent jitter`() {
        val backoff = Backoff(initialMillis = 1_000, maxMillis = 8_000, random = Random(42))
        val delays = List(6) { backoff.next() }
        listOf(1_000L, 2_000L, 4_000L, 8_000L, 8_000L, 8_000L).zip(delays).forEach { (base, actual) ->
            assertTrue("$actual should be within [${base * 3 / 4}, $base]", actual in (base * 3 / 4)..base)
        }
    }

    @Test
    fun `reset starts over`() {
        val backoff = Backoff(initialMillis = 1_000, maxMillis = 60_000, random = Random(0))
        repeat(5) { backoff.next() }
        backoff.reset()
        assertTrue(backoff.next() <= 1_000)
    }

    @Test
    fun `never overflows after many attempts`() {
        val backoff = Backoff(initialMillis = 2_000, maxMillis = 300_000, random = Random(1))
        repeat(200) { backoff.next() }
        assertTrue(backoff.next() in 225_000L..300_000L)
        assertEquals(Unit, backoff.reset())
    }
}
