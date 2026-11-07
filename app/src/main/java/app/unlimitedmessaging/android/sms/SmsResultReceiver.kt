package app.unlimitedmessaging.android.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import app.unlimitedmessaging.android.container

/**
 * Receives the per-part results of the SMS the gateway sends: "sent" (the
 * radio accepted or refused the part) and "delivered" (the recipient's
 * network delivery report, when the carrier provides one).
 */
class SmsResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
        val part = intent.getIntExtra(EXTRA_PART, 0)
        val sender = context.container.smsSender

        when (intent.action) {
            ACTION_SENT -> sender.onPartSent(messageId, part, resultCode, ok = resultCode == Activity.RESULT_OK)
            ACTION_DELIVERED -> {
                val pdu = intent.getByteArrayExtra("pdu") ?: return
                val format = intent.getStringExtra("format")
                val report = SmsMessage.createFromPdu(pdu, format) ?: return
                sender.onPartDelivery(messageId, part, classifyDeliveryStatus(report.status, format))
            }
        }
    }

    companion object {
        const val ACTION_SENT = "app.unlimitedmessaging.android.SMS_SENT"
        const val ACTION_DELIVERED = "app.unlimitedmessaging.android.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "messageId"
        const val EXTRA_PART = "part"
    }
}
