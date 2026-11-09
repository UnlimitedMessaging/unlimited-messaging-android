package app.unlimitedmessaging.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.unlimitedmessaging.android.analytics.SetupStep
import app.unlimitedmessaging.android.analytics.StartTrigger
import app.unlimitedmessaging.android.auth.AuthError
import app.unlimitedmessaging.android.auth.AuthState
import app.unlimitedmessaging.android.container
import app.unlimitedmessaging.android.data.ActivityEntry
import app.unlimitedmessaging.android.service.GatewayController
import app.unlimitedmessaging.android.service.GatewaySnapshot
import app.unlimitedmessaging.android.sim.SimCard
import app.unlimitedmessaging.android.system.BackgroundSettings
import app.unlimitedmessaging.android.system.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the system currently allows the app to do. Re-read every time the screen resumes. */
data class SetupState(
    val smsGranted: Boolean = false,
    val phoneNumbersGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryExempt: Boolean = false,
    val aggressiveManufacturer: Boolean = false,
) {
    val requiredDone: Boolean get() = smsGranted

    /** Every item of the checklist handled: it moves to the bottom of the screen. */
    val allDone: Boolean get() = smsGranted && phoneNumbersGranted && notificationsGranted && batteryExempt

    val completedSteps: Set<SetupStep>
        get() = buildSet {
            if (smsGranted) add(SetupStep.SMS)
            if (batteryExempt) add(SetupStep.BATTERY)
            if (notificationsGranted) add(SetupStep.NOTIFICATIONS)
            if (phoneNumbersGranted) add(SetupStep.PHONE_NUMBERS)
        }
}

data class UiState(
    val auth: AuthState,
    val authError: AuthError?,
    val gatewayEnabled: Boolean,
    /** "Share usage statistics", or null when this build has no telemetry (switch hidden). */
    val telemetryEnabled: Boolean?,
    val gateway: GatewaySnapshot,
    val setup: SetupState,
    val sims: List<SimCard>,
    val activity: List<ActivityEntry>,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val container = application.container

    val apiBaseUrl: String = container.config.apiBaseUrl
    val deviceName: String get() = container.deviceIdentity.displayName

    private val setup = MutableStateFlow(readSetup())
    private val sims = MutableStateFlow(container.simInventory.activeSims())

    private val session = combine(
        container.auth.state,
        container.auth.error,
        container.preferences.gatewayEnabled,
        container.preferences.telemetryEnabled,
    ) { a, e, g, t -> Session(a, e, g, t.takeIf { container.telemetry.enabledInBuild }) }
    private val device = combine(container.gatewayStatus.snapshot, setup, sims, container.activityLog.entries) { g, s, c, l ->
        Quad(g, s, c, l)
    }

    val state: StateFlow<UiState> = combine(session, device) { (auth, error, enabled, telemetry), (gateway, setup, sims, activity) ->
        UiState(auth, error, enabled, telemetry, gateway, setup, sims, activity)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UiState(
            container.auth.state.value, container.auth.error.value, container.preferences.gatewayEnabled.value,
            container.preferences.telemetryEnabled.value.takeIf { container.telemetry.enabledInBuild },
            container.gatewayStatus.snapshot.value, setup.value, sims.value, container.activityLog.entries.value,
        ),
    )

    init {
        // Start the gateway as soon as a sign-in completes.
        viewModelScope.launch {
            container.auth.state.filter { it == AuthState.SignedIn }.collect { refresh(StartTrigger.SIGN_IN) }
        }
    }

    fun signIn(openBrowser: (Uri) -> Unit) {
        viewModelScope.launch { container.auth.startSignIn(openBrowser) }
    }

    fun onResume() {
        container.auth.onAppResumed()
        refresh()
        container.telemetry.appOpened(
            gatewayEnabled = container.preferences.gatewayEnabled.value,
            connection = container.gatewayStatus.snapshot.value.connection.name.lowercase(),
            setupComplete = setup.value.allDone,
        )
    }

    /** Re-reads permissions, SIMs and settings, and starts or stops the gateway to match. */
    fun refresh(trigger: StartTrigger = StartTrigger.UI) {
        val previous = setup.value
        val current = readSetup()
        setup.value = current
        sims.value = container.simInventory.activeSims()
        container.telemetry.setupObserved(current.completedSteps, current.allDone)
        GatewayController.ensureRunning(app, trigger)
        // A permission granted since the last registration can reveal more
        // (each SIM's number): tell the server again.
        if (current != previous) GatewayController.refreshRegistration(app)
    }

    fun setGatewayEnabled(enabled: Boolean) {
        container.preferences.setGatewayEnabled(enabled)
        GatewayController.ensureRunning(app, StartTrigger.UI)
    }

    fun setTelemetryEnabled(enabled: Boolean) {
        container.preferences.setTelemetryEnabled(enabled)
        if (!enabled) container.telemetry.clear()
        // The server learns the choice from the registration (`telemetry`).
        GatewayController.refreshRegistration(app)
    }

    /** The answer to a permission request: [group] is `sms`, `phone_numbers` or `notifications`. */
    fun onPermissionResult(group: String, granted: Boolean, permanentlyDenied: Boolean) {
        container.telemetry.permissionResult(group, granted, permanentlyDenied)
        refresh()
    }

    fun signOut() {
        // App scope: finishes even if the screen goes away.
        container.appScope.launch { GatewayController.signOut(app) }
    }

    private fun readSetup() = SetupState(
        smsGranted = Permissions.allGranted(app, Permissions.SMS),
        phoneNumbersGranted = Permissions.allGranted(app, Permissions.PHONE_NUMBERS),
        notificationsGranted = Permissions.allGranted(app, Permissions.NOTIFICATIONS),
        batteryExempt = BackgroundSettings.isIgnoringBatteryOptimizations(app),
        aggressiveManufacturer = BackgroundSettings.hasAggressiveManufacturer,
    )

    private data class Session(val auth: AuthState, val error: AuthError?, val gatewayEnabled: Boolean, val telemetry: Boolean?)

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
