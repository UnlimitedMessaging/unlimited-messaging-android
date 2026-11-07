package app.unlimitedmessaging.android.api

import kotlinx.serialization.Serializable

/*
 * Request and response bodies of the device endpoints. The authoritative
 * description of each field, and of what the server does with it, is
 * docs/API_CONTRACT.md: keep both in step.
 */

@Serializable
data class RegisterDeviceRequest(
    val installationId: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val androidSdk: Int,
    val appVersion: String,
    val sims: List<SimPayload>,
)

@Serializable
data class SimPayload(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String?,
    val displayName: String?,
    /** E.164 when the carrier exposes it to the phone, null otherwise (common). */
    val phoneNumber: String?,
    val countryIso: String?,
)

@Serializable
data class RegisterDeviceResponse(
    val deviceId: String,
    val accounts: List<DeviceAccount>,
)

@Serializable
data class DeviceAccount(
    val subscriptionId: Int,
    val accountId: String,
    val status: String,
)

@Serializable
data class OutboxResponse(
    val messages: List<OutboxMessage>,
)

@Serializable
data class OutboxMessage(
    val id: String,
    val accountId: String,
    val subscriptionId: Int,
    val to: String,
    /** Final text, sent verbatim: anything the server adds (e.g. a plan's footer) is already in it. */
    val text: String,
    val expiresAt: String? = null,
)

/** Values of [StatusReport.status]. */
object ReportedStatus {
    const val SENT = "SENT"
    const val DELIVERED = "DELIVERED"
    const val UNDELIVERABLE = "UNDELIVERABLE"
    const val FAILED = "FAILED"
}

@Serializable
data class StatusReport(
    val status: String,
    /** ISO-8601 instant at which the phone observed this status. */
    val occurredAt: String,
    val errorCode: String? = null,
    /** How many SMS parts the text was split into. */
    val parts: Int? = null,
)

@Serializable
data class InboundSms(
    val subscriptionId: Int,
    val from: String,
    val text: String,
    /** When the sender's network says the message was sent (SMSC timestamp). */
    val sentAt: String,
    /** When this phone received it. */
    val receivedAt: String,
    /** Stable across retries of the same SMS: the server ignores a second copy. */
    val dedupKey: String,
)
