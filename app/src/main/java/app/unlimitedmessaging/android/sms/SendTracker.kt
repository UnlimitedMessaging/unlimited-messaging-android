package app.unlimitedmessaging.android.sms

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Persists a [SendRecord] per outgoing message, so per-part results that
 * arrive after the app was killed (delivery reports can take hours) still
 * land on the right message.
 *
 * It also makes sending idempotent: a message the server hands out twice
 * (because our status report was lost) is recognized and not sent again.
 *
 * Records are pruned after [RETENTION_DAYS].
 */
class SendTracker(context: Context) {
    private val prefs = context.getSharedPreferences("send_tracker", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    fun get(messageId: String): SendRecord? = synchronized(lock) { read(messageId) }

    /** Starts tracking a message about to be sent in [parts] parts. */
    fun start(messageId: String, parts: Int): SendRecord = synchronized(lock) {
        prune()
        SendRecord(messageId, parts, createdAt = System.currentTimeMillis()).also(::write)
    }

    /**
     * Applies [change] to the stored record atomically. Returns the
     * transition, or null for a message this phone never tracked.
     */
    fun update(messageId: String, change: (SendRecord) -> SendRecord.Transition): SendRecord.Transition? =
        synchronized(lock) {
            val current = read(messageId) ?: return null
            change(current).also { write(it.record) }
        }

    private fun read(messageId: String): SendRecord? =
        prefs.getString(messageId, null)?.let {
            runCatching { json.decodeFromString(SendRecord.serializer(), it) }.getOrNull()
        }

    private fun write(record: SendRecord) {
        prefs.edit(commit = true) { putString(record.messageId, json.encodeToString(SendRecord.serializer(), record)) }
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        val stale = prefs.all.keys.filter { key -> (read(key)?.createdAt ?: 0L) < cutoff }
        if (stale.isNotEmpty()) prefs.edit(commit = true) { stale.forEach(::remove) }
    }

    private companion object {
        const val RETENTION_DAYS = 7L
    }
}
