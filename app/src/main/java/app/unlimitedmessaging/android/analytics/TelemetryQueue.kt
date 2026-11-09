package app.unlimitedmessaging.android.analytics

import app.unlimitedmessaging.android.api.DeviceEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/** One event waiting to be sent, as persisted on the phone. */
@Serializable
data class QueuedEvent(
    val event: String,
    /** Epoch millis at which it happened. */
    val at: Long,
    /** Random, lets the server ignore an event it already received (a retried batch). */
    val uuid: String,
    val properties: JsonObject,
) {
    fun toDeviceEvent() = DeviceEvent(
        event = event,
        occurredAt = Instant.ofEpochMilli(at).toString(),
        uuid = uuid,
        properties = properties,
    )
}

/**
 * The rules of the local queue, without storage: small, recent, oldest
 * dropped first. Telemetry is best effort, so a phone offline for a week
 * loses the oldest events rather than piling them up.
 */
object TelemetryQueue {
    const val MAX_EVENTS = 100
    const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

    /** The server accepts at most this many events per request. */
    const val BATCH_SIZE = 50

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(QueuedEvent.serializer())

    /** Events still worth sending at [now]. */
    fun prune(events: List<QueuedEvent>, now: Long): List<QueuedEvent> =
        events.filter { now - it.at in 0..MAX_AGE_MILLIS }.takeLast(MAX_EVENTS)

    fun append(events: List<QueuedEvent>, event: QueuedEvent, now: Long): List<QueuedEvent> =
        prune(events + event, now)

    fun nextBatch(events: List<QueuedEvent>): List<QueuedEvent> = events.take(BATCH_SIZE)

    fun remove(events: List<QueuedEvent>, sent: Collection<QueuedEvent>): List<QueuedEvent> {
        val ids = sent.mapTo(HashSet()) { it.uuid }
        return events.filterNot { it.uuid in ids }
    }

    fun encode(events: List<QueuedEvent>): String = json.encodeToString(serializer, events)

    /** An unreadable queue (older format, corrupted file) is simply empty. */
    fun decode(text: String?): List<QueuedEvent> =
        if (text.isNullOrEmpty()) emptyList() else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
}
