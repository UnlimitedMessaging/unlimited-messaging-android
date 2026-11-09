package app.unlimitedmessaging.android.analytics

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryQueueTest {

    private val now = 1_800_000_000_000L
    private fun event(id: Int, at: Long = now) = QueuedEvent("android_app_opened", at, "uuid-$id", buildJsonObject { put("n", id) })

    @Test
    fun `keeps only the most recent events past the cap`() {
        var queue = emptyList<QueuedEvent>()
        repeat(TelemetryQueue.MAX_EVENTS + 20) { queue = TelemetryQueue.append(queue, event(it), now) }
        assertEquals(TelemetryQueue.MAX_EVENTS, queue.size)
        assertEquals("uuid-20", queue.first().uuid)
        assertEquals("uuid-119", queue.last().uuid)
    }

    @Test
    fun `drops events older than seven days`() {
        val old = event(1, at = now - TelemetryQueue.MAX_AGE_MILLIS - 1)
        val recent = event(2, at = now - TelemetryQueue.MAX_AGE_MILLIS + 1)
        assertEquals(listOf(recent), TelemetryQueue.prune(listOf(old, recent), now))
    }

    @Test
    fun `batches hold at most fifty events, oldest first`() {
        val queue = List(120) { event(it) }
        val batch = TelemetryQueue.nextBatch(queue)
        assertEquals(TelemetryQueue.BATCH_SIZE, batch.size)
        assertEquals("uuid-0", batch.first().uuid)
        assertEquals(70, TelemetryQueue.remove(queue, batch).size)
    }

    @Test
    fun `round trips through storage, and an unreadable queue is empty`() {
        val queue = listOf(event(1), QueuedEvent("android_setup_completed", now, "u", buildJsonObject { put("minutes_since_sign_in", JsonNull) }))
        assertEquals(queue, TelemetryQueue.decode(TelemetryQueue.encode(queue)))
        assertTrue(TelemetryQueue.decode("not json").isEmpty())
        assertTrue(TelemetryQueue.decode(null).isEmpty())
    }

    @Test
    fun `sent events carry an ISO-8601 UTC timestamp`() {
        assertEquals("2027-01-15T08:00:00Z", event(1, at = 1_800_000_000_000L).toDeviceEvent().occurredAt)
    }
}
