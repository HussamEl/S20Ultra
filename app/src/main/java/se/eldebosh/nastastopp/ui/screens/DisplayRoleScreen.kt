package se.eldebosh.nastastopp.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.link.DisplayLinkClient
import se.eldebosh.nastastopp.link.PairedDevice
import se.eldebosh.nastastopp.settings.AppSettings
import se.eldebosh.nastastopp.ui.BigButton
import se.eldebosh.nastastopp.ui.Paragraph
import se.eldebosh.nastastopp.ui.SectionTitle
import se.eldebosh.nastastopp.ui.TopBar
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.theme.Amber

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
    onRequestPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onChoose: (PairedDevice) -> Unit,
    onSpeak: () -> Unit,
    onToggleSpeaks: (Boolean) -> Unit,
    onSwitchToController: () -> Unit,
) {
    var showSetup by remember { mutableStateOf(false) }
    val blocked = link.status == DisplayLinkClient.Status.NO_PERMISSION ||
        link.status == DisplayLinkClient.Status.BLUETOOTH_OFF ||
        link.status == DisplayLinkClient.Status.NO_BLUETOOTH
    if (settings.displayControllerAddress != null && !showSetup && !blocked) {
        val name = link.deviceName ?: settings.displayControllerAddress
        PassengerDisplayScreen(
            snapshot = snapshot,
            status = if (link.status == DisplayLinkClient.Status.CONNECTED) {
                stringResource(R.string.display_connected, name)
            } else {
                stringResource(R.string.display_connecting, name)
            },
            connected = link.status == DisplayLinkClient.Status.CONNECTED,
            onSpeak = onSpeak,
            onExit = { showSetup = true },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            stringResource(R.string.role_display),
            onBack = if (settings.displayControllerAddress != null && !blocked) ({ showSetup = false }) else null,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Paragraph(stringResource(R.string.link_hint))
            when (link.status) {
                DisplayLinkClient.Status.NO_PERMISSION ->
                    BigButton(stringResource(R.string.allow_bluetooth), onRequestPermission, Modifier.fillMaxWidth(), icon = R.drawable.ic_bluetooth)
                DisplayLinkClient.Status.BLUETOOTH_OFF ->
                    BigButton(stringResource(R.string.turn_on_bluetooth), onEnableBluetooth, Modifier.fillMaxWidth(), icon = R.drawable.ic_bluetooth)
                DisplayLinkClient.Status.NO_BLUETOOTH ->
                    Text(stringResource(R.string.link_no_bt), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }

            SectionTitle(stringResource(R.string.display_choose_device))
            if (paired.isEmpty()) {
                Paragraph(stringResource(R.string.display_no_paired))
            }
            paired.forEach { device ->
                val selected = device.address == settings.displayControllerAddress
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable {
                            showSetup = false
                            onChoose(device)
                        }
                        .padding(horizontal = 16.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_bluetooth), contentDescription = null, tint = if (selected) Amber else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(12.dp))
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                }
            }
            BigButton(stringResource(R.string.open_bt_settings), onOpenBluetoothSettings, Modifier.fillMaxWidth(), icon = R.drawable.ic_settings, primary = false)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget).clickable { onToggleSpeaks(!settings.displaySpeaks) },
            ) {
                Text(stringResource(R.string.display_speaks), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = settings.displaySpeaks, onCheckedChange = onToggleSpeaks)
            }
            Spacer(Modifier.size(8.dp))
            BigButton(stringResource(R.string.switch_to_controller), onSwitchToController, Modifier.fillMaxWidth(), icon = R.drawable.ic_navigation, primary = false)
            Spacer(Modifier.size(24.dp))
        }
    }
}
