package app.unlimitedmessaging.android.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import app.unlimitedmessaging.android.container
import app.unlimitedmessaging.android.data.ActivityEntry
import app.unlimitedmessaging.android.data.ActivityLog
import app.unlimitedmessaging.android.data.Direction
import app.unlimitedmessaging.android.data.EntryStatus
import app.unlimitedmessaging.android.work.InboundSmsWorker
import java.time.Instant

/**
 * Forwards every SMS the phone receives, from the moment the app is signed
 * in. Older messages already on the phone are not read or uploaded.
 *
 * The phone's own SMS app still receives and stores the message as usual:
 * this receiver only reads the broadcast, it does not consume it.
 *
 * The upload is queued in WorkManager so it is not lost when the network is
 * down or the process dies right after this returns.
 */
class SmsReceivedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val container = context.container
        if (!container.auth.isSignedIn) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val subscriptionId = subscriptionIdOf(intent)
        val receivedAt = Instant.now().toString()

        // One broadcast carries every part of a long SMS, possibly from
        // several senders at once: regroup them per sender.
        parts.groupBy { it.displayOriginatingAddress.orEmpty() }.forEach { (from, messageParts) ->
            val text = messageParts.joinToString("") { it.displayMessageBody.orEmpty() }
            val sentAtMillis = messageParts.minOf { it.timestampMillis }
            val sentAt = Instant.ofEpochMilli(sentAtMillis).toString()
            val dedupKey = inboundDedupKey(subscriptionId, from, sentAtMillis, text)

            Log.i(TAG, "SMS received on subscription $subscriptionId (${text.length} chars), queued for upload")
            container.activityLog.upsert(
                ActivityEntry(
                    id = dedupKey,
                    direction = Direction.INCOMING,
                    address = from,
                    preview = ActivityLog.preview(text),
                    status = EntryStatus.RECEIVED,
                    subscriptionId = subscriptionId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            InboundSmsWorker.enqueue(context, subscriptionId, from, text, sentAt, receivedAt, dedupKey)
        }
    }

    /** Which SIM received it. Older devices use the "subscription" extra. */
    private fun subscriptionIdOf(intent: Intent): Int {
        val id = intent.getIntExtra(EXTRA_SUBSCRIPTION_INDEX, INVALID)
            .takeIf { it != INVALID }
            ?: intent.getIntExtra(EXTRA_SUBSCRIPTION_LEGACY, INVALID)
        return if (id != INVALID) id else SubscriptionManager.getDefaultSmsSubscriptionId()
    }

    private companion object {
        const val TAG = "SmsReceivedReceiver"
        const val EXTRA_SUBSCRIPTION_INDEX = "android.telephony.extra.SUBSCRIPTION_INDEX"
        const val EXTRA_SUBSCRIPTION_LEGACY = "subscription"
        const val INVALID = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }
}
