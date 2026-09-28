package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.core.geo.StreetInfo
import se.eldebosh.nastastopp.core.parse.TripTimes
import se.eldebosh.nastastopp.core.route.DetectorPhase
import se.eldebosh.nastastopp.route.TrackingState
import se.eldebosh.nastastopp.route.model.RouteData
import se.eldebosh.nastastopp.route.model.Stop
import se.eldebosh.nastastopp.ui.BigButton
import se.eldebosh.nastastopp.ui.ButtonRow
import se.eldebosh.nastastopp.ui.TopBar
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.theme.Amber
import se.eldebosh.nastastopp.ui.theme.Located
import se.eldebosh.nastastopp.ui.theme.NotLocated
import se.eldebosh.nastastopp.util.TimeLabels
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Active route: the trips stay listed in order, each starting with its time. Completed trips stay
 * visible but small, faded and condensed; the current trip is large; upcoming trips follow.
 */
@Composable
fun ActiveRouteScreen(
    route: RouteData,
    tracking: TrackingState,
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
    // Keep one completed trip visible above the current one.
    LaunchedEffect(route.completed.size) {
        listState.scrollToItem((route.completed.size - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.active_counts, route.completedCount, route.stops.size),
            onBack = onBack,
            backRef = 61,
            titleRef = 62,
            actions = {
                IconButton(onClick = onOpenDisplay, modifier = Modifier.ref(63).size(TouchTarget)) {
                    Icon(painterResource(R.drawable.ic_display), contentDescription = stringResource(R.string.open_display), modifier = Modifier.size(30.dp))
                }
                if (overlayAvailable) {
                    IconButton(onClick = onToggleOverlay, modifier = Modifier.ref(64).size(TouchTarget)) {
                        Icon(
                            painterResource(if (overlayHidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                            contentDescription = stringResource(if (overlayHidden) R.string.overlay_show else R.string.overlay_hide),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            },
        )
        if (hasLocationPermission) StreetBar(street, onSpeakStreet)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(route.completed, key = { _, s -> "done-${s.id}" }) { _, stop ->
                CompletedRow(stop, spokenName(stop))
            }
            if (current != null) {
                item(key = "current-${current.id}") {
                    CurrentCard(current, spokenName(current), statusText(current, tracking, hasLocationPermission), tracking.arrivedAtMs)
                }
            }
            if (route.stops.size > 1) {
                item(key = "then") {
                    Text(
                        stringResource(R.string.active_then_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.ref(75).padding(start = 4.dp, end = 4.dp, top = 8.dp),
                    )
                }
            }
            itemsIndexed(route.stops.drop(1), key = { _, s -> "next-${s.id}" }) { i, stop ->
                UpcomingRow(index = i + 2, stop = stop, area = spokenName(stop))
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ButtonRow {
                BigButton(
                    stringResource(R.string.overlay_back), onPreviousTrip, Modifier.ref(77).weight(1.3f),
                    icon = R.drawable.ic_previous, primary = false, enabled = route.completed.isNotEmpty(), minHeight = 96.dp,
                )
                BigButton(stringResource(R.string.btn_next), onNext, Modifier.ref(78).weight(2f), icon = R.drawable.ic_next, minHeight = 96.dp)
            }
            ButtonRow {
                BigButton(stringResource(R.string.btn_repeat), onRepeat, Modifier.ref(79).weight(1f), icon = R.drawable.ic_repeat, primary = false)
                BigButton(stringResource(R.string.btn_open_maps), onOpenMaps, Modifier.ref(80).weight(1f), icon = R.drawable.ic_navigation, primary = false)
            }
            ButtonRow {
                BigButton(stringResource(R.string.btn_edit_list), onEdit, Modifier.ref(81).weight(1f), icon = R.drawable.ic_edit, primary = false)
                BigButton(
                    stringResource(R.string.btn_end), { confirmEnd = true }, Modifier.ref(82).weight(1f),
                    icon = R.drawable.ic_stop, primary = false, contentColor = MaterialTheme.colorScheme.error,
                )
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
        modifier = Modifier.ref(67).fillMaxWidth().alpha(0.45f).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(painterResource(R.drawable.ic_located), contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        if (stop.time != null) {
            Text(stop.time, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Amber)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            "$area · ${stop.displayText}",
            fontSize = 14.sp,
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
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Icon(painterResource(R.drawable.ic_pin), contentDescription = null, tint = Amber, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.street_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val name = street?.street ?: street?.area
            Text(
                name ?: stringResource(R.string.street_unknown),
                style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (street?.street != null && street.area != null) {
                Text(street.area, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onSpeak, enabled = street?.spoken != null, modifier = Modifier.ref(66).size(TouchTarget)) {
            Icon(painterResource(R.drawable.ic_speaker), contentDescription = stringResource(R.string.overlay_speak_street_desc), modifier = Modifier.size(28.dp))
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
    val color = Color(TimeLabels.color(TripTimes.level(until)))
    val label = remember(until, resources) { TimeLabels.until(context, until) }
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.ref(70).clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

@Composable
private fun CurrentCard(current: Stop, area: String, status: String, arrivedAtMs: Long?) {
    val nowMs = rememberNowMs()
    Column(
        Modifier
            .ref(68)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (current.time != null) {
                Text(current.time, style = MaterialTheme.typography.headlineMedium, color = Amber, modifier = Modifier.ref(69))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                stringResource(R.string.active_next_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            if (current.time != null) TimeStatusChip(current.time, nowMs)
        }
        Text(
            area,
            style = MaterialTheme.typography.displaySmall.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.ref(71),
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (current.isLocated) R.drawable.ic_located else R.drawable.ic_not_located),
                contentDescription = stringResource(if (current.isLocated) R.string.stop_located else R.string.stop_not_located),
                tint = if (current.isLocated) Located else NotLocated,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                current.displayText,
                style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.ref(72),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.ref(73))
        if (arrivedAtMs != null) {
            Text(
                stringResource(R.string.wait_at_stop, TimeLabels.duration(nowMs - arrivedAtMs)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Amber,
                modifier = Modifier.ref(74),
            )
        }
    }
}

@Composable
private fun UpcomingRow(index: Int, stop: Stop, area: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .ref(76)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            stop.time ?: "--:--",
            style = MaterialTheme.typography.titleMedium,
            color = if (stop.time != null) Amber else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$index. $area", style = MaterialTheme.typography.titleMedium)
            Text(
                stop.displayText,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusText(stop: Stop, tracking: TrackingState, hasLocation: Boolean): String = when {
    !hasLocation -> stringResource(R.string.status_no_location)
    !stop.isLocated || !tracking.autoEnabled -> stringResource(R.string.status_manual_only)
    tracking.phase == DetectorPhase.ARRIVED -> stringResource(R.string.status_arrived)
    tracking.distanceM != null -> stringResource(R.string.status_distance, tracking.distanceM.roundToInt())
    else -> stringResource(R.string.status_waiting_gps)
}
