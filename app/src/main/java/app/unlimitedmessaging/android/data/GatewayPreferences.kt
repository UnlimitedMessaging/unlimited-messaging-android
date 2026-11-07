package app.unlimitedmessaging.android.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Small, non-secret settings. Secrets (tokens) live in
 * [app.unlimitedmessaging.android.auth.TokenStore] instead.
 */
class GatewayPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("gateway", Context.MODE_PRIVATE)

    private val _gatewayEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))

    /**
     * Whether the user wants the gateway running. Signing in turns it on; the
     * switch on the home screen pauses it without signing out.
     */
    val gatewayEnabled: StateFlow<Boolean> = _gatewayEnabled.asStateFlow()

    fun setGatewayEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        _gatewayEnabled.value = enabled
    }

    /**
     * A random id for this installation, generated once. The backend keys the
     * device record on it, so registering again after a restart updates the
     * same device instead of creating a new one. Reinstalling the app creates
     * a new id, hence a new device.
     */
    val installationId: String
        @Synchronized get() = prefs.getString(KEY_INSTALLATION_ID, null)
            ?: UUID.randomUUID().toString().also { prefs.edit { putString(KEY_INSTALLATION_ID, it) } }

    private companion object {
        const val KEY_ENABLED = "gateway_enabled"
        const val KEY_INSTALLATION_ID = "installation_id"
    }
}
