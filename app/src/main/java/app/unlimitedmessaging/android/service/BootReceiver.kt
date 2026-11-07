package app.unlimitedmessaging.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Brings the gateway back after a reboot or an app update. Starting a
 * foreground service from BOOT_COMPLETED is explicitly allowed for the
 * `specialUse` type.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                GatewayController.ensureRunning(context)
        }
    }
}
