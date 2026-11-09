package app.unlimitedmessaging.android.service

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import app.unlimitedmessaging.android.analytics.StartTrigger
import app.unlimitedmessaging.android.container
import app.unlimitedmessaging.android.system.Permissions
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one place that decides whether the gateway service should run, and
 * starts or stops it accordingly. Every entry point (screen, boot, watchdog,
 * sign-in, sign-out) goes through here.
 */
object GatewayController {
    private const val TAG = "GatewayController"

    /** Signed in, switched on, and allowed to send SMS and list SIMs. */
    fun shouldRun(context: Context): Boolean {
        val container = context.container
        return container.auth.isSignedIn &&
            container.preferences.gatewayEnabled.value &&
            Permissions.allGranted(context, Permissions.SMS)
    }

    /**
     * Starts the service if it should run and does not, stops it if it should not.
     *
     * @param trigger where the call comes from, reported when Android refuses the start.
     */
    fun ensureRunning(context: Context, trigger: StartTrigger) {
        if (!shouldRun(context)) {
            if (GatewayService.isRunning) stop(context)
            return
        }
        if (GatewayService.isRunning) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, GatewayService::class.java))
            GatewayNotifications.cancelRestartNeeded(context)
        } catch (e: IllegalStateException) {
            // Android 12+ refuses to start a foreground service from the
            // background unless the app is exempt from battery optimization.
            // Ask the user to open the app, which is always allowed.
            val notAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            Log.w(TAG, "Could not start the gateway service (background start refused: $notAllowed)", e)
            GatewayNotifications.showRestartNeeded(context)
            context.container.telemetry.serviceStartBlocked(trigger, isBackgroundRestricted(context))
        }
    }

    /** "Restricted" background usage in the app's battery settings (Android 9+). */
    private fun isBackgroundRestricted(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            runCatching { context.getSystemService(ActivityManager::class.java).isBackgroundRestricted }.getOrDefault(false)

    /** Asks a running gateway to register again (SIMs or permissions changed). */
    fun refreshRegistration(context: Context) {
        if (!GatewayService.isRunning) return
        try {
            context.startService(Intent(context, GatewayService::class.java).setAction(GatewayService.ACTION_REFRESH))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not ask the gateway to register again", e)
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, GatewayService::class.java))
    }

    /**
     * Ends the session: stops the gateway, removes this device on the server
     * (its accounts stop accepting messages), revokes the tokens, and clears
     * what the phone kept locally.
     *
     * The server calls are best effort with a timeout: signing out works
     * offline too, the device then shows as offline on the website until the
     * user removes it there.
     */
    suspend fun signOut(context: Context) {
        val container = context.container
        stop(context)
        withTimeoutOrNull(10_000) {
            runCatching { container.deviceApi.unregister() }
                .onFailure { Log.w(TAG, "Could not unregister the device", it) }
        }
        container.auth.signOut()
        container.telemetry.clear()
        container.preferences.signedInAt = null
        container.activityLog.clear()
        container.gatewayStatus.reset()
    }
}
