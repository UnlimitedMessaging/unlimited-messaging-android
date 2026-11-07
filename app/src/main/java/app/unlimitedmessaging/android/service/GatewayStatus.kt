package app.unlimitedmessaging.android.service

import app.unlimitedmessaging.android.api.DeviceAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ConnectionState {
    /** The service is not running. */
    STOPPED,

    /** Registering, or opening the first outbox request. */
    CONNECTING,

    /** Waiting on the outbox: messages are picked up as soon as they are queued. */
    ONLINE,

    /** The last request failed; retrying after a backoff or when the network returns. */
    OFFLINE,
}

data class GatewaySnapshot(
    val connection: ConnectionState = ConnectionState.STOPPED,
    /** The accounts the backend holds for this phone's SIMs, from the last registration. */
    val accounts: List<DeviceAccount> = emptyList(),
    /** Last time the server answered, epoch millis. */
    val lastContactAt: Long? = null,
    /** Short technical reason for OFFLINE, for the home screen. */
    val lastError: String? = null,
)

/** What the gateway service is doing, observed by the home screen and the notification. */
class GatewayStatus {
    private val _snapshot = MutableStateFlow(GatewaySnapshot())
    val snapshot: StateFlow<GatewaySnapshot> = _snapshot.asStateFlow()

    fun connecting() = _snapshot.update { it.copy(connection = ConnectionState.CONNECTING) }

    fun registered(accounts: List<DeviceAccount>) =
        _snapshot.update { it.copy(accounts = accounts, lastContactAt = System.currentTimeMillis()) }

    fun online() = _snapshot.update {
        it.copy(connection = ConnectionState.ONLINE, lastContactAt = System.currentTimeMillis(), lastError = null)
    }

    fun offline(reason: String) = _snapshot.update { it.copy(connection = ConnectionState.OFFLINE, lastError = reason) }

    fun stopped() = _snapshot.update { it.copy(connection = ConnectionState.STOPPED) }

    /** After sign-out: nothing of the previous session should remain on screen. */
    fun reset() {
        _snapshot.value = GatewaySnapshot()
    }
}
