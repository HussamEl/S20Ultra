package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.Hint
import se.eldebosh.nastastopp.core.parse.TripTimes
import se.eldebosh.nastastopp.route.model.GeoStatus
import se.eldebosh.nastastopp.route.model.RouteData
import se.eldebosh.nastastopp.route.model.Stop
import se.eldebosh.nastastopp.ui.BigButton
import se.eldebosh.nastastopp.ui.ButtonRow
import se.eldebosh.nastastopp.ui.TopBar
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.theme.Amber
import se.eldebosh.nastastopp.ui.theme.Located
import se.eldebosh.nastastopp.ui.theme.NotLocated

@Composable
fun ReviewScreen(
    route: RouteData?,
    spokenName: (Stop) -> String,
    importing: Boolean,
    onBack: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (Stop) -> Unit,
    onDeleteAbove: (Stop) -> Unit,
    onEdit: (Stop, String, String?) -> Boolean,
    onRetry: (Stop) -> Unit,
    onAddManual: (String, String?) -> Boolean,
    onSortByTime: () -> Unit,
    onAddScreenshots: () -> Unit,
    onStart: () -> Unit,
    onBackToRoute: () -> Unit,
) {
    val stops = route?.stops.orEmpty()
    val active = route?.active == true
    var editing by remember { mutableStateOf<Stop?>(null) }
    var adding by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val move by rememberUpdatedState(onMove)
    val reorder = remember(listState) { ReorderState(listState) { from, to -> move(from, to) } }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.review_title), onBack = onBack, backRef = 40) {
            if (stops.count { it.time != null } >= 2) {
                IconButton(onClick = onSortByTime, modifier = Modifier.ref(41).size(TouchTarget)) {
                    Icon(painterResource(R.drawable.ic_schedule), contentDescription = stringResource(R.string.sort_by_time), modifier = Modifier.size(30.dp))
                }
            }
        }
        Text(
            stringResource(R.string.review_count, stops.size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.ref(42).padding(horizontal = 16.dp),
        )
        Hint(R.string.review_hint, Modifier.ref(43).fillMaxWidth().padding(horizontal = 16.dp))
        if (stops.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (importing) CircularProgressIndicator()
                else Text(stringResource(R.string.review_empty), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(stops, key = { _, s -> s.id }) { index, stop ->
                    val dragging = reorder.draggingKey == stop.id
                    Box(
                        Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) reorder.offset else 0f },
                    ) {
                        SwipeableStopRow(
                            index = index,
                            stop = stop,
                            spoken = spokenName(stop),
                            dragging = dragging,
                            reorder = reorder,
                            onClick = { editing = stop },
                            onDelete = { onDelete(stop) },
                            onDeleteAbove = { onDeleteAbove(stop) },
                            onRetry = { onRetry(stop) },
                        )
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ButtonRow {
                BigButton(stringResource(R.string.review_add_manual), { adding = true }, Modifier.ref(50).weight(1f), icon = R.drawable.ic_add, primary = false)
                BigButton(stringResource(R.string.review_add_screens), onAddScreenshots, Modifier.ref(51).weight(1f), icon = R.drawable.ic_images, primary = false, enabled = !importing)
            }
            if (active) {
                BigButton(stringResource(R.string.review_back_to_route), onBackToRoute, Modifier.ref(52).fillMaxWidth(), icon = R.drawable.ic_navigation, minHeight = 76.dp)
            } else {
                BigButton(
                    stringResource(R.string.review_start), onStart, Modifier.ref(52).fillMaxWidth(),
                    icon = R.drawable.ic_navigation, enabled = stops.isNotEmpty(), minHeight = 76.dp,
                )
            }
        }
    }

    editing?.let { stop ->
        AddressDialog(
            title = stringResource(R.string.dialog_edit_title),
            initial = stop.displayText,
            initialTime = stop.time.orEmpty(),
            onDismiss = { editing = null },
            onSave = { text, time -> onEdit(stop, text, time).also { if (it) editing = null } },
        )
    }
    if (adding) {
        AddressDialog(
            title = stringResource(R.string.dialog_add_title),
            initial = "",
            initialTime = "",
            onDismiss = { adding = false },
            onSave = { text, time -> onAddManual(text, time).also { if (it) adding = false } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableStopRow(
    index: Int,
    stop: Stop,
    spoken: String,
    dragging: Boolean,
    reorder: ReorderState,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDeleteAbove: () -> Unit,
    onRetry: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    SwipeToDismissBox(
        state = dismissState,
        onDismiss = { value -> if (value != SwipeToDismissBoxValue.Settled) onDelete() },
        gesturesEnabled = !dragging,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete), modifier = Modifier.size(32.dp))
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .ref(45)
                .fillMaxWidth()
                .clip(shape)
                .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer)
                .heightIn(min = 84.dp),
        ) {
            // Drag handle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .ref(44)
                    .size(width = TouchTarget, height = 84.dp)
                    .pointerInput(stop.id) {
                        detectDragGestures(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                reorder.start(stop.id)
                            },
                            onDragEnd = { reorder.end() },
                            onDragCancel = { reorder.end() },
                            onDrag = { change, amount ->
                                change.consume()
                                reorder.drag(amount.y)
                            },
                        )
                    },
            ) {
                Icon(painterResource(R.drawable.ic_drag), contentDescription = null, modifier = Modifier.size(32.dp))
            }
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu = true
                            },
                        )
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    if (stop.time != null) {
                        Text(stop.time, style = MaterialTheme.typography.titleMedium, color = Amber, fontWeight = FontWeight.Bold, modifier = Modifier.ref(46))
                    }
                    Text(
                        "${index + 1}.  ${stop.displayText}",
                        style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.ref(47),
                    )
                    Text(
                        stringResource(R.string.spoken_label, spoken),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.ref(48),
                    )
                    if (stop.geoStatus == GeoStatus.NOT_LOCATED) {
                        Text(stringResource(R.string.stop_not_located), style = MaterialTheme.typography.bodySmall, color = NotLocated)
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit), style = MaterialTheme.typography.titleMedium) },
                        onClick = { menu = false; onClick() },
                        modifier = Modifier.ref(57).heightIn(min = TouchTarget),
                    )
                    if (index > 0) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_delete_above), style = MaterialTheme.typography.titleMedium) },
                            onClick = { menu = false; onDeleteAbove() },
                            modifier = Modifier.ref(58).heightIn(min = TouchTarget),
                        )
                    }
                    if (stop.geoStatus == GeoStatus.NOT_LOCATED) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_retry), style = MaterialTheme.typography.titleMedium) },
                            onClick = { menu = false; onRetry() },
                            modifier = Modifier.ref(59).heightIn(min = TouchTarget),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                        modifier = Modifier.ref(60).heightIn(min = TouchTarget),
                    )
                }
            }
            Box(Modifier.ref(49).size(width = 56.dp, height = 84.dp), contentAlignment = Alignment.Center) {
                when (stop.geoStatus) {
                    GeoStatus.PENDING -> {
                        val locating = stringResource(R.string.stop_locating)
                        CircularProgressIndicator(Modifier.size(28.dp).semantics { contentDescription = locating }, strokeWidth = 3.dp)
                    }
                    GeoStatus.LOCATED -> Icon(
                        painterResource(R.drawable.ic_located), contentDescription = stringResource(R.string.stop_located),
                        tint = Located, modifier = Modifier.size(32.dp),
                    )
                    GeoStatus.NOT_LOCATED -> Icon(
                        painterResource(R.drawable.ic_not_located), contentDescription = stringResource(R.string.stop_not_located),
                        tint = NotLocated, modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun AddressDialog(
    title: String,
    initial: String,
    initialTime: String,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Boolean,
) {
    var text by remember { mutableStateOf(initial) }
    var time by remember { mutableStateOf(initialTime) }
    var error by remember { mutableStateOf(false) }
    var timeError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    placeholder = { Text(stringResource(R.string.dialog_address_hint)) },
                    isError = error,
                    supportingText = if (error) ({ Text(stringResource(R.string.invalid_address)) }) else null,
                    textStyle = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                    minLines = 2,
                    modifier = Modifier.ref(53).fillMaxWidth(),
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it; timeError = false },
                    label = { Text(stringResource(R.string.dialog_time_label)) },
                    isError = timeError,
                    supportingText = if (timeError) ({ Text(stringResource(R.string.invalid_time)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                    modifier = Modifier.ref(54).fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedTime = if (time.isBlank()) null else TripTimes.normalizeTyped(time)
                    if (time.isNotBlank() && parsedTime == null) {
                        timeError = true
                    } else if (!onSave(text.trim(), parsedTime)) {
                        error = true
                    }
                },
                modifier = Modifier.ref(55).heightIn(min = TouchTarget),
            ) {
                Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.ref(56).heightIn(min = TouchTarget)) {
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
