package app.unlimitedmessaging.android.work

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import app.unlimitedmessaging.android.api.ApiException
import app.unlimitedmessaging.android.api.DeviceRemovedException
import app.unlimitedmessaging.android.api.UnauthorizedException
import app.unlimitedmessaging.android.auth.AuthRepository
import java.io.IOException
import java.util.concurrent.TimeUnit

/** After this many attempts (days, with backoff) a report is dropped. */
private const val MAX_ATTEMPTS = 30

/** Only run with a network, retry with exponential backoff from 30 s (WorkManager caps it at 5 h). */
internal fun OneTimeWorkRequest.Builder.forReport(): OneTimeWorkRequest.Builder = this
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)

internal enum class ReportOutcome {
    /** The server has it. */
    DONE,

    /** May succeed later: WorkManager retries with backoff. */
    RETRY,

    /** Will never succeed (signed out, refused, too many attempts): dropped. */
    DROPPED;

    fun toResult(): Result = when (this) {
        DONE -> Result.success()
        RETRY -> Result.retry()
        DROPPED -> Result.failure()
    }
}

/**
 * Runs one upload to the API and classifies the outcome: retry what may
 * succeed later, drop what never will.
 */
internal suspend fun runReport(tag: String, attempt: Int, auth: AuthRepository, call: suspend () -> Unit): ReportOutcome {
    if (!auth.isSignedIn) return ReportOutcome.DROPPED
    return try {
        call()
        ReportOutcome.DONE
    } catch (e: DeviceRemovedException) {
        auth.onSessionExpired()
        ReportOutcome.DROPPED
    } catch (e: UnauthorizedException) {
        // Signed out by the refresh: drop. Still signed in: refresh was only unavailable.
        if (auth.isSignedIn) retryOrGiveUp(tag, attempt, e) else ReportOutcome.DROPPED
    } catch (e: ApiException) {
        when (e.status) {
            408, 425, 429 -> retryOrGiveUp(tag, attempt, e)
            // The server will refuse it identically: unknown message, invalid body.
            in 400..499 -> {
                Log.w(tag, "Dropped after HTTP ${e.status}", e)
                ReportOutcome.DROPPED
            }
            else -> retryOrGiveUp(tag, attempt, e)
        }
    } catch (e: IOException) {
        retryOrGiveUp(tag, attempt, e)
    }
}

private fun retryOrGiveUp(tag: String, attempt: Int, cause: Exception): ReportOutcome =
    if (attempt + 1 >= MAX_ATTEMPTS) {
        Log.w(tag, "Giving up after $MAX_ATTEMPTS attempts", cause)
        ReportOutcome.DROPPED
    } else {
        Log.i(tag, "Will retry (attempt ${attempt + 1}): ${cause.message}")
        ReportOutcome.RETRY
    }
