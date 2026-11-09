package app.unlimitedmessaging.android.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.unlimitedmessaging.android.analytics.StartTrigger
import app.unlimitedmessaging.android.service.GatewayController
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes (WorkManager's minimum), restarts the gateway service if
 * the system or the manufacturer's battery manager killed it.
 *
 * A safety net, not the main mechanism: the service is sticky and restarts
 * on boot. See docs/RELIABILITY.md.
 */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        GatewayController.ensureRunning(applicationContext, StartTrigger.WATCHDOG)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("gateway-watchdog", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
