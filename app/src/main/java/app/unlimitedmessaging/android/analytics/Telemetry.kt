package app.unlimitedmessaging.android.analytics

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.core.content.edit
import app.unlimitedmessaging.android.auth.AuthRepository
import app.unlimitedmessaging.android.data.GatewayPreferences
import app.unlimitedmessaging.android.work.TelemetryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.system.exitProcess

/**
 * A few usage and diagnostic events, sent to the Unlimited Messaging API
 * (`POST /device/events`), never to a third party. The full list, and what
 * each one carries, is in README.md ("Privacy") and docs/API_CONTRACT.md:
 * keep the three in step.
 *
 * Fire-and-forget: recording appends to a small queue on the phone, and
 * [TelemetryWorker] sends it when there is a network. Nothing here may block
 * the screen or the gateway, or crash the app: every entry point swallows
 * its own failures.
 *
 * Recorded only when the build allows it ([enabledInBuild], the
 * `um.telemetryEnabled` Gradle property), the user left "Share usage
 * statistics" on, and the phone is signed in.
 */
class Telemetry(
    context: Context,
    private val preferences: GatewayPreferences,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
    val enabledInBuild: Boolean,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
    private val lock = Any()

    private val active: Boolean
        get() = enabledInBuild && preferences.telemetryEnabled.value && auth.isSignedIn

    fun appOpened(gatewayEnabled: Boolean, connection: String, setupComplete: Boolean) = safely {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_APP_OPENED, 0)
        if (!active || now - last in 0 until APP_OPENED_INTERVAL_MILLIS) return@safely
        prefs.edit { putLong(KEY_LAST_APP_OPENED, now) }
        record("android_app_opened") {
            put("signed_in", auth.isSignedIn)
            put("gateway_enabled", gatewayEnabled)
            put("connection", connection)
            put("setup_complete", setupComplete)
        }
    }

    fun permissionResult(group: String, granted: Boolean, permanentlyDenied: Boolean) = record("android_permission_result") {
        put("group", group)
        put("granted", granted)
        put("permanently_denied", permanentlyDenied)
    }

    /**
     * Compares the checklist with the last one seen, which is kept across
     * restarts: a permission granted in system settings while the app was
     * closed still counts.
     */
    fun setupObserved(current: Set<SetupStep>, allDone: Boolean) = safely {
        val previous = synchronized(lock) {
            val stored = prefs.getStringSet(KEY_SETUP_STEPS, null)
                ?.mapNotNullTo(HashSet()) { name -> SetupStep.entries.firstOrNull { it.name == name } }
            prefs.edit { putStringSet(KEY_SETUP_STEPS, current.mapTo(HashSet()) { it.name }) }
            stored
        }
        val diff = diffSetupSteps(previous, current)
        diff.completed.forEach { record("android_setup_step_completed") { put("step", it.wireName) } }
        diff.revoked.forEach { record("android_setup_step_revoked") { put("step", it.wireName) } }

        if (allDone && !prefs.getBoolean(KEY_SETUP_COMPLETED, false)) {
            prefs.edit { putBoolean(KEY_SETUP_COMPLETED, true) }
            // Already complete on the first observation: an update, not a setup.
            if (previous == null) return@safely
            val signedInAt = preferences.signedInAt
            record("android_setup_completed") {
                put("minutes_since_sign_in", signedInAt?.let { (System.currentTimeMillis() - it).coerceAtLeast(0) / 60_000 })
            }
        }
    }

    fun serviceStartBlocked(trigger: StartTrigger, backgroundRestricted: Boolean) = record("android_service_start_blocked") {
        put("trigger", trigger.wireName)
        put("background_restricted", backgroundRestricted)
    }

    fun offlineEpisode(episode: OfflineEpisodeTracker.Episode) = record("android_gateway_offline_episode") {
        put("duration_s", episode.durationSeconds)
        put("error_class", episode.errorClass)
        put("attempts", episode.attempts)
    }

    /**
     * Records crashes for the next launch, then lets the previous handler
     * (Android's, which shows the "app stopped" dialog) do its usual work.
     */
    fun installCrashHandler(isInService: () -> Boolean) {
        if (!enabledInBuild) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                if (active) {
                    // Synchronously: the process is about to die.
                    append(event("android_crash", crashProperties(error, isInService())), commit = true)
                }
            } catch (_: Throwable) {
                // Never let reporting a crash hide the crash.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** Sends what is left from a previous run (a crash, a batch that failed). */
    fun flushPending() = safely {
        if (!enabledInBuild) return@safely
        if (!active) {
            if (pendingCount() > 0) clear()
            return@safely
        }
        if (pendingCount() > 0) TelemetryWorker.enqueue(appContext)
    }

    /** Forgets every queued event: switch turned off, signed out, or the server does not want them. */
    fun clear() = safely {
        dropQueue()
        TelemetryWorker.cancel(appContext)
    }

    // Used by TelemetryWorker.

    internal val shouldSend: Boolean get() = active

    /** [clear] without cancelling the flush, for the flush itself. */
    internal fun dropQueue() = synchronized(lock) { prefs.edit { remove(KEY_QUEUE) } }

    internal fun nextBatch(): List<QueuedEvent> = synchronized(lock) {
        TelemetryQueue.nextBatch(TelemetryQueue.prune(load(), System.currentTimeMillis()))
    }

    internal fun remove(sent: List<QueuedEvent>) = synchronized(lock) {
        save(TelemetryQueue.remove(load(), sent), commit = false)
    }

    private fun pendingCount(): Int = synchronized(lock) { load().size }

    private fun record(name: String, properties: JsonObjectBuilder.() -> Unit) = safely {
        if (!active) return@safely
        val queued = event(name, buildJsonObject(properties))
        // Off the caller's thread: the screen and the gateway loop never wait on storage.
        scope.launch {
            safely {
                append(queued, commit = false)
                TelemetryWorker.enqueue(appContext)
            }
        }
    }

    private fun event(name: String, properties: JsonObject) =
        QueuedEvent(name, System.currentTimeMillis(), UUID.randomUUID().toString(), properties)

    private fun crashProperties(error: Throwable, inService: Boolean) = buildJsonObject {
        put("exception_class", CrashSummary.exceptionClass(error))
        put("app_frames", buildJsonArray { CrashSummary.appFrames(error).forEach { add(JsonPrimitive(it)) } })
        put("in_service", inService)
    }

    private fun append(event: QueuedEvent, commit: Boolean) = synchronized(lock) {
        save(TelemetryQueue.append(load(), event, System.currentTimeMillis()), commit)
    }

    private fun load(): List<QueuedEvent> = TelemetryQueue.decode(prefs.getString(KEY_QUEUE, null))

    private fun save(events: List<QueuedEvent>, commit: Boolean) {
        prefs.edit(commit = commit) { putString(KEY_QUEUE, TelemetryQueue.encode(events)) }
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Telemetry failed, ignored", e)
        }
    }

    private companion object {
        const val TAG = "Telemetry"
        const val APP_OPENED_INTERVAL_MILLIS = 30L * 60 * 1000
        const val KEY_QUEUE = "queue"
        const val KEY_LAST_APP_OPENED = "last_app_opened"
        const val KEY_SETUP_STEPS = "setup_steps"
        const val KEY_SETUP_COMPLETED = "setup_completed"
    }
}
