package app.unlimitedmessaging.android.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** The runtime permissions the app asks for, grouped the way the home screen presents them. */
object Permissions {
    /** Required for the gateway to run: send SMS, receive SMS, list SIMs. */
    val SMS: Array<String> = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_PHONE_STATE,
    )

    /** Optional: lets the app read each SIM's number when the carrier provides it. */
    val PHONE_NUMBERS: Array<String> = arrayOf(Manifest.permission.READ_PHONE_NUMBERS)

    /** Optional (Android 13+): lets the status and alert notifications show. */
    val NOTIFICATIONS: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    fun allGranted(context: Context, permissions: Array<String>): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
