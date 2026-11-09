package app.unlimitedmessaging.android.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.unlimitedmessaging.android.AppContainer
import app.unlimitedmessaging.android.analytics.OfflineEpisodeTracker
import app.unlimitedmessaging.android.api.DeviceRemovedException
import app.unlimitedmessaging.android.api.OutboxMessage
import app.unlimitedmessaging.android.api.UnauthorizedException
import app.unlimitedmessaging.android.container
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Keeps the phone connected to the backend so queued SMS go out right away.
 *
 * The loop:
 * 1. register the device and its SIMs (again whenever the SIMs change);
 * 2. long-poll `GET /device/outbox`: the server holds the request until a
 *    message is queued for one of this phone's SIMs, or 30 s pass;
 * 3. send whatever came back, then go to 2.
 *
 * Long-polling rather than a push service: no Google Play Services or
 * Firebase dependency, it works on any Android phone, and the backend stays
 * stateless (any API instance can answer any poll). The price is a
 * foreground service with its permanent notification, which is also what
 * keeps Android from killing the process.
 */
class GatewayService : LifecycleService() {

    private val container: AppContainer by lazy { applicationContext.container }

    private var loop: Job? = null
    private val registrationNeeded = AtomicBoolean(true)

    /** Cuts a backoff short when there is a reason to retry now (network back, SIM change). */
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    private var stopObservingSims: (() -> Unit)? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        enterForeground()

        stopObservingSims = container.simInventory.observe(ContextCompat.getMainExecutor(this)) {
            registrationNeeded.set(true)
            wakeUp.trySend(Unit)
        }
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wakeUp.trySend(Unit)
            }
        }.also { getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(it) }

        // Keep the notification in step with the connection state.
        lifecycleScope.launch {
            container.gatewayStatus.snapshot
                .distinctUntilChangedBy { it.connection to it.accounts.size }
                .collect { snapshot -> GatewayNotifications.updateStatus(this@GatewayService, snapshot) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        enterForeground()
        if (intent?.action == ACTION_REFRESH) {
            registrationNeeded.set(true)
            currentPoll?.cancel()
        }
        if (loop?.isActive != true) {
            loop = lifecycleScope.launch(Dispatchers.IO) { runLoop() }
        }
        // Restarted by the system with a null intent if it kills the process.
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        stopObservingSims?.invoke()
        networkCallback?.let { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        container.gatewayStatus.stopped()
        super.onDestroy()
    }

    private fun enterForeground() {
        val notification = GatewayNotifications.status(this, container.gatewayStatus.snapshot.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                GatewayNotifications.STATUS_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(GatewayNotifications.STATUS_ID, notification)
        }
    }

    private suspend fun runLoop() {
        val backoff = Backoff()
        val offline = OfflineEpisodeTracker()
        container.gatewayStatus.connecting()

        while (coroutineContext.isActive) {
            if (!GatewayController.shouldRun(this)) {
                Log.i(TAG, "Gateway should not run any more (signed out, paused or permission revoked), stopping")
                stopSelf()
                return
            }
            try {
                if (registrationNeeded.getAndSet(false)) {
                    register()
                    reportRecovery(offline)
                }
                container.gatewayStatus.online()
                val messages = poll() ?: continue
                reportRecovery(offline)
                backoff.reset()
                container.gatewayStatus.online()
                if (messages.isNotEmpty()) send(messages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeviceRemovedException) {
                Log.i(TAG, "Device removed on the server, signing out")
                container.auth.onSessionExpired()
                stopSelf()
                return
            } catch (e: Exception) {
                // IOException (network, HTTP error) or an unreadable response:
                // either way, back off and retry rather than crash the service.
                if (e is UnauthorizedException && !container.auth.isSignedIn) {
                    stopSelf()
                    return
                }
                // Register again after any failure: cheap, and covers a
                // registration that was the request that failed.
                registrationNeeded.set(true)
                offline.onFailure(e, System.currentTimeMillis())
                val delay = backoff.next()
                Log.w(TAG, "Outbox unreachable (${e.message}), retrying in ${delay / 1000}s")
                container.gatewayStatus.offline(e.message ?: e.javaClass.simpleName)
                withTimeoutOrNull(delay) { wakeUp.receive() }
            }
        }
    }

    /** One telemetry event per outage, when the server answers again. */
    private fun reportRecovery(offline: OfflineEpisodeTracker) {
        offline.onContact(System.currentTimeMillis())?.let(container.telemetry::offlineEpisode)
    }

    /** The current outbox request, cancelled to register again right away. Null when cancelled that way. */
    @Volatile
    private var currentPoll: Deferred<List<OutboxMessage>>? = null

    private suspend fun poll(): List<OutboxMessage>? = coroutineScope {
        val request = async { container.deviceApi.pollOutbox(container.config.outboxWaitSeconds) }
        currentPoll = request
        try {
            request.await()
        } catch (e: CancellationException) {
            // Our own refresh cancelled the request, not the service stopping.
            if (!request.isCancelled || !isActive) throw e
            null
        } finally {
            currentPoll = null
        }
    }

    private suspend fun register() {
        val sims = container.simInventory.activeSims().map { it.toPayload() }
        val response = container.deviceApi.register(container.deviceIdentity.registration(sims))
        Log.i(TAG, "Registered device ${response.deviceId} with ${response.accounts.size} SIM account(s)")
        container.gatewayStatus.registered(response.accounts)
    }

    /**
     * Hands every message to the radio. A partial wake lock keeps the CPU up
     * until they are all handed over, in case the screen is off; the radio
     * reports results on its own afterwards.
     */
    private fun send(messages: List<OutboxMessage>) {
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:send")
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        try {
            messages.forEach(container.smsSender::send)
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    companion object {
        private const val TAG = "GatewayService"
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L
        const val ACTION_REFRESH = "app.unlimitedmessaging.android.action.REFRESH_REGISTRATION"

        /** True between onCreate and onDestroy, in this process. */
        @Volatile
        var isRunning = false
            private set
    }
}
