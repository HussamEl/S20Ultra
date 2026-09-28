package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.link.DisplayLinkServer
import se.eldebosh.nastastopp.route.HistoryEntry
import se.eldebosh.nastastopp.route.model.RouteData
import se.eldebosh.nastastopp.tts.TtsStatus
import se.eldebosh.nastastopp.ui.AppButton
import se.eldebosh.nastastopp.ui.AppCard
import se.eldebosh.nastastopp.ui.CardDivider
import se.eldebosh.nastastopp.ui.Chevron
import se.eldebosh.nastastopp.ui.HelpDot
import se.eldebosh.nastastopp.ui.ListRow
import se.eldebosh.nastastopp.ui.SectionTitle
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.explain
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.Brand
import se.eldebosh.nastastopp.ui.theme.Located
import se.eldebosh.nastastopp.ui.theme.NotLocated
import se.eldebosh.nastastopp.ui.theme.TimeColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    route: RouteData?,
    ttsStatus: TtsStatus,
    importing: Boolean,
    onImport: () -> Unit,
    onResume: () -> Unit,
    onReview: () -> Unit,
    onClear: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onTtsMissing: () -> Unit,
    link: DisplayLinkServer.State,
    onToggleLink: (Boolean) -> Unit,
    onFixLink: () -> Unit,
    onUseAsDisplay: () -> Unit,
    overlayPermission: Boolean,
    overlayHidden: Boolean,
    onOverlayVisible: (Boolean) -> Unit,
    onOverlayPermission: () -> Unit,
    onAddTile: (() -> Unit)?,
    history: List<HistoryEntry>,
    historyRetentionHours: Int,
    onClearHistory: () -> Unit,
    /** The YouDrive card (trip-change alerts), number 25. */
    youDrive: @Composable () -> Unit = {},
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header: name + "?" (what the app does), Settings and Help as small icons.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Brand)
            HelpDot(R.string.home_subtitle, Modifier.refCorner(20), title = stringResource(R.string.app_name))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings, modifier = Modifier.refCorner(31).size(TouchTarget)) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.home_settings), modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onHelp, modifier = Modifier.refCorner(32).size(TouchTarget)) {
                Icon(painterResource(R.drawable.ic_help), contentDescription = stringResource(R.string.home_help), modifier = Modifier.size(22.dp))
            }
        }

        if (ttsStatus == TtsStatus.MISSING_DATA || ttsStatus == TtsStatus.NOT_SUPPORTED || ttsStatus == TtsStatus.ERROR) {
            AppCard(Modifier.ref(21), onClick = onTtsMissing) {
                ListRow(
                    title = stringResource(R.string.tts_missing_banner),
                    icon = R.drawable.ic_speaker,
                    iconTint = NotLocated,
                    trailing = { Chevron() },
                )
            }
        }

        if (route?.active == true) {
            AppButton(stringResource(R.string.home_resume), onResume, Modifier.ref(22).fillMaxWidth(), icon = R.drawable.ic_navigation, minHeight = 52.dp)
        } else if (route != null && route.stops.isNotEmpty()) {
            AppButton(stringResource(R.string.home_resume_draft), onReview, Modifier.ref(22).fillMaxWidth(), icon = R.drawable.ic_edit, minHeight = 52.dp)
        }

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            AppButton(
                text = stringResource(R.string.home_import),
                onClick = onImport,
                icon = R.drawable.ic_images,
                enabled = !importing,
                primary = route == null || route.stops.isEmpty(),
                minHeight = 52.dp,
                modifier = Modifier.ref(23).weight(1f),
            )
            HelpDot(R.string.home_share_hint, Modifier.refCorner(24).padding(start = 4.dp, bottom = 10.dp), title = stringResource(R.string.home_import))
        }

        youDrive()
        LinkCard(link, onToggleLink, onFixLink, cardRef = 26, switchRef = 27)
        OverlayCard(overlayPermission, overlayHidden, onOverlayVisible, onOverlayPermission, onAddTile)

        AppButton(
            text = stringResource(R.string.switch_to_display),
            onClick = onUseAsDisplay,
            icon = R.drawable.ic_display,
            primary = false,
            modifier = Modifier.ref(33).fillMaxWidth(),
        )
        if (route != null) {
            AppButton(
                text = stringResource(R.string.home_clear),
                onClick = { confirmClear = true },
                icon = R.drawable.ic_delete,
                primary = false,
                contentColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.ref(34).fillMaxWidth(),
            )
        }

        HistorySection(history, historyRetentionHours) { confirmClearHistory = true }
        Spacer(Modifier.height(16.dp))
    }
    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text(stringResource(R.string.history_clear)) },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmClearHistory = false; onClearHistory() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.confirm_clear_title)) },
            text = { Text(stringResource(R.string.confirm_clear_text)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Passenger display link (Bluetooth): on/off switch and its status. */
@Composable
fun LinkCard(link: DisplayLinkServer.State, onToggle: (Boolean) -> Unit, onFix: () -> Unit, cardRef: Int, switchRef: Int) {
    val on = link.status != DisplayLinkServer.Status.OFF
    val needsFix = link.status == DisplayLinkServer.Status.NO_PERMISSION || link.status == DisplayLinkServer.Status.BLUETOOTH_OFF
    val status = when (link.status) {
        DisplayLinkServer.Status.OFF -> stringResource(R.string.link_off)
        DisplayLinkServer.Status.NO_PERMISSION -> stringResource(R.string.link_no_permission)
        DisplayLinkServer.Status.NO_BLUETOOTH -> stringResource(R.string.link_no_bt)
        DisplayLinkServer.Status.BLUETOOTH_OFF -> stringResource(R.string.link_bt_off)
        DisplayLinkServer.Status.WAITING -> link.localName?.let { stringResource(R.string.link_waiting) + " · " + stringResource(R.string.link_device_name, it) }
            ?: stringResource(R.string.link_waiting)
        DisplayLinkServer.Status.CONNECTED -> stringResource(R.string.display_connected, link.clients.joinToString())
    }
    AppCard(Modifier.ref(cardRef)) {
        ListRow(
            title = stringResource(R.string.link_title),
            subtitle = status,
            subtitleColor = if (needsFix) NotLocated else null,
            icon = R.drawable.ic_bluetooth,
            iconTint = if (link.status == DisplayLinkServer.Status.CONNECTED) Located else Brand,
            help = if (link.status == DisplayLinkServer.Status.WAITING) R.string.link_waiting_hint else null,
            onClick = { if (needsFix) onFix() else onToggle(!on) },
            trailing = { Switch(checked = on, onCheckedChange = onToggle, modifier = Modifier.refCorner(switchRef)) },
        )
    }
}

/**
 * Floating button: show it again after "×", grant the overlay permission, or add the Quick
 * Settings tile ([onAddTile], Android 13+) that brings it back with one tap.
 */
@Composable
fun OverlayCard(permission: Boolean, hidden: Boolean, onVisible: (Boolean) -> Unit, onPermission: () -> Unit, onAddTile: (() -> Unit)? = null) {
    val shown = permission && !hidden
    val status = when {
        !permission -> stringResource(R.string.home_overlay_no_permission)
        hidden -> stringResource(R.string.home_overlay_hidden)
        else -> stringResource(R.string.home_overlay_shown)
    }
    AppCard(Modifier.ref(28)) {
        ListRow(
            title = stringResource(R.string.home_overlay_title),
            subtitle = status,
            subtitleColor = if (!permission) NotLocated else null,
            icon = if (shown) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
            iconTint = if (shown) Located else Brand,
            help = if (permission) R.string.home_overlay_restore_hint else R.string.home_overlay_restricted_hint,
            onClick = { if (!permission) onPermission() else onVisible(hidden) },
            trailing = {
                Switch(
                    checked = shown,
                    onCheckedChange = { on -> if (!permission) onPermission() else onVisible(on) },
                    modifier = Modifier.refCorner(29),
                )
            },
        )
        if (permission && onAddTile != null) {
            CardDivider()
            ListRow(
                title = stringResource(R.string.home_overlay_add_tile),
                icon = R.drawable.ic_tile,
                iconTint = Brand,
                onClick = onAddTile,
                trailing = { Chevron() },
                ref = 30,
            )
        }
    }
}

/** Trips of earlier routes (newest first): time, area and address, kept after "End". */
@Composable
private fun HistorySection(history: List<HistoryEntry>, retentionHours: Int, onClear: () -> Unit) {
    SectionTitle(
        stringResource(R.string.history_title),
        helpText = explain(R.string.history_retention_note, explainRetention(retentionHours)),
        helpRef = 35,
    )
    if (history.isEmpty()) {
        Text(
            stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        return
    }
    val today = remember { LocalDate.now() }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("d/M HH:mm") }
    AppCard {
        history.asReversed().forEachIndexed { i, e ->
            if (i > 0) CardDivider()
            val at = Instant.ofEpochMilli(e.atMs).atZone(ZoneId.systemDefault())
            val finished = if (at.toLocalDate() == today) at.format(timeFormat) else at.format(dateFormat)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (e.done) 1f else 0.7f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .ref(36),
            ) {
                Text(
                    e.time ?: "--:--",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (e.time != null) TimeColor else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.ref(37, centered = true),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.area, style = MaterialTheme.typography.titleSmall)
                    Text(
                        e.displayText,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (e.done) "✓ $finished" else stringResource(R.string.history_not_completed),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (e.done) Located else NotLocated,
                    modifier = Modifier.ref(38, centered = true),
                )
            }
        }
    }
    AppButton(
        text = stringResource(R.string.history_clear),
        onClick = onClear,
        icon = R.drawable.ic_delete,
        primary = false,
        contentColor = MaterialTheme.colorScheme.error,
        modifier = Modifier.ref(39).fillMaxWidth(),
    )
}

/** Retention period in the explanation language (see [explain]). */
@Composable
@ReadOnlyComposable
private fun explainRetention(hours: Int): String = when (hours) {
    24 -> explain(R.string.retention_24h)
    168 -> explain(R.string.retention_7d)
    else -> explain(R.string.retention_12h)
}

@Composable
fun retentionLabel(hours: Int): String = when (hours) {
    24 -> stringResource(R.string.retention_24h)
    168 -> stringResource(R.string.retention_7d)
    else -> stringResource(R.string.retention_12h)
}
