package app.unlimitedmessaging.android.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import app.unlimitedmessaging.android.BuildConfig
import app.unlimitedmessaging.android.api.RegisterDeviceRequest
import app.unlimitedmessaging.android.api.SimPayload
import app.unlimitedmessaging.android.data.GatewayPreferences

/** Describes this phone to the backend when it registers. */
class DeviceIdentity(
    private val context: Context,
    private val preferences: GatewayPreferences,
) {
    /** The name the user gave the phone in system settings, else its model. */
    val displayName: String
        get() = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER.replaceFirstChar(Char::uppercaseChar)} ${Build.MODEL}"

    fun registration(sims: List<SimPayload>) = RegisterDeviceRequest(
        installationId = preferences.installationId,
        name = displayName,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidSdk = Build.VERSION.SDK_INT,
        appVersion = BuildConfig.VERSION_NAME,
        sims = sims,
        telemetry = BuildConfig.TELEMETRY_ENABLED && preferences.telemetryEnabled.value,
    )
}
