package app.unlimitedmessaging.android.sms

import android.telephony.SmsManager

/** Error codes the app reports to the backend, beyond the radio's own (see [radioError]). */
object AppErrorCodes {
    /** The SIM the message was queued for is no longer in the phone. */
    const val SIM_NOT_AVAILABLE = "SIM_NOT_AVAILABLE"

    /** The user revoked the SMS permission. */
    const val PERMISSION_DENIED = "PERMISSION_DENIED"

    /** The message reached the phone after its `expiresAt`. */
    const val EXPIRED = "EXPIRED"

    /** Android refused the request outright (empty text, malformed number...). */
    const val INVALID_MESSAGE = "INVALID_MESSAGE"

    /** The recipient's network reported a permanent delivery failure. */
    const val DELIVERY_FAILED = "DELIVERY_FAILED"
}

/**
 * Names the result code of a "sent" PendingIntent, so the backend and its
 * users get `NO_SERVICE` rather than `4`.
 */
fun radioError(resultCode: Int): String = when (resultCode) {
    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "GENERIC_FAILURE"
    SmsManager.RESULT_ERROR_RADIO_OFF -> "RADIO_OFF"
    SmsManager.RESULT_ERROR_NULL_PDU -> "NULL_PDU"
    SmsManager.RESULT_ERROR_NO_SERVICE -> "NO_SERVICE"
    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "LIMIT_EXCEEDED"
    SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "FDN_CHECK_FAILURE"
    SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "SHORT_CODE_NOT_ALLOWED"
    SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "SHORT_CODE_NEVER_ALLOWED"
    else -> "RADIO_ERROR_$resultCode"
}

/** How the recipient's network classified a delivery report. */
enum class DeliveryOutcome { DELIVERED, PENDING, FAILED }

/**
 * Reads the status of a delivery report PDU (`SmsMessage.getStatus()`).
 *
 * - 3GPP (GSM/UMTS/LTE), TP-Status from 3GPP TS 23.040 9.2.3.15:
 *   0x00-0x1F completed, 0x20-0x3F still trying, 0x40 and up failed.
 * - 3GPP2 (CDMA): Android puts the error class in bits 24-25:
 *   0 no error, 2 temporary, 3 permanent.
 */
fun classifyDeliveryStatus(status: Int, format: String?): DeliveryOutcome =
    if (format == "3gpp2") {
        when ((status shr 24) and 0x03) {
            0 -> DeliveryOutcome.DELIVERED
            2 -> DeliveryOutcome.PENDING
            else -> DeliveryOutcome.FAILED
        }
    } else {
        when {
            status < 0x20 -> DeliveryOutcome.DELIVERED
            status < 0x40 -> DeliveryOutcome.PENDING
            else -> DeliveryOutcome.FAILED
        }
    }
