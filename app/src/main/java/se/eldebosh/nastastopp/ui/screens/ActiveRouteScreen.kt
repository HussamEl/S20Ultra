package se.eldebosh.nastastopp.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.preferKeepClear
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.geo.StreetInfo
import se.eldebosh.nastastopp.core.parse.TripKind
import se.eldebosh.nastastopp.core.parse.TripTimes
import se.eldebosh.nastastopp.route.model.RouteData
import se.eldebosh.nastastopp.route.model.Stop
import se.eldebosh.nastastopp.ui.AppButton
import se.eldebosh.nastastopp.ui.ButtonRow
import se.eldebosh.nastastopp.ui.KindLabel
import se.eldebosh.nastastopp.ui.TopBar
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.TripSurface
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.AppTheme
import se.eldebosh.nastastopp.util.TimeLabels
import java.time.Instant
import java.time.ZoneId

/**
 * Active route: the trips stay listed in order, each starting with its time. Completed trips stay
 * visible but small, faded and condensed; the current trip is large; upcoming trips follow.
 */
@Composable
fun ActiveRouteScreen(
    route: RouteData,
    hasLocationPermission: Boolean,
    spokenName: (Stop) -> String,
    overlayAvailable: Boolean,
    overlayHidden: Boolean,
    street: StreetInfo?,
    onSpeakStreet: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPreviousTrip: () -> Unit,
    onRepeat: () -> Unit,
    onOpenMaps: () -> Unit,
    onEdit: () -> Unit,
    onEnd: () -> Unit,
    onOpenDisplay: () -> Unit,
    onToggleOverlay: () -> Unit,
) {
    // Keep the screen on while this screen is visible.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var confirmEnd by remember { mutableStateOf(false) }
    val current = route.stops.firstOrNull()
    val listState = rememberLazyListState()
    // Keep one completed trip (or the start point) visible above the current one.
    val lead = if (route.depot != null) 1 else 0
    LaunchedEffect(route.completed.size, lead) {
        listState.scrollToItem((lead + route.completed.size - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.active_counts, route.completedCount, route.stops.size),
            onBack = onBack,
            backRef = 61,
            titleRef = 62,
            actions = {
                IconButton(onClick = onOpenDisplay, modifier = Modifier.refCorner(63).size(TouchTarget)) {
                    Icon(painterResource(R.drawable.ic_display), contentDescription = stringResource(R.string.open_display), modifier = Modifier.size(22.dp))
                }
                if (overlayAvailable) {
                    IconButton(onClick = onToggleOverlay, modifier = Modifier.refCorner(64).size(TouchTarget)) {
                        Icon(
                            painterResource(if (overlayHidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                            contentDescription = stringResource(if (overlayHidden) R.string.overlay_show else R.string.overlay_hide),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                // End sits up here, not with the driving buttons: Maps' picture-in-picture window
                // covers the bottom-right corner (V3), and ending is rare and asks first anyway.
                IconButton(onClick = { confirmEnd = true }, modifier = Modifier.refCorner(82).size(TouchTarget)) {
                    Icon(
                        painterResource(R.drawable.ic_stop),
                        contentDescription = stringResource(R.string.btn_end),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )
        if (hasLocationPermission) StreetBar(street, onSpeakStreet)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            route.depot?.let { depot -> item(key = "depot") { DepotRow(depot) } }
            itemsIndexed(route.completed, key = { _, s -> "done-${s.id}" }) { _, stop ->
                CompletedRow(stop, spokenName(stop))
            }
            if (current != null) {
                item(key = "current-${current.id}") {
                    CurrentCard(current, spokenName(current), statusText())
                }
            }
            if (route.stops.size > 1) {
                item(key = "then") {
                    Text(
                        stringResource(R.string.active_then_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.ref(75).padding(start = 4.dp, end = 4.dp, top = 6.dp),
                    )
                }
            }
            itemsIndexed(route.stops.drop(1), key = { _, s -> "next-${s.id}" }) { i, stop ->
                UpcomingRow(index = i + 2, stop = stop, area = spokenName(stop))
            }
        }
        HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.cardBorder)
        // preferKeepClear: asks the system to keep floating windows (Maps' picture-in-picture) off
        // the driving buttons. Honoured only where the system supports it (Android 13+, not all).
        Column(
            Modifier.fillMaxWidth().preferKeepClear().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ButtonRow {
                AppButton(
                    stringResource(R.string.overlay_back), onPreviousTrip, Modifier.ref(77).weight(1f),
                    icon = R.drawable.ic_previous, primary = false, enabled = route.completed.isNotEmpty(), minHeight = 60.dp,
                )
                AppButton(
                    stringResource(R.string.btn_next), onNext, Modifier.ref(78).weight(1.6f),
                    icon = R.drawable.ic_next, minHeight = 60.dp, containerColor = AppTheme.colors.accent, contentColor = AppTheme.colors.onAccent,
                )
            }
            ButtonRow {
                ActionTile(R.drawable.ic_repeat, stringResource(R.string.btn_repeat), 79, onRepeat)
                ActionTile(R.drawable.ic_navigation, stringResource(R.string.btn_open_maps), 80, onOpenMaps)
                ActionTile(R.drawable.ic_edit, stringResource(R.string.btn_edit_list), 81, onEdit)
            }
        }
    }
    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(R.string.confirm_end_title)) },
            text = { Text(stringResource(R.string.confirm_end_text)) },
            confirmButton = { TextButton(onClick = { confirmEnd = false; onEnd() }, modifier = Modifier.ref(83)) { Text(stringResource(R.string.btn_end)) } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }, modifier = Modifier.ref(84)) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Completed trip: time kept visible, but small, faded and on one line. */
@Composable
private fun CompletedRow(stop: Stop, area: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.ref(67).fillMaxWidth().alpha(0.5f).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(painterResource(R.drawable.ic_located), contentDescription = null, tint = AppTheme.colors.success, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        if (stop.time != null) {
            Text(stop.time, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.time)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            listOfNotNull(stop.name, area, stop.displayText).joinToString(" · "),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
        )
    }
}

/** The street the vehicle is on now, with a speaker button that says it. */
@Composable
private fun StreetBar(street: StreetInfo?, onSpeak: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .ref(65)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Icon(painterResource(R.drawable.ic_pin), contentDescription = null, tint = AppTheme.colors.info, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.street_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val name = street?.street ?: street?.area
            Text(
                name ?: stringResource(R.string.street_unknown),
                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (street?.street != null && street.area != null) {
                Text(street.area, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onSpeak, enabled = street?.spoken != null, modifier = Modifier.refCorner(66).size(TouchTarget)) {
            Icon(painterResource(R.drawable.ic_speaker), contentDescription = stringResource(R.string.overlay_speak_street_desc), modifier = Modifier.size(22.dp))
        }
    }
}

/** Current time in milliseconds, updated every second while shown. */
@Composable
private fun rememberNowMs(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000 - now % 1_000)
        }
    }
    return now
}

/** "in 7 min" / "5 min late" next to the trip's scheduled time (green, yellow, red). */
@Composable
private fun TimeStatusChip(time: String, nowMs: Long) {
    val now = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalTime()
    val until = TripTimes.minutesUntil(time, now.hour * 60 + now.minute) ?: return
    val resources = LocalResources.current
    val context = LocalContext.current
    val color = AppTheme.colors.status(TripTimes.level(until))
    val label = remember(until, resources) { TimeLabels.until(context, until) }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = AppTheme.colors.onStatus,
        modifier = Modifier.ref(70).clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * The current trip, like YouDrive's active card: the trip's colour (green pick-up, white
 * drop-off) inside a thick border, the time on a yellow pill, the passenger's name beside it.
 */
@Composable
private fun CurrentCard(current: Stop, area: String, status: String) {
    val nowMs = rememberNowMs()
    TripSurface(current.kind, Modifier.ref(68).fillMaxWidth(), current = true) {
        Column(Modifier.animateContentSize().padding(16.dp)) {
            // First line, as on YouDrive's card: the time and the passenger's first and last
            // name level with it (the driver's screen only), then how late or early it is.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current.time != null) {
                    Text(
                        current.time,
                        style = MaterialTheme.typography.headlineSmall,
                        color = AppTheme.colors.onAccent,
                        modifier = Modifier
                            .ref(69)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppTheme.colors.accent)
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                if (current.name != null) {
                    Text(
                        current.name,
                        style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
                        color = AppTheme.colors.onTrip,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.ref(135, centered = true).weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (current.time != null) TimeStatusChip(current.time, nowMs)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.active_next_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.onTrip,
                )
                if (current.kind != null) {
                    Spacer(Modifier.width(8.dp))
                    KindLabel(current.kind, Modifier.ref(128, centered = true))
                }
            }
            Text(
                area,
                style = MaterialTheme.typography.headlineLarge.copy(textDirection = TextDirection.Content),
                color = AppTheme.colors.onTrip,
                modifier = Modifier.ref(71),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painterResource(if (current.isLocated) R.drawable.ic_located else R.drawable.ic_not_located),
                    contentDescription = stringResource(if (current.isLocated) R.string.stop_located else R.string.stop_not_located),
                    tint = if (current.isLocated) AppTheme.colors.success else AppTheme.colors.danger,
                    modifier = Modifier.padding(top = 2.dp).size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    current.displayText,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                    color = AppTheme.colors.onTrip,
                    modifier = Modifier.ref(72),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.onTripMuted, modifier = Modifier.ref(73))
        }
    }
}

/** A coming trip on its YouDrive colour (green pick-up, white drop-off, grey depot). */
@Composable
private fun UpcomingRow(index: Int, stop: Stop, area: String) {
    TripSurface(stop.kind, Modifier.ref(76).fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // First line: the time and the passenger's name level with it, as on YouDrive's card.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stop.time ?: "--:--",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (stop.time != null) AppTheme.colors.time else MaterialTheme.colorScheme.outline,
                )
                if (stop.name != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stop.name,
                        style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content),
                        color = AppTheme.colors.onTrip,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (stop.kind != null) {
                    Spacer(Modifier.width(8.dp))
                    KindLabel(stop.kind)
                }
            }
            Text("$index. $area", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.onTrip)
            Text(
                stop.displayText,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = AppTheme.colors.onTripMuted,
            )
        }
    }
}

/** The day's start point (YouDrive's grey Pull-out card): shown, but not a stop. */
@Composable
private fun DepotRow(depot: Stop) {
    TripSurface(TripKind.PULL_OUT, Modifier.ref(85).fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            KindLabel(TripKind.PULL_OUT)
            Spacer(Modifier.width(10.dp))
            if (depot.time != null) {
                Text(depot.time, style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.onTrip)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                depot.displayText,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = AppTheme.colors.onTrip,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A small square action under Next (icon above a short label). */
@Composable
private fun RowScope.ActionTile(@DrawableRes icon: Int, label: String, ref: Int, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .ref(ref)
            .weight(1f)
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, AppTheme.colors.cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun statusText(): String = stringResource(R.string.status_tap_next)
