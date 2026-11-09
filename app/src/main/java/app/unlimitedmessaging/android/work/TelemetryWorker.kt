package app.unlimitedmessaging.android.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.unlimitedmessaging.android.api.ApiException
import app.unlimitedmessaging.android.api.DeviceRemovedException
import app.unlimitedmessaging.android.api.UnauthorizedException
import app.unlimitedmessaging.android.container
import java.io.IOException

/**
 * Sends the queued telemetry events, [app.unlimitedmessaging.android.analytics.TelemetryQueue.BATCH_SIZE] per request,
 * with the same network constraint and backoff as the message reports.
 *
 * Unlike a message report, an event is never worth more than the gateway:
 * a 410 here drops the events but does not sign the phone out (the gateway
 * loop handles a removed device on its own), and a 404 means a server
 * without the endpoint, so the whole queue goes.
 *
 * @see app.unlimitedmessaging.android.analytics.Telemetry
 */
class TelemetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val telemetry = container.telemetry
        if (!telemetry.shouldSend) {
            telemetry.dropQueue()
            return Result.success()
        }
        while (true) {
            val batch = telemetry.nextBatch()
            if (batch.isEmpty()) return Result.success()
            try {
                container.deviceApi.sendEvents(batch.map { it.toDeviceEvent() })
                telemetry.remove(batch)
            } catch (e: DeviceRemovedException) {
                telemetry.dropQueue()
                return Result.success()
            } catch (e: UnauthorizedException) {
                if (!container.auth.isSignedIn) {
                    telemetry.dropQueue()
                    return Result.success()
                }
                return retry(e)
            } catch (e: ApiException) {
                when (e.status) {
                    404, 410 -> {
                        telemetry.dropQueue()
                        return Result.success()
                    }
                    408, 425, 429 -> return retry(e)
                    in 400..499 -> {
                        // Refused as invalid: it would be refused again, drop this batch only.
                        Log.w(TAG, "Dropped ${batch.size} event(s) after HTTP ${e.status}")
                        telemetry.remove(batch)
                    }
                    else -> return retry(e)
                }
            } catch (e: IOException) {
                return retry(e)
            } catch (e: Exception) {
                Log.w(TAG, "Dropped ${batch.size} unsendable event(s)", e)
                telemetry.remove(batch)
            }
        }
    }

    private fun retry(cause: Exception): Result =
        if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
            // The queue stays: the next recorded event starts a new attempt.
            Log.i(TAG, "Giving up for now after $MAX_ATTEMPTS attempts: ${cause.message}")
            Result.success()
        } else {
            Result.retry()
        }

    companion object {
        private const val TAG = "TelemetryWorker"
        private const val WORK_NAME = "telemetry-flush"
        private const val MAX_ATTEMPTS = 15

        /** KEEP: a flush already waiting (or backing off) sends the new event with the rest. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TelemetryWorker>().forReport().build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
