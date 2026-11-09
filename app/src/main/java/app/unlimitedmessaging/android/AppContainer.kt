package app.unlimitedmessaging.android

import android.content.Context
import app.unlimitedmessaging.android.analytics.Telemetry
import app.unlimitedmessaging.android.api.ApiClient
import app.unlimitedmessaging.android.api.DeviceApi
import app.unlimitedmessaging.android.auth.AuthRepository
import app.unlimitedmessaging.android.auth.OAuthClient
import app.unlimitedmessaging.android.auth.TokenStore
import app.unlimitedmessaging.android.config.AppConfig
import app.unlimitedmessaging.android.data.ActivityLog
import app.unlimitedmessaging.android.data.GatewayPreferences
import app.unlimitedmessaging.android.device.DeviceIdentity
import app.unlimitedmessaging.android.service.GatewayStatus
import app.unlimitedmessaging.android.sim.SimInventory
import app.unlimitedmessaging.android.sms.SendTracker
import app.unlimitedmessaging.android.sms.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-wired dependencies: one instance of each per process.
 *
 * The graph is small enough that a DI framework would add more indirection
 * than it removes. Components get what they need through their constructor,
 * so each stays testable on its own.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Work that must outlive the screen that started it (sign-in, sign-out). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val config = AppConfig.fromBuildConfig()
    val preferences = GatewayPreferences(appContext)
    val tokenStore = TokenStore(appContext)
    val deviceIdentity = DeviceIdentity(appContext, preferences)
    val simInventory = SimInventory(appContext)
    val activityLog = ActivityLog(appContext)
    val sendTracker = SendTracker(appContext)
    val gatewayStatus = GatewayStatus()

    private val oauthClient = OAuthClient(config, ApiClient.baseHttpClient())
    val auth = AuthRepository(appContext, config, oauthClient, tokenStore, preferences)

    private val apiClient = ApiClient(config, tokenStore, auth)
    val deviceApi = DeviceApi(apiClient)

    val smsSender = SmsSender(appContext, simInventory, sendTracker, activityLog)

    val telemetry = Telemetry(appContext, preferences, auth, appScope, enabledInBuild = BuildConfig.TELEMETRY_ENABLED)
}

/** The process-wide [AppContainer], from any [Context]. */
val Context.container: AppContainer
    get() = (applicationContext as GatewayApp).container
