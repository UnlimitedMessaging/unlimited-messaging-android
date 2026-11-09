package app.unlimitedmessaging.android.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.unlimitedmessaging.android.BuildConfig
import app.unlimitedmessaging.android.auth.AuthState
import app.unlimitedmessaging.android.system.BackgroundSettings
import app.unlimitedmessaging.android.system.Permissions
import app.unlimitedmessaging.android.ui.screens.HomeActions
import app.unlimitedmessaging.android.ui.screens.HomeScreen
import app.unlimitedmessaging.android.ui.screens.SignInScreen
import app.unlimitedmessaging.android.ui.theme.GatewayTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), ::onPermissionsResult)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GatewayTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                if (state.auth == AuthState.SignedIn) {
                    HomeScreen(
                        state = state,
                        deviceName = viewModel.deviceName,
                        versionName = BuildConfig.VERSION_NAME,
                        actions = HomeActions(
                            setGatewayEnabled = viewModel::setGatewayEnabled,
                            setTelemetryEnabled = viewModel::setTelemetryEnabled,
                            grantSms = { request(Permissions.SMS) },
                            grantPhoneNumbers = { request(Permissions.PHONE_NUMBERS) },
                            grantNotifications = { request(Permissions.NOTIFICATIONS) },
                            requestBatteryExemption = {
                                open(BackgroundSettings.requestIgnoreBatteryOptimizationsIntent(this))
                            },
                            openManufacturerSettings = { open(BackgroundSettings.manufacturerSettingsIntent(this)) },
                            openManufacturerGuide = { open(Intent(Intent.ACTION_VIEW, Uri.parse(BackgroundSettings.manufacturerGuideUrl))) },
                            openAppSettings = {
                                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                            },
                            signOut = viewModel::signOut,
                        ),
                    )
                } else {
                    SignInScreen(
                        state = state.auth,
                        error = state.authError,
                        apiBaseUrl = viewModel.apiBaseUrl.takeIf { BuildConfig.DEBUG },
                        onSignIn = { viewModel.signIn(::openSignInPage) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions and battery settings may have changed in system settings.
        viewModel.onResume()
    }

    /**
     * The website's sign-in page in a Custom Tab: the user's own browser,
     * with their existing Google/GitHub sessions and password manager, but
     * on top of the app.
     */
    private fun openSignInPage(uri: Uri) {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
    }

    private fun request(permissions: Array<String>) {
        if (permissions.isNotEmpty()) permissionRequest.launch(permissions)
    }

    /**
     * The result map is keyed by the permissions that were requested, which
     * tells which group of the checklist it was. Empty when the request was
     * interrupted (screen rotated): nothing to report then.
     */
    private fun onPermissionsResult(results: Map<String, Boolean>) {
        val group = when {
            results.isEmpty() -> null
            Permissions.SMS.any(results::containsKey) -> "sms"
            Permissions.PHONE_NUMBERS.any(results::containsKey) -> "phone_numbers"
            Permissions.NOTIFICATIONS.any(results::containsKey) -> "notifications"
            else -> null
        }
        if (group == null) {
            viewModel.refresh()
            return
        }
        val denied = results.filterValues { !it }.keys
        // After a denial, Android stops offering the dialog once it no longer wants a rationale shown.
        val permanentlyDenied = denied.isNotEmpty() && denied.any { !shouldShowRequestPermissionRationale(it) }
        viewModel.onPermissionResult(group, granted = denied.isEmpty(), permanentlyDenied = permanentlyDenied)
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
}
