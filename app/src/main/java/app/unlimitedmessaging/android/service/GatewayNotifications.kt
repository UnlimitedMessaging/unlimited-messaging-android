package app.unlimitedmessaging.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.unlimitedmessaging.android.R
import app.unlimitedmessaging.android.system.Permissions
import app.unlimitedmessaging.android.ui.MainActivity

/**
 * The two notification channels: the permanent one Android requires for a
 * foreground service (silent, low importance), and alerts that need the user
 * (signed out, service could not restart).
 */
object GatewayNotifications {
    private const val CHANNEL_STATUS = "gateway_status"
    private const val CHANNEL_ALERTS = "gateway_alerts"

    const val STATUS_ID = 1
    private const val SESSION_EXPIRED_ID = 2
    private const val RESTART_NEEDED_ID = 3

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_STATUS,
                    context.getString(R.string.notification_channel_status),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_status_description)
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_ALERTS,
                    context.getString(R.string.notification_channel_alerts),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    fun status(context: Context, snapshot: GatewaySnapshot): Notification {
        val text = when (snapshot.connection) {
            ConnectionState.ONLINE -> context.resources.getQuantityString(
                R.plurals.notification_status_online,
                snapshot.accounts.size,
                snapshot.accounts.size,
            )
            ConnectionState.OFFLINE -> context.getString(R.string.notification_status_offline)
            else -> context.getString(R.string.notification_status_connecting)
        }
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_status_title))
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /** Refreshes the foreground notification (no-op without POST_NOTIFICATIONS). */
    fun updateStatus(context: Context, snapshot: GatewaySnapshot) = notifySafely(context, STATUS_ID, status(context, snapshot))

    fun showSessionExpired(context: Context) = alert(
        context,
        SESSION_EXPIRED_ID,
        R.string.notification_session_expired_title,
        R.string.notification_session_expired_text,
    )

    fun cancelSessionExpired(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(SESSION_EXPIRED_ID)

    fun showRestartNeeded(context: Context) = alert(
        context,
        RESTART_NEEDED_ID,
        R.string.notification_restart_title,
        R.string.notification_restart_text,
    )

    fun cancelRestartNeeded(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(RESTART_NEEDED_ID)

    private fun alert(context: Context, id: Int, title: Int, text: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(text)))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        notifySafely(context, id, notification)
    }

    /** Skipped when POST_NOTIFICATIONS is denied: the home screen shows the same state. */
    private fun notifySafely(context: Context, id: Int, notification: Notification) {
        if (!Permissions.allGranted(context, Permissions.NOTIFICATIONS)) return
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
