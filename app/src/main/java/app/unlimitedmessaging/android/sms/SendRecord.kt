package app.unlimitedmessaging.android.sms

import app.unlimitedmessaging.android.api.ReportedStatus
import kotlinx.serialization.Serializable

/**
 * What the phone knows about one outgoing message, across its SMS parts.
 *
 * A text longer than one SMS (160 GSM-7 characters, 70 with any non-GSM
 * character such as most emoji) is split into parts, and Android reports
 * "sent" and "delivered" per part. The backend wants one status per message:
 * this record turns per-part events into message-level transitions.
 */
@Serializable
data class SendRecord(
    val messageId: String,
    val parts: Int,
    val sentParts: Set<Int> = emptySet(),
    val deliveredParts: Set<Int> = emptySet(),
    /** The last status handed to the backend, null before the first one. */
    val reported: String? = null,
    val errorCode: String? = null,
    val createdAt: Long,
) {
    private val isFinal: Boolean
        get() = reported == ReportedStatus.FAILED ||
            reported == ReportedStatus.UNDELIVERABLE ||
            reported == ReportedStatus.DELIVERED

    /**
     * The radio accepted [part]. Once every part is accepted, the message is
     * SENT.
     */
    fun onPartSent(part: Int): Transition {
        if (isFinal) return Transition(this)
        val next = copy(sentParts = sentParts + part)
        return if (next.reported == null && next.sentParts.size >= parts) {
            Transition(next.copy(reported = ReportedStatus.SENT), listOf(ReportedStatus.SENT))
        } else {
            Transition(next)
        }
    }

    /** The radio refused [part]: the message as a whole FAILED, and stays so. */
    fun onPartFailed(part: Int, code: String): Transition {
        if (isFinal || reported == ReportedStatus.SENT) return Transition(this)
        val next = copy(reported = ReportedStatus.FAILED, errorCode = code)
        return Transition(next, listOf(ReportedStatus.FAILED))
    }

    /**
     * The recipient's network confirmed [part]. A delivery report implies the
     * part was sent, even if its "sent" callback has not arrived yet, so the
     * message can go through SENT and DELIVERED in one step.
     */
    fun onPartDelivered(part: Int): Transition {
        if (isFinal) return Transition(this)
        var next = copy(sentParts = sentParts + part, deliveredParts = deliveredParts + part)
        val emitted = mutableListOf<String>()
        if (next.reported == null && next.sentParts.size >= parts) {
            next = next.copy(reported = ReportedStatus.SENT)
            emitted += ReportedStatus.SENT
        }
        if (next.deliveredParts.size >= parts) {
            next = next.copy(reported = ReportedStatus.DELIVERED)
            emitted += ReportedStatus.DELIVERED
        }
        return Transition(next, emitted)
    }

    /** The recipient's network gave up on [part]: the message is UNDELIVERABLE. */
    fun onPartUndeliverable(part: Int, code: String): Transition {
        if (isFinal) return Transition(this)
        val next = copy(reported = ReportedStatus.UNDELIVERABLE, errorCode = code)
        return Transition(next, listOf(ReportedStatus.UNDELIVERABLE))
    }

    /** The updated record, and the statuses (in order) to report to the backend. */
    data class Transition(val record: SendRecord, val emitted: List<String> = emptyList())
}
