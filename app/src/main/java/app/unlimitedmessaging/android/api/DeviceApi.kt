package app.unlimitedmessaging.android.api

import android.net.Uri

/**
 * The device endpoints of the API. See docs/API_CONTRACT.md for what each
 * one does on the server side.
 */
class DeviceApi(private val client: ApiClient) {

    /** Creates or updates this device and one account per SIM. Idempotent. */
    suspend fun register(request: RegisterDeviceRequest): RegisterDeviceResponse =
        client.post(
            "/device/register",
            request,
            RegisterDeviceRequest.serializer(),
            RegisterDeviceResponse.serializer(),
        ) ?: throw ApiException(204, "Empty response to /device/register")

    /**
     * Claims the messages waiting for this device. The server holds the
     * request open for up to [waitSeconds] when there is nothing to send, and
     * answers as soon as something arrives.
     */
    suspend fun pollOutbox(waitSeconds: Int): List<OutboxMessage> =
        client.get("/device/outbox?wait=$waitSeconds", OutboxResponse.serializer(), longPoll = true)
            ?.messages
            .orEmpty()

    suspend fun reportStatus(messageId: String, report: StatusReport) {
        client.post<StatusReport, Unit>(
            "/device/messages/${Uri.encode(messageId)}/status",
            report,
            StatusReport.serializer(),
            response = null,
        )
    }

    suspend fun reportInbound(sms: InboundSms) {
        client.post<InboundSms, Unit>("/device/inbound", sms, InboundSms.serializer(), response = null)
    }

    /** Removes this device and deactivates its accounts (sign-out). */
    suspend fun unregister() {
        client.delete("/device")
    }
}
