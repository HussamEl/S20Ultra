package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.ui.Hint
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.theme.Amber
import se.eldebosh.nastastopp.ui.theme.Located
import se.eldebosh.nastastopp.ui.theme.NotLocated
import se.eldebosh.nastastopp.youdrive.YouDriveWatcher
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The slim bar of YouDrive's window (the page itself fills the rest of the screen): back, status,
 * watch bell, start page, reload and a menu (add all trips, check now, log out). Added /
 * cancelled trips appear under it with "add / remove" buttons.
 */
@Composable
fun YouDriveBar(
    state: YouDriveWatcher.State,
    watching: Boolean,
    onBack: () -> Unit,
    onWatch: (Boolean) -> Unit,
    onStartPage: () -> Unit,
    onReload: () -> Unit,
    onReadNow: () -> Unit,
    onImportAll: () -> Unit,
    onApply: (YouDriveWatcher.PendingChange) -> Unit,
    onDismiss: (YouDriveWatcher.PendingChange) -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 2.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.ref(140).size(52.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back), modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.youdrive_title), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    youDriveStatus(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.status == YouDriveWatcher.Status.LOGGED_OUT || state.problem != null) NotLocated else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.ref(143),
                )
            }
            IconButton(onClick = { onWatch(!watching) }, modifier = Modifier.ref(142).size(52.dp)) {
                Icon(
                    painterResource(if (watching) R.drawable.ic_bell else R.drawable.ic_bell_off),
                    contentDescription = stringResource(R.string.youdrive_watch),
                    tint = if (watching) Located else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp),
                )
            }
            IconButton(onClick = onStartPage, modifier = Modifier.ref(153).size(52.dp)) {
                Icon(painterResource(R.drawable.ic_home), contentDescription = stringResource(R.string.youdrive_start_page), modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onReload, modifier = Modifier.ref(141).size(52.dp)) {
                Icon(painterResource(R.drawable.ic_repeat), contentDescription = stringResource(R.string.youdrive_reload), modifier = Modifier.size(26.dp))
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.ref(155).size(52.dp)) {
                    Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.youdrive_more), modifier = Modifier.size(26.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.youdrive_import_all, state.trips.size)) },
                        onClick = { menu = false; onImportAll() },
                        enabled = state.trips.isNotEmpty(),
                        modifier = Modifier.ref(148).heightIn(min = TouchTarget),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.youdrive_read_now)) },
                        onClick = { menu = false; onReadNow() },
                        modifier = Modifier.ref(149).heightIn(min = TouchTarget),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.youdrive_logout), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; confirmLogout = true },
                        modifier = Modifier.ref(152).heightIn(min = TouchTarget),
                    )
                }
            }
        }
        state.changes.asReversed().forEach { ChangeRow(it, onApply, onDismiss) }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.youdrive_logout)) },
            text = { Hint(R.string.youdrive_logout_text, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Status line for the YouDrive page (also used on the Home card). */
@Composable
fun youDriveStatus(state: YouDriveWatcher.State): String {
    // A page problem is shown while no trips are read (the driver can report its text).
    if (state.problem != null && state.status != YouDriveWatcher.Status.WATCHING) {
        return stringResource(R.string.youdrive_problem, state.problem)
    }
    val checked = state.lastReadMs?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    } ?: "--:--"
    return when (state.status) {
        YouDriveWatcher.Status.OFF -> stringResource(R.string.youdrive_status_off)
        YouDriveWatcher.Status.LOADING -> stringResource(R.string.youdrive_status_loading)
        YouDriveWatcher.Status.WATCHING -> stringResource(R.string.youdrive_status_watching, state.trips.size, checked)
        YouDriveWatcher.Status.NO_TRIPS -> stringResource(R.string.youdrive_status_no_trips, checked)
        YouDriveWatcher.Status.LOGGED_OUT -> stringResource(R.string.youdrive_status_logged_out)
    }
}

/** A trip added or cancelled on the page, with "add to / remove from my list" and "dismiss". */
@Composable
private fun ChangeRow(
    change: YouDriveWatcher.PendingChange,
    onApply: (YouDriveWatcher.PendingChange) -> Unit,
    onDismiss: (YouDriveWatcher.PendingChange) -> Unit,
) {
    val added = change.change.added
    val trip = change.change.trip
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .ref(144)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (added) R.string.trip_added_title else R.string.trip_cancelled_title, trip.time ?: "--:--"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (added) Located else NotLocated,
            )
            Text(
                trip.address,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = { onApply(change) }, modifier = Modifier.ref(if (added) 145 else 147).heightIn(min = TouchTarget)) {
            Text(stringResource(if (added) R.string.youdrive_add else R.string.youdrive_remove), color = Amber)
        }
        TextButton(onClick = { onDismiss(change) }, modifier = Modifier.ref(146).heightIn(min = TouchTarget)) {
            Text(stringResource(R.string.youdrive_dismiss))
        }
    }
}

/** Home card: opens the YouDrive screen; shows whether it is watched and unhandled changes. */
@Composable
fun YouDriveCard(state: YouDriveWatcher.State, watching: Boolean, onOpen: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .ref(25)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen)
            .heightIn(min = TouchTarget)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_schedule),
            contentDescription = null,
            tint = if (watching) Located else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.youdrive_card_title), style = MaterialTheme.typography.titleMedium)
            Text(
                if (watching) youDriveStatus(state) else stringResource(R.string.youdrive_card_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.changes.isNotEmpty()) {
                Text(
                    stringResource(R.string.youdrive_card_changes, state.changes.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Amber,
                )
            }
        }
    }
}
