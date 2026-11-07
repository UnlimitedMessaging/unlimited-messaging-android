package app.unlimitedmessaging.android.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.unlimitedmessaging.android.api.OutboxMessage
import app.unlimitedmessaging.android.api.ReportedStatus
import app.unlimitedmessaging.android.data.ActivityEntry
import app.unlimitedmessaging.android.data.ActivityLog
import app.unlimitedmessaging.android.data.Direction
import app.unlimitedmessaging.android.data.EntryStatus
import app.unlimitedmessaging.android.sim.SimInventory
import app.unlimitedmessaging.android.work.StatusReportWorker
import java.time.Instant

/**
 * Sends outbox messages through the right SIM and turns the radio's answers
 * into status reports for the backend.
 *
 * Reports are queued in WorkManager, not sent inline: they then survive the
 * app being killed, the phone rebooting, or the network being down when the
 * result arrives.
 */
class SmsSender(
    private val context: Context,
    private val sims: SimInventory,
    private val tracker: SendTracker,
    private val activityLog: ActivityLog,
) {
    fun canSend(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun send(message: OutboxMessage) {
        // Handed out again (our previous report was lost): never send twice,
        // just repeat what we know.
        tracker.get(message.id)?.let { existing ->
            Log.i(TAG, "Message ${message.id} already handled (${existing.reported}), not sending again")
            existing.reported?.let { report(message.id, it, existing.errorCode, existing.parts) }
            return
        }

        val expiresAt = message.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        when {
            !canSend() -> return failBeforeSending(message, AppErrorCodes.PERMISSION_DENIED)
            expiresAt != null && expiresAt.isBefore(Instant.now()) ->
                return failBeforeSending(message, AppErrorCodes.EXPIRED)
            sims.find(message.subscriptionId) == null ->
                return failBeforeSending(message, AppErrorCodes.SIM_NOT_AVAILABLE)
        }

        val manager = smsManagerFor(message.subscriptionId)
        try {
            val parts = manager.divideMessage(message.text)
            tracker.start(message.id, parts.size)
            log(message, EntryStatus.SENDING)
            manager.sendMultipartTextMessage(
                message.to,
                null,
                parts,
                ArrayList(parts.indices.map { resultIntent(SmsResultReceiver.ACTION_SENT, message.id, it) }),
                ArrayList(parts.indices.map { resultIntent(SmsResultReceiver.ACTION_DELIVERED, message.id, it) }),
            )
            Log.i(TAG, "Message ${message.id} handed to the radio in ${parts.size} part(s)")
        } catch (e: Exception) {
            // IllegalArgumentException (empty text, bad number) or SecurityException.
            Log.w(TAG, "Android refused message ${message.id}", e)
            if (tracker.get(message.id) == null) tracker.start(message.id, 1)
            applyTransition(message.id) { it.onPartFailed(0, AppErrorCodes.INVALID_MESSAGE) }
            if (activityLog.entries.value.none { it.id == message.id }) {
                log(message, EntryStatus.FAILED, AppErrorCodes.INVALID_MESSAGE)
            }
        }
    }

    /** Called by [SmsResultReceiver] for each "sent" callback. */
    fun onPartSent(messageId: String, part: Int, resultCode: Int, ok: Boolean) {
        applyTransition(messageId) { record ->
            if (ok) record.onPartSent(part) else record.onPartFailed(part, radioError(resultCode))
        }
    }

    /** Called by [SmsResultReceiver] for each delivery report. */
    fun onPartDelivery(messageId: String, part: Int, outcome: DeliveryOutcome) {
        applyTransition(messageId) { record ->
            when (outcome) {
                DeliveryOutcome.DELIVERED -> record.onPartDelivered(part)
                DeliveryOutcome.FAILED -> record.onPartUndeliverable(part, AppErrorCodes.DELIVERY_FAILED)
                // The network is still trying; a final report follows.
                DeliveryOutcome.PENDING -> SendRecord.Transition(record)
            }
        }
    }

    private fun applyTransition(messageId: String, change: (SendRecord) -> SendRecord.Transition) {
        val transition = tracker.update(messageId, change) ?: return
        for (status in transition.emitted) {
            report(messageId, status, transition.record.errorCode, transition.record.parts)
            activityLog.setStatus(messageId, status.toEntryStatus(), transition.record.errorCode)
        }
    }

    private fun failBeforeSending(message: OutboxMessage, code: String) {
        Log.w(TAG, "Message ${message.id} not sent: $code")
        tracker.start(message.id, 1)
        applyTransition(message.id) { it.onPartFailed(0, code) }
        log(message, EntryStatus.FAILED, code)
    }

    private fun report(messageId: String, status: String, errorCode: String?, parts: Int) {
        StatusReportWorker.enqueue(
            context,
            messageId = messageId,
            status = status,
            occurredAt = Instant.now().toString(),
            errorCode = errorCode.takeIf { status == ReportedStatus.FAILED || status == ReportedStatus.UNDELIVERABLE },
            parts = parts,
        )
    }

    private fun log(message: OutboxMessage, status: EntryStatus, errorCode: String? = null) {
        activityLog.upsert(
            ActivityEntry(
                id = message.id,
                direction = Direction.OUTGOING,
                address = message.to,
                preview = ActivityLog.preview(message.text),
                status = status,
                subscriptionId = message.subscriptionId,
                updatedAt = System.currentTimeMillis(),
                errorCode = errorCode,
            ),
        )
    }

    /**
     * One PendingIntent per part and per kind. The data URI makes each one
     * distinct (PendingIntents that only differ by extras are the same one).
     *
     * Mutable on purpose: the system attaches the result to it (the delivery
     * report PDU travels as an extra). The intent is explicit, aimed at our
     * own non-exported receiver, so nothing else can be substituted.
     */
    private fun resultIntent(action: String, messageId: String, part: Int): PendingIntent {
        val intent = Intent(context, SmsResultReceiver::class.java)
            .setAction(action)
            .setData(
                Uri.Builder().scheme("sms-result").authority(action.substringAfterLast('.'))
                    .appendPath(messageId).appendPath(part.toString()).build(),
            )
            .putExtra(SmsResultReceiver.EXTRA_MESSAGE_ID, messageId)
            .putExtra(SmsResultReceiver.EXTRA_PART, part)
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutability)
    }

    private fun smsManagerFor(subscriptionId: Int): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }

    private fun String.toEntryStatus() = when (this) {
        ReportedStatus.SENT -> EntryStatus.SENT
        ReportedStatus.DELIVERED -> EntryStatus.DELIVERED
        ReportedStatus.UNDELIVERABLE -> EntryStatus.UNDELIVERABLE
        else -> EntryStatus.FAILED
    }

    private companion object {
        const val TAG = "SmsSender"
    }
}
