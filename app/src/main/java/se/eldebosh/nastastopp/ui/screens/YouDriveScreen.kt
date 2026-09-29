package se.eldebosh.nastastopp.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.ui.AppCard
import se.eldebosh.nastastopp.ui.Chevron
import se.eldebosh.nastastopp.ui.Hint
import se.eldebosh.nastastopp.ui.KindLabel
import se.eldebosh.nastastopp.ui.ListRow
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.Brand
import se.eldebosh.nastastopp.ui.theme.Hairline
import se.eldebosh.nastastopp.ui.theme.Located
import se.eldebosh.nastastopp.ui.theme.NotLocated
import se.eldebosh.nastastopp.ui.theme.TimeColor
import se.eldebosh.nastastopp.youdrive.YouDriveWatcher
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The slim bar of YouDrive's window (the page itself fills the rest of the screen): back, title
 * with the status line, the watch bell and a menu (start page, reload, add all trips, check now,
 * log out). Added / cancelled trips appear under it with "add / remove" buttons.
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
    val problem = state.status == YouDriveWatcher.Status.LOGGED_OUT || (state.problem != null && state.status != YouDriveWatcher.Status.WATCHING)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.refCorner(140).size(TouchTarget)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back), modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(stringResource(R.string.youdrive_title), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.ref(143)) {
                    Box(Modifier.size(7.dp).clip(MaterialTheme.shapes.extraSmall).background(if (problem) NotLocated else if (watching) Located else MaterialTheme.colorScheme.outline))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        youDriveStatus(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (problem) NotLocated else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { onWatch(!watching) }, modifier = Modifier.refCorner(142).size(TouchTarget)) {
                Icon(
                    painterResource(if (watching) R.drawable.ic_bell else R.drawable.ic_bell_off),
                    contentDescription = stringResource(R.string.youdrive_watch),
                    tint = if (watching) Located else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.refCorner(155).size(TouchTarget)) {
                    Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.youdrive_more), modifier = Modifier.size(22.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(stringResource(R.string.youdrive_start_page), R.drawable.ic_home, 153) { menu = false; onStartPage() }
                    MenuItem(stringResource(R.string.youdrive_reload), R.drawable.ic_repeat, 141) { menu = false; onReload() }
                    MenuItem(
                        stringResource(R.string.youdrive_import_all, state.trips.size), R.drawable.ic_add, 148,
                        enabled = state.trips.isNotEmpty(),
                    ) { menu = false; onImportAll() }
                    MenuItem(stringResource(R.string.youdrive_read_now), R.drawable.ic_schedule, 149) { menu = false; onReadNow() }
                    HorizontalDivider(color = Hairline)
                    MenuItem(stringResource(R.string.youdrive_logout), R.drawable.ic_delete, 152, color = MaterialTheme.colorScheme.error) {
                        menu = false
                        confirmLogout = true
                    }
                }
            }
        }
        state.changes.asReversed().forEach { ChangeRow(it, onApply, onDismiss) }
        HorizontalDivider(color = Hairline)
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

@Composable
private fun MenuItem(
    text: String,
    @DrawableRes icon: Int,
    ref: Int,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = if (enabled) color else MaterialTheme.colorScheme.outline) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, tint = if (enabled) color else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.ref(ref).heightIn(min = TouchTarget),
    )
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
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .ref(144)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (added) R.string.trip_added_title else R.string.trip_cancelled_title, trip.time ?: "--:--"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (added) Located else NotLocated,
                )
                trip.stop?.kind?.let {
                    Spacer(Modifier.width(8.dp))
                    KindLabel(it)
                }
            }
            Text(
                trip.address,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = { onApply(change) }, modifier = Modifier.refCorner(if (added) 145 else 147).heightIn(min = TouchTarget)) {
            Text(stringResource(if (added) R.string.youdrive_add else R.string.youdrive_remove), color = Brand, style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = { onDismiss(change) }, modifier = Modifier.refCorner(146).heightIn(min = TouchTarget)) {
            Text(stringResource(R.string.youdrive_dismiss), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Home card: opens YouDrive's window; shows whether it is watched and unhandled changes. */
@Composable
fun YouDriveCard(state: YouDriveWatcher.State, watching: Boolean, onOpen: () -> Unit) {
    AppCard(Modifier.ref(25), onClick = onOpen) {
        ListRow(
            title = stringResource(R.string.youdrive_card_title),
            subtitle = if (watching) youDriveStatus(state) else stringResource(R.string.youdrive_card_off),
            icon = R.drawable.ic_schedule,
            iconTint = if (watching) Located else Brand,
            trailing = { Chevron() },
        )
        if (state.changes.isNotEmpty()) {
            Text(
                stringResource(R.string.youdrive_card_changes, state.changes.size),
                style = MaterialTheme.typography.labelLarge,
                color = TimeColor,
                modifier = Modifier.padding(start = 64.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}
