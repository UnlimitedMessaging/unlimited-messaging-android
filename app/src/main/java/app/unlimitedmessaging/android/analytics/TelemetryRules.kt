package app.unlimitedmessaging.android.analytics

/** The items of the home screen checklist, with their name in `android_setup_step_*` events. */
enum class SetupStep(val wireName: String) {
    SMS("sms"),
    BATTERY("battery"),
    NOTIFICATIONS("notifications"),
    PHONE_NUMBERS("phone_numbers"),
}

data class StepDiff(val completed: List<SetupStep>, val revoked: List<SetupStep>) {
    val isEmpty: Boolean get() = completed.isEmpty() && revoked.isEmpty()
}

/**
 * What changed in the checklist since the last time the app looked. With no
 * previous observation (first launch, or the first launch of a version with
 * telemetry) there is nothing to compare to: no event, only a baseline.
 */
fun diffSetupSteps(previous: Set<SetupStep>?, current: Set<SetupStep>): StepDiff {
    if (previous == null) return StepDiff(emptyList(), emptyList())
    return StepDiff(
        completed = SetupStep.entries.filter { it in current && it !in previous },
        revoked = SetupStep.entries.filter { it in previous && it !in current },
    )
}

/** Where a start of the gateway service was attempted from. */
enum class StartTrigger(val wireName: String) {
    UI("ui"),
    BOOT("boot"),
    WATCHDOG("watchdog"),
    SIGN_IN("sign_in"),
}

/**
 * Turns the gateway loop's failures into one episode: the first failure
 * opens it, the next successful contact with the server closes it. Never one
 * event per retry, so an outage of an hour is a single event.
 */
class OfflineEpisodeTracker {
    data class Episode(val durationSeconds: Long, val errorClass: String, val attempts: Int)

    private var startedAt: Long? = null
    private var errorClass: String = ""
    private var attempts = 0

    fun onFailure(error: Throwable, now: Long) {
        if (startedAt == null) {
            startedAt = now
            errorClass = error.javaClass.simpleName.ifEmpty { "Unknown" }
        }
        attempts++
    }

    /** The server answered. Returns the episode that just ended, if there was one. */
    fun onContact(now: Long): Episode? {
        val start = startedAt ?: return null
        val episode = Episode(((now - start) / 1000).coerceAtLeast(0), errorClass, attempts)
        startedAt = null
        attempts = 0
        return episode
    }
}

/**
 * What a crash report keeps: the exception's class and where in the app's
 * own code it happened. Never the message, which can hold anything (a phone
 * number, a URL, message text).
 */
object CrashSummary {
    private const val APP_PACKAGE = "app.unlimitedmessaging."
    const val MAX_FRAMES = 5

    /** The deepest cause: `RuntimeException: Unable to start service` usually wraps the real one. */
    fun rootCause(error: Throwable): Throwable {
        var current = error
        val seen = HashSet<Throwable>()
        while (true) {
            val cause = current.cause ?: return current
            if (!seen.add(current)) return current
            current = cause
        }
    }

    fun exceptionClass(error: Throwable): String = rootCause(error).javaClass.simpleName.ifEmpty { "Unknown" }

    /** Up to [MAX_FRAMES] frames of the app's own code, from the root cause outwards, as `Class#method`. */
    fun appFrames(error: Throwable): List<String> {
        val chain = generateSequence(error) { it.cause }.take(10).toList().asReversed()
        return chain.asSequence()
            .flatMap { it.stackTrace.asSequence() }
            .filter { it.className.startsWith(APP_PACKAGE) }
            .map { "${it.className.substringAfterLast('.')}#${it.methodName}" }
            .distinct()
            .take(MAX_FRAMES)
            .toList()
    }
}
