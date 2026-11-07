package app.unlimitedmessaging.android.sim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import app.unlimitedmessaging.android.api.SimPayload
import java.util.concurrent.Executor

/** One active SIM (physical or eSIM) as the gateway sees it. */
data class SimCard(
    /**
     * Android's id for this SIM on this phone. It stays the same when the same
     * SIM is taken out and put back, and changes for a new SIM: that is why
     * the backend keys an account on (device, subscriptionId).
     */
    val subscriptionId: Int,
    /** 0-based physical slot, or -1 for an eSIM not tied to one. */
    val slotIndex: Int,
    val carrierName: String?,
    val displayName: String?,
    val phoneNumber: String?,
    val countryIso: String?,
) {
    fun toPayload() = SimPayload(subscriptionId, slotIndex, carrierName, displayName, phoneNumber, countryIso)
}

/**
 * Lists the phone's active SIM cards. Every SIM becomes its own messaging
 * account on the backend, so a dual-SIM phone shows up as two accounts.
 */
class SimInventory(private val context: Context) {
    private val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)

    fun canRead(): Boolean = granted(Manifest.permission.READ_PHONE_STATE)

    /** Active SIMs, ordered by slot. Empty without READ_PHONE_STATE. */
    @SuppressLint("MissingPermission") // Checked by canRead().
    fun activeSims(): List<SimCard> {
        if (!canRead()) return emptyList()
        val infos: List<SubscriptionInfo> = subscriptionManager.activeSubscriptionInfoList.orEmpty()
        return infos
            .map { info ->
                SimCard(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    carrierName = info.carrierName?.toString()?.takeIf { it.isNotBlank() },
                    displayName = info.displayName?.toString()?.takeIf { it.isNotBlank() },
                    phoneNumber = phoneNumber(info),
                    countryIso = info.countryIso?.takeIf { it.isNotBlank() }?.uppercase(),
                )
            }
            .sortedBy { it.slotIndex }
    }

    fun find(subscriptionId: Int): SimCard? = activeSims().firstOrNull { it.subscriptionId == subscriptionId }

    /**
     * The SIM's own number, when the carrier wrote it on the SIM or provides
     * it over the network. Many do not: null is a normal answer here, and the
     * backend asks the user for the number instead. (Detecting it by sending
     * a verification SMS to a known number is planned, server side.)
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun phoneNumber(info: SubscriptionInfo): String? {
        val number = try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    granted(Manifest.permission.READ_PHONE_NUMBERS) ->
                    subscriptionManager.getPhoneNumber(info.subscriptionId)
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                    @Suppress("DEPRECATION") info.number
                else -> null
            }
        } catch (_: SecurityException) {
            null
        }
        return number?.takeIf { it.isNotBlank() }
    }

    /**
     * Calls [onChange] whenever SIMs are added, removed or swapped, and once
     * right away. Returns a function that stops listening.
     */
    @SuppressLint("MissingPermission")
    fun observe(executor: Executor, onChange: () -> Unit): () -> Unit {
        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() = onChange()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            subscriptionManager.addOnSubscriptionsChangedListener(executor, listener)
        } else {
            @Suppress("DEPRECATION")
            subscriptionManager.addOnSubscriptionsChangedListener(listener)
        }
        return { subscriptionManager.removeOnSubscriptionsChangedListener(listener) }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
