package app.unlimitedmessaging.android.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

enum class Direction { OUTGOING, INCOMING }

enum class EntryStatus {
    /** Outgoing: handed to the radio, waiting for its answer. */
    SENDING,
    SENT,
    DELIVERED,
    UNDELIVERABLE,
    FAILED,

    /** Incoming: received, not yet acknowledged by the server. */
    RECEIVED,

    /** Incoming: stored by the server. */
    FORWARDED,
}

@Serializable
data class ActivityEntry(
    /** Message id (outgoing) or dedup key (incoming). */
    val id: String,
    val direction: Direction,
    val address: String,
    val preview: String,
    val status: EntryStatus,
    val subscriptionId: Int?,
    val updatedAt: Long,
    val errorCode: String? = null,
)

/**
 * The last [MAX_ENTRIES] messages this phone handled, for the home screen.
 *
 * Kept on the phone only, in the app's private storage, and cleared on
 * sign-out. The server remains the source of truth for message history:
 * this is a local view of what the gateway itself did.
 */
class ActivityLog(context: Context) {
    private val file = File(context.filesDir, "activity.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ActivityEntry.serializer())
    private val lock = Any()

    private val _entries = MutableStateFlow(load())

    /** Newest first. */
    val entries: StateFlow<List<ActivityEntry>> = _entries.asStateFlow()

    fun upsert(entry: ActivityEntry) = mutate { list ->
        listOf(entry) + list.filterNot { it.id == entry.id }
    }

    /** Moves an existing entry to [status]; no-op for an unknown id. */
    fun setStatus(id: String, status: EntryStatus, errorCode: String? = null) = mutate { list ->
        list.map {
            if (it.id == id) it.copy(status = status, errorCode = errorCode ?: it.errorCode, updatedAt = System.currentTimeMillis()) else it
        }
    }

    fun clear() = mutate { emptyList() }

    private fun mutate(change: (List<ActivityEntry>) -> List<ActivityEntry>) = synchronized(lock) {
        val next = change(_entries.value).take(MAX_ENTRIES)
        _entries.value = next
        try {
            file.writeText(json.encodeToString(serializer, next))
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist the activity log", e)
        }
    }

    private fun load(): List<ActivityEntry> = try {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "Activity log unreadable, starting empty", e)
        emptyList()
    }

    companion object {
        private const val TAG = "ActivityLog"
        const val MAX_ENTRIES = 200
        const val PREVIEW_LENGTH = 80

        /** One line: line breaks (a footer, a signature) would only push the text out of the row. */
        fun preview(text: String): String {
            val oneLine = text.replace(Regex("\\s+"), " ").trim()
            return if (oneLine.length <= PREVIEW_LENGTH) oneLine else oneLine.take(PREVIEW_LENGTH - 1) + "…"
        }
    }
}
