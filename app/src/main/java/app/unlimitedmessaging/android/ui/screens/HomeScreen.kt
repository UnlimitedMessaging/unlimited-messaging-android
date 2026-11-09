package app.unlimitedmessaging.android.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.unlimitedmessaging.android.R
import app.unlimitedmessaging.android.data.ActivityEntry
import app.unlimitedmessaging.android.data.Direction
import app.unlimitedmessaging.android.data.EntryStatus
import app.unlimitedmessaging.android.service.ConnectionState
import app.unlimitedmessaging.android.sim.SimCard
import app.unlimitedmessaging.android.ui.UiState
import app.unlimitedmessaging.android.ui.components.StatusDot
import app.unlimitedmessaging.android.ui.components.UmCard
import app.unlimitedmessaging.android.ui.components.UmLinkButton
import app.unlimitedmessaging.android.ui.components.UmSecondaryButton
import app.unlimitedmessaging.android.ui.components.UmTonalButton
import app.unlimitedmessaging.android.ui.components.UmWordmark
import app.unlimitedmessaging.android.ui.theme.LocalUmColors
import app.unlimitedmessaging.android.ui.theme.UmColors

/** Everything the home screen can ask for, implemented by the activity. */
data class HomeActions(
    val setGatewayEnabled: (Boolean) -> Unit,
    val setTelemetryEnabled: (Boolean) -> Unit,
    val grantSms: () -> Unit,
    val grantPhoneNumbers: () -> Unit,
    val grantNotifications: () -> Unit,
    val requestBatteryExemption: () -> Unit,
    val openManufacturerSettings: () -> Unit,
    val openManufacturerGuide: () -> Unit,
    val openAppSettings: () -> Unit,
    val signOut: () -> Unit,
)

@Composable
fun HomeScreen(state: UiState, deviceName: String, versionName: String, actions: HomeActions) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                UmWordmark(size = 22f, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp))
            }
            item { GatewayCard(state, deviceName, actions.setGatewayEnabled) }
            if (!state.setup.allDone) item { SetupCard(state, actions) }
            item { SimsCard(state) }
            item { ActivityCard(state) }
            if (state.setup.allDone) item { SetupCard(state, actions) }
            state.telemetryEnabled?.let { enabled -> item { TelemetryCard(enabled, actions.setTelemetryEnabled) } }
            item {
                Spacer(Modifier.height(4.dp))
                UmSecondaryButton(onClick = actions.signOut, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sign_out))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.version, versionName),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalUmColors.current.muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = modifier)
}

@Composable
private fun GatewayCard(state: UiState, deviceName: String, setEnabled: (Boolean) -> Unit) {
    val (status, dot) = when {
        !state.gatewayEnabled -> R.string.gateway_paused to LocalUmColors.current.muted
        !state.setup.requiredDone -> R.string.gateway_needs_setup to MaterialTheme.colorScheme.error
        else -> when (state.gateway.connection) {
            ConnectionState.ONLINE -> R.string.gateway_online to UmColors.Bright
            ConnectionState.OFFLINE -> R.string.gateway_offline to Color(0xFFE2A13A)
            ConnectionState.CONNECTING -> R.string.gateway_connecting to Color(0xFFE2A13A)
            ConnectionState.STOPPED -> R.string.gateway_stopped to LocalUmColors.current.muted
        }
    }
    UmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle(stringResource(R.string.gateway_title))
                Spacer(Modifier.height(2.dp))
                Text(deviceName, style = MaterialTheme.typography.bodySmall, color = LocalUmColors.current.muted)
            }
            UmSwitch(checked = state.gatewayEnabled, onCheckedChange = setEnabled)
        }
        Spacer(Modifier.height(14.dp))
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(dot)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(status), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (state.gatewayEnabled && state.gateway.connection == ConnectionState.OFFLINE) {
            state.gateway.lastError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun UmSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

/** "Share usage statistics". Hidden on builds made without telemetry. */
@Composable
private fun TelemetryCard(enabled: Boolean, setEnabled: (Boolean) -> Unit) {
    UmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.telemetry_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.telemetry_detail), style = MaterialTheme.typography.bodySmall, color = LocalUmColors.current.muted)
            }
            Spacer(Modifier.width(12.dp))
            UmSwitch(checked = enabled, onCheckedChange = setEnabled)
        }
    }
}

@Composable
private fun SetupCard(state: UiState, actions: HomeActions) {
    val setup = state.setup
    UmCard {
        SectionTitle(stringResource(R.string.setup_title))
        SetupRow(R.string.setup_sms, R.string.setup_sms_detail, setup.smsGranted, actions.grantSms)
        SetupRow(R.string.setup_battery, R.string.setup_battery_detail, setup.batteryExempt, actions.requestBatteryExemption)
        SetupRow(R.string.setup_notifications, R.string.setup_notifications_detail, setup.notificationsGranted, actions.grantNotifications)
        SetupRow(R.string.setup_phone_numbers, R.string.setup_phone_numbers_detail, setup.phoneNumbersGranted, actions.grantPhoneNumbers)
        if (setup.aggressiveManufacturer) {
            HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.outline)
            Text(stringResource(R.string.setup_manufacturer), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.setup_manufacturer_detail), style = MaterialTheme.typography.bodySmall, color = LocalUmColors.current.muted)
            Row {
                UmLinkButton(stringResource(R.string.action_open_settings), actions.openManufacturerSettings)
                UmLinkButton(stringResource(R.string.action_open_guide), actions.openManufacturerGuide)
            }
        }
        HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.outline)
        UmLinkButton(stringResource(R.string.action_app_settings), actions.openAppSettings)
    }
}

@Composable
private fun SetupRow(title: Int, detail: Int, done: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(detail), style = MaterialTheme.typography.bodySmall, color = LocalUmColors.current.muted)
        }
        Spacer(Modifier.width(12.dp))
        if (done) {
            Text(
                "✓ " + stringResource(R.string.setup_done),
                style = MaterialTheme.typography.labelLarge,
                color = LocalUmColors.current.success,
            )
        } else {
            UmTonalButton(onClick = onFix) { Text(stringResource(R.string.action_allow), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun SimsCard(state: UiState) {
    UmCard {
        SectionTitle(stringResource(R.string.sims_title))
        if (state.sims.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.sims_empty), style = MaterialTheme.typography.bodyMedium, color = LocalUmColors.current.muted)
        }
        state.sims.forEachIndexed { index, sim ->
            if (index > 0) HorizontalDivider(Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val account = state.gateway.accounts.firstOrNull { it.subscriptionId == sim.subscriptionId }
            val active = account?.status == "ACTIVE"
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(simLabel(sim), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        sim.phoneNumber ?: stringResource(R.string.sims_number_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalUmColors.current.muted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Pill(
                    text = when {
                        account == null -> stringResource(R.string.sims_not_registered)
                        active -> stringResource(R.string.sims_active)
                        else -> stringResource(R.string.sims_inactive)
                    },
                    color = if (active) LocalUmColors.current.success else LocalUmColors.current.muted,
                )
            }
        }
    }
}

/** A small status label. Square-ish (6dp), never a full pill. */
@Composable
private fun Pill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.10f), shape = MaterialTheme.shapes.extraSmall) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun simLabel(sim: SimCard): String {
    val slot = if (sim.slotIndex >= 0) {
        stringResource(R.string.sims_slot, sim.slotIndex + 1)
    } else {
        stringResource(R.string.sims_esim)
    }
    return listOfNotNull(slot, sim.carrierName ?: sim.displayName).joinToString(" · ")
}

@Composable
private fun ActivityCard(state: UiState) {
    UmCard {
        SectionTitle(stringResource(R.string.activity_title))
        if (state.activity.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.activity_empty), style = MaterialTheme.typography.bodyMedium, color = LocalUmColors.current.muted)
        }
        state.activity.take(MAX_ACTIVITY_ROWS).forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ActivityRow(entry, state.sims)
        }
    }
}

private const val MAX_ACTIVITY_ROWS = 50

@Composable
private fun ActivityRow(entry: ActivityEntry, sims: List<SimCard>) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val arrow = if (entry.direction == Direction.OUTGOING) "↑" else "↓"
            Text(
                "$arrow ${entry.address}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Pill(stringResource(entry.status.labelRes()), entry.status.color())
        }
        Spacer(Modifier.height(4.dp))
        Text(
            entry.preview,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalUmColors.current.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val sim = sims.firstOrNull { it.subscriptionId == entry.subscriptionId }
        val details = listOfNotNull(
            DateUtils.getRelativeTimeSpanString(entry.updatedAt).toString(),
            sim?.let { simLabel(it) },
            entry.errorCode,
        ).joinToString(" · ")
        Spacer(Modifier.height(4.dp))
        Text(details, style = MaterialTheme.typography.labelSmall, color = LocalUmColors.current.muted)
    }
}

private fun EntryStatus.labelRes(): Int = when (this) {
    EntryStatus.SENDING -> R.string.status_sending
    EntryStatus.SENT -> R.string.status_sent
    EntryStatus.DELIVERED -> R.string.status_delivered
    EntryStatus.UNDELIVERABLE -> R.string.status_undeliverable
    EntryStatus.FAILED -> R.string.status_failed
    EntryStatus.RECEIVED -> R.string.status_received
    EntryStatus.FORWARDED -> R.string.status_forwarded
}

@Composable
private fun EntryStatus.color(): Color = when (this) {
    EntryStatus.FAILED, EntryStatus.UNDELIVERABLE -> MaterialTheme.colorScheme.error
    EntryStatus.DELIVERED, EntryStatus.FORWARDED, EntryStatus.SENT -> LocalUmColors.current.success
    else -> LocalUmColors.current.muted
}
