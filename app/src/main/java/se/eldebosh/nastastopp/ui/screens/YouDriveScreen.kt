package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.viewinterop.AndroidView
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.ui.BigButton
import se.eldebosh.nastastopp.ui.ButtonRow
import se.eldebosh.nastastopp.ui.Hint
import se.eldebosh.nastastopp.ui.TopBar
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.theme.Amber
import se.eldebosh.nastastopp.ui.theme.Located
import se.eldebosh.nastastopp.ui.theme.NotLocated
import se.eldebosh.nastastopp.youdrive.YouDriveWatcher
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.webkit.WebView

/**
 * The driver's YouDrive page inside the app: log in once, switch on "Watch for changes", and the
 * app alerts when a trip is added or cancelled. Changes can be applied to the route list here.
 */
@Composable
fun YouDriveScreen(
    state: YouDriveWatcher.State,
    watching: Boolean,
    webView: (android.content.Context) -> WebView,
    onReleaseWebView: () -> Unit,
    canGoBack: Boolean,
    onPageBack: () -> Unit,
    onStartPage: () -> Unit,
    onBack: () -> Unit,
    onWatch: (Boolean) -> Unit,
    onReload: () -> Unit,
    onReadNow: () -> Unit,
    onImportAll: () -> Unit,
    onApply: (YouDriveWatcher.PendingChange) -> Unit,
    onDismiss: (YouDriveWatcher.PendingChange) -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by remember { mutableStateOf(false) }
    // Full page while logging in / finding the trips page (the page cannot scroll, so it needs the
    // room); the controls come back once trips are found. The driver can switch either way (154).
    var fullPageChoice by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val fullPage = fullPageChoice ?: (state.status != YouDriveWatcher.Status.WATCHING && state.changes.isEmpty())
    // The phone's Back key goes back inside the page first (e.g. from its Settings to the login).
    BackHandler(enabled = canGoBack, onBack = onPageBack)
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.youdrive_title), onBack = onBack, backRef = 140) {
            IconButton(onClick = onStartPage, modifier = Modifier.ref(153).size(TouchTarget)) {
                Icon(painterResource(R.drawable.ic_home), contentDescription = stringResource(R.string.youdrive_start_page), modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = onReload, modifier = Modifier.ref(141).size(TouchTarget)) {
                Icon(painterResource(R.drawable.ic_repeat), contentDescription = stringResource(R.string.youdrive_reload), modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { fullPageChoice = !fullPage }, modifier = Modifier.ref(154).size(TouchTarget)) {
                Icon(
                    painterResource(if (fullPage) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen),
                    contentDescription = stringResource(if (fullPage) R.string.youdrive_show_controls else R.string.youdrive_full_page),
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = { confirmLogout = true }, modifier = Modifier.ref(152).size(TouchTarget)) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.youdrive_logout), modifier = Modifier.size(28.dp))
            }
        }
        if (fullPage) {
            // One line of status; tap it for the controls.
            Text(
                youDriveStatus(state),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.status == YouDriveWatcher.Status.LOGGED_OUT || state.problem != null) NotLocated else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.ref(143).fillMaxWidth().clickable { fullPageChoice = false }.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        } else {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WatchRow(state, watching, onWatch)
                state.changes.asReversed().forEach { ChangeRow(it, onApply, onDismiss) }
                ButtonRow {
                    BigButton(
                        stringResource(R.string.youdrive_import_all, state.trips.size), onImportAll, Modifier.ref(148).weight(1f),
                        icon = R.drawable.ic_add, primary = false, enabled = state.trips.isNotEmpty(), minHeight = 48.dp,
                    )
                    BigButton(stringResource(R.string.youdrive_read_now), onReadNow, Modifier.ref(149).weight(1f), icon = R.drawable.ic_schedule, primary = false, minHeight = 48.dp)
                }
                if (!watching) Hint(R.string.youdrive_hint, Modifier.ref(150).fillMaxWidth())
            }
        }
        // The YouDrive page itself (the same WebView keeps running while watching).
        AndroidView(
            factory = { context -> webView(context) },
            onRelease = { onReleaseWebView() },
            modifier = Modifier.ref(151).weight(1f).fillMaxWidth().padding(top = 6.dp),
        )
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

/** "Watch for changes" switch with the page status (trips read, last check). */
@Composable
private fun WatchRow(state: YouDriveWatcher.State, watching: Boolean, onWatch: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onWatch(!watching) }
            .heightIn(min = TouchTarget)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.youdrive_watch), style = MaterialTheme.typography.titleMedium)
            Text(
                youDriveStatus(state),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.status == YouDriveWatcher.Status.LOGGED_OUT) NotLocated else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.ref(143),
            )
        }
        Switch(checked = watching, onCheckedChange = onWatch, modifier = Modifier.ref(142))
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
