package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.link.DisplayLinkClient
import se.eldebosh.nastastopp.link.PairedDevice
import se.eldebosh.nastastopp.settings.AppSettings
import se.eldebosh.nastastopp.ui.AppButton
import se.eldebosh.nastastopp.ui.AppCard
import se.eldebosh.nastastopp.ui.HelpDot
import se.eldebosh.nastastopp.ui.IconBadge
import se.eldebosh.nastastopp.ui.ListRow
import se.eldebosh.nastastopp.ui.SectionTitle
import se.eldebosh.nastastopp.ui.TopBar
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.AppTheme

/**
 * This device is a passenger display: first choose the driver's (paired) device, then show the
 * passenger display it sends over Bluetooth.
 */
@Composable
fun DisplayRoleScreen(
    settings: AppSettings,
    link: DisplayLinkClient.State,
    snapshot: DisplaySnapshot?,
    paired: List<PairedDevice>,
    /** Checked synchronously by the caller, so the right screen shows before the link loop runs. */
    bluetoothReady: Boolean,
    onRequestPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    /** A paired device's address, or null for automatic search. */
    onChoose: (String?) -> Unit,
    onSpeak: () -> Unit,
    onToggleSpeaks: (Boolean) -> Unit,
    onSwitchToController: () -> Unit,
    availabilityStatus: DisplayLinkClient.Status = DisplayLinkClient.Status.IDLE,
    /** Goes up by one with each announcement from the driver's phone. */
    spoken: Int = 0,
) {
    var showSetup by remember { mutableStateOf(false) }
    val blocked = !bluetoothReady || paired.isEmpty() ||
        link.status == DisplayLinkClient.Status.NO_PERMISSION ||
        link.status == DisplayLinkClient.Status.BLUETOOTH_OFF ||
        link.status == DisplayLinkClient.Status.NO_BLUETOOTH ||
        link.status == DisplayLinkClient.Status.NO_DEVICES
    // The display searches for the driver's device by itself: no device has to be chosen first.
    if (!showSetup && !blocked) {
        val connected = link.status == DisplayLinkClient.Status.CONNECTED
        PassengerDisplayScreen(
            snapshot = snapshot,
            status = when {
                connected -> stringResource(R.string.display_connected, link.deviceName.orEmpty())
                link.deviceName != null -> stringResource(R.string.display_connecting, link.deviceName)
                else -> stringResource(R.string.display_searching)
            },
            connected = connected,
            onSpeak = onSpeak,
            onExit = { showSetup = true },
            spoken = spoken,
            detail = if (!connected) link.lastError?.let { stringResource(R.string.display_last_error, it) } else null,
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            stringResource(R.string.role_display),
            onBack = if (!blocked) ({ showSetup = false }) else null,
            backRef = 191,
        ) {
            HelpDot(R.string.link_hint, Modifier.refCorner(192), title = stringResource(R.string.role_display))
            Spacer(Modifier.size(8.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (if (bluetoothReady) link.status else availabilityStatus) {
                DisplayLinkClient.Status.NO_PERMISSION ->
                    AppButton(stringResource(R.string.allow_bluetooth), onRequestPermission, Modifier.ref(193).fillMaxWidth(), icon = R.drawable.ic_bluetooth)
                DisplayLinkClient.Status.BLUETOOTH_OFF ->
                    AppButton(stringResource(R.string.turn_on_bluetooth), onEnableBluetooth, Modifier.ref(193).fillMaxWidth(), icon = R.drawable.ic_bluetooth)
                DisplayLinkClient.Status.NO_BLUETOOTH ->
                    Text(stringResource(R.string.link_no_bt), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }

            link.lastError?.let {
                Text(stringResource(R.string.display_last_error, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.ref(194))
            }

            SectionTitle(
                stringResource(R.string.display_choose_device),
                help = if (paired.isEmpty()) R.string.display_no_paired else null,
            )
            DeviceRow(stringResource(R.string.display_auto), settings.displayControllerAddress == null, 195) {
                showSetup = false
                onChoose(null)
            }
            paired.forEach { device ->
                DeviceRow(device.name, device.address == settings.displayControllerAddress, 196) {
                    showSetup = false
                    onChoose(device.address)
                }
            }
            AppButton(stringResource(R.string.open_bt_settings), onOpenBluetoothSettings, Modifier.ref(197).fillMaxWidth(), icon = R.drawable.ic_settings, primary = false)

            AppCard {
                ListRow(
                    title = stringResource(R.string.display_speaks),
                    onClick = { onToggleSpeaks(!settings.displaySpeaks) },
                    trailing = { Switch(checked = settings.displaySpeaks, onCheckedChange = onToggleSpeaks, modifier = Modifier.refCorner(198)) },
                )
            }
            Spacer(Modifier.size(8.dp))
            AppButton(stringResource(R.string.switch_to_controller), onSwitchToController, Modifier.ref(199).fillMaxWidth(), icon = R.drawable.ic_navigation, primary = false)
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun DeviceRow(name: String, selected: Boolean, ref: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .ref(ref)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (selected) AppTheme.colors.info else AppTheme.colors.cardBorder, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        IconBadge(R.drawable.ic_bluetooth, if (selected) AppTheme.colors.info else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}
