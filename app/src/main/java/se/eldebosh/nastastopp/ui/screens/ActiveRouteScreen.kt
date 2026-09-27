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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.route.DetectorPhase
import se.eldebosh.nastastopp.route.TrackingState
import se.eldebosh.nastastopp.route.model.RouteData
import se.eldebosh.nastastopp.route.model.Stop
import se.eldebosh.nastastopp.ui.BigButton
import se.eldebosh.nastastopp.ui.ButtonRow
import se.eldebosh.nastastopp.ui.TopBar
import se.eldebosh.nastastopp.ui.theme.Located
import se.eldebosh.nastastopp.ui.theme.NotLocated
import kotlin.math.roundToInt

@Composable
fun ActiveRouteScreen(
    route: RouteData,
    tracking: TrackingState,
    hasLocationPermission: Boolean,
    spokenName: (Stop) -> String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onOpenMaps: () -> Unit,
    onEdit: () -> Unit,
    onEnd: () -> Unit,
) {
    // Keep the screen on while this screen is visible.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var confirmEnd by remember { mutableStateOf(false) }
    val current = route.stops.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.active_counts, route.completedCount, route.stops.size),
            onBack = onBack,
        )
        if (current != null) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(18.dp),
            ) {
                Text(stringResource(R.string.active_next_label), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    spokenName(current),
                    style = MaterialTheme.typography.displaySmall.copy(textDirection = TextDirection.Content),
                    color = MaterialTheme.colorScheme.primary,
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
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(statusText(current, tracking, hasLocationPermission), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(
            stringResource(R.string.active_then_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(route.stops.drop(1), key = { _, s -> s.id }) { i, stop ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text("${i + 2}. ${spokenName(stop)}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        stop.displayText,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(stringResource(R.string.btn_next), onNext, Modifier.fillMaxWidth(), icon = R.drawable.ic_next, minHeight = 96.dp)
            ButtonRow {
                BigButton(stringResource(R.string.btn_repeat), onRepeat, Modifier.weight(1f), icon = R.drawable.ic_repeat, primary = false)
                BigButton(stringResource(R.string.btn_open_maps), onOpenMaps, Modifier.weight(1f), icon = R.drawable.ic_navigation, primary = false)
            }
            ButtonRow {
                BigButton(stringResource(R.string.btn_edit_list), onEdit, Modifier.weight(1f), icon = R.drawable.ic_edit, primary = false)
                BigButton(
                    stringResource(R.string.btn_end), { confirmEnd = true }, Modifier.weight(1f),
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
            confirmButton = { TextButton(onClick = { confirmEnd = false; onEnd() }) { Text(stringResource(R.string.btn_end)) } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.cancel)) } },
        )
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
