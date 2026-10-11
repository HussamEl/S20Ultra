package se.eldebosh.nastastopp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.display.DisplayItem
import se.eldebosh.nastastopp.core.display.TimeStatus
import se.eldebosh.nastastopp.core.nav.OrderPlanner
import se.eldebosh.nastastopp.core.nav.RouteLine
import se.eldebosh.nastastopp.core.parse.TripKind
import se.eldebosh.nastastopp.core.parse.TripTimes
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.AppTheme
import se.eldebosh.nastastopp.ui.theme.DigitFont
import se.eldebosh.nastastopp.ui.theme.DisplayFont

/**
 * The order the driver is trying on his map ([preview], null: the phone's), the trip he picked to
 * move ([picked]), whether he is dragging one ([dragging]: the way is asked for once he lets go),
 * the order he sent to the phone ([sent], until it comes back) and what Google's travel times said
 * of the order ([advice]).
 */
@Stable
internal class WayEdit {
    var preview by mutableStateOf<List<DisplayItem>?>(null)
    var picked by mutableStateOf<DisplayItem?>(null)
    var dragging by mutableStateOf(false)
    var sent by mutableStateOf<List<Long>?>(null)
    var advice by mutableStateOf<Advice?>(null)

    /**
     * The best order is the one shown ([same]), or it saves [savedSeconds] and leaves [lateAfter]
     * minutes late (was [lateBefore]); [unknownBefore] when the order shown had no way or could
     * not be used, so there is nothing to compare with.
     */
    data class Advice(val same: Boolean, val savedSeconds: Int = 0, val lateBefore: Int = 0, val lateAfter: Int = 0, val unknownBefore: Boolean = false)

    fun clear() {
        preview = null
        picked = null
        dragging = false
        sent = null
        advice = null
    }

    /**
     * The trips of the way changed under the order tried ([window], the phone's order). A trip the
     * driver added comes into the order tried in its place: before it when it comes before its
     * trips, else after them. The order tried is dropped when it now is the phone's (sent and
     * taken), or when one of its trips is no longer on the way.
     */
    fun settle(window: List<DisplayItem>) {
        val p = preview ?: return
        if (!p.all { t -> window.any { it.sameTrip(t) } }) {
            preview = null
            sent = null
            return
        }
        val first = window.indexOfFirst { w -> p.any { it.sameTrip(w) } }
        val added = window.filter { w -> p.none { it.sameTrip(w) } }
        val merged = if (added.isEmpty()) {
            p
        } else {
            val before = window.take(first).filter { w -> added.any { it.sameTrip(w) } }
            before + p + added.filter { a -> before.none { it.sameTrip(a) } }
        }
        if (merged.indices.all { merged[it].sameTrip(window[it]) }) {
            preview = null
            sent = null
        } else {
            preview = merged
        }
    }

    /** Where the trip looked at is in [shown]: the one picked, else the one the map was opened for ([opened]). */
    fun lookedIndex(shown: List<DisplayItem>, opened: Int): Int =
        picked?.let { p -> shown.indexOfFirst { it.sameTrip(p) } }?.takeIf { it >= 0 }
            ?: opened.coerceIn(0, shown.lastIndex.coerceAtLeast(0))

    /** Moves the trip at [from] of [shown] to [to] (a neighbour's place, or one step with the arrows). */
    fun move(shown: List<DisplayItem>, from: Int, to: Int) {
        if (from !in shown.indices || to !in shown.indices || from == to) return
        preview = shown.toMutableList().apply { add(to, removeAt(from)) }
        advice = null
    }
}

/** The same trip in another snapshot: its number, or what it shows. */
internal fun DisplayItem.sameTrip(other: DisplayItem): Boolean =
    if (id != null && other.id != null) id == other.id else trip == other.trip

/**
 * Asks Google for the best order of [shown] ([RouteMap.suggest]). Lateness counts only from the
 * next stop ([fromNext]): a way that starts later skips the trips before it.
 */
internal fun suggestOrder(map: RouteMap, shown: List<DisplayItem>, fromNext: Boolean, now: LocalTime) {
    val trips = plannerTrips(shown)
    map.suggest(shown.map { it.mapStop }, if (fromNext) trips else trips.map { it.copy(booked = null) }, now.toSecondOfDay())
}

/** The trips for [OrderPlanner]: booked time, pick-up or drop-off, passenger's number. */
internal fun plannerTrips(trips: List<DisplayItem>): List<OrderPlanner.Trip> = trips.map {
    OrderPlanner.Trip(
        booked = TripTimes.minutes(it.time).takeIf { m -> m != Int.MAX_VALUE },
        pickUp = when (it.kind) {
            TripKind.PICK_UP -> true
            TripKind.DROP_OFF -> false
            else -> null
        },
        rider = it.rider,
    )
}

/**
 * The driver's way on his map (the tablet), a small list at its left, read up close: its trips in
 * order, lettered as on the map (A, B, C…; the one he looks at red), each with its time, a dot for
 * its kind, the start of its street, its leg's minutes and, when the way starts at the next stop,
 * when it is reached, in the clock's status colours (a stop takes [OrderPlanner.DWELL_SECONDS]).
 * Above them: the minutes and distance to the one looked at, the whole way's, and how much the
 * order tried saves or costs; what keeps the map or the way from coming ([note], 233).
 *
 * A trip is dragged by its handle to another place (245), or tapped to pick it (the map turns to
 * it) and moved a step with its arrows (246, 247): the map shows the new order with its times.
 * "+" (260) adds the next trip to the way, in its place ([onAdd]). "Suggest" (248) asks Google for
 * the travel times between them all and puts the best order up ([OrderPlanner]); "Undo" (249) goes
 * back; "Use" (250) sends the order to the phone ([onOrder]). An order with a passenger's drop-off
 * before their pick-up cannot be used.
 *
 * The list is a window ([FloatingWindow], [place]): its bar moves it (265), its border makes it
 * bigger or smaller from any edge or corner (266), and two fingers on it do both; it opens where and
 * as big as the driver last left it. The map keeps the way clear of it ([RouteMap.listAt]).
 */
@Composable
internal fun WayList(
    visible: Boolean,
    map: RouteMap,
    edit: WayEdit,
    shown: List<DisplayItem>,
    at: Int,
    window: List<DisplayItem>,
    fromNext: Boolean,
    now: () -> LocalTime,
    note: String?,
    onOrder: ((List<Long>) -> Unit)?,
    onAdd: (() -> Unit)?,
    onAddEarlier: (() -> Unit)?,
    onRemove: (DisplayItem) -> Unit,
    onUse: () -> Unit,
    place: WindowState,
    modifier: Modifier = Modifier,
) {
    val key = RouteMap.keyOf(shown.map { it.mapStop })
    val line = map.route?.takeIf { map.routeKey == key }?.takeIf { it.legs.size == shown.size }
    // The phone's order's whole way, to compare an order tried with.
    val windowKey = RouteMap.keyOf(window.map { it.mapStop })
    var base by remember(windowKey) { mutableStateOf<Int?>(null) }
    LaunchedEffect(map.routeKey, map.route, windowKey) {
        if (map.routeKey == windowKey) map.route?.legs?.takeIf { it.size == window.size }?.let { base = wholeSeconds(it.map { l -> l.seconds }) }
    }
    val trips = plannerTrips(shown)
    val order = shown.indices.toList()
    val allowed = OrderPlanner.allowed(order, trips)
    val clashing = if (allowed) emptySet() else clashes(trips)
    val changed = edit.preview != null
    // Google's best order for the trips shown: put up to be weighed, never taken by itself.
    LaunchedEffect(map.suggestion) {
        val s = map.suggestion ?: return@LaunchedEffect
        if (s.key != key) return@LaunchedEffect
        if (s.best.order == order) {
            edit.advice = WayEdit.Advice(same = true)
        } else {
            edit.preview = s.best.order.map { shown[it] }
            edit.picked = null
            val current = s.current
            edit.advice = if (current == null) {
                WayEdit.Advice(false, lateAfter = s.best.lateMinutes, unknownBefore = true)
            } else {
                WayEdit.Advice(false, (current.seconds - s.best.seconds).coerceAtLeast(0), current.lateMinutes, s.best.lateMinutes)
            }
        }
    }
    // Dragged by its handle: the trip follows the finger and takes a neighbour's place as it passes it.
    val list = rememberLazyListState()
    val latest by rememberUpdatedState(shown)
    val reorder = remember(list) {
        ReorderState(list) { from, to ->
            onUse()
            edit.move(latest, from, to)
        }
    }
    val keys = rowKeys(shown)
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(LIST_IN_MS)), exit = fadeOut(tween(LIST_OUT_MS))) {
        FloatingWindow(place, edgeRef = 266, onTouch = onUse) {
            Column(
                Modifier
                    .width(LIST_WIDTH)
                    .clip(RoundedCornerShape(LIST_CORNER))
                    .background(AppTheme.colors.card.copy(alpha = LIST_GROUND))
                    .onGloballyPositioned { c ->
                        val whole = c.findRootCoordinates().size.width.toFloat()
                        val bounds = c.boundsInRoot()
                        if (whole > 0f) map.listAt(bounds.left / whole, bounds.right / whole)
                    }
                    .padding(LIST_PAD),
            ) {
                // The window's bar: a finger on it moves the list.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val move = stringResource(R.string.window_move)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(BAR_HEIGHT)
                            .ref(265, centered = true)
                            .clip(RoundedCornerShape(BAR_HEIGHT / 2))
                            .background(AppTheme.colors.tonal)
                            .movesWindow(place)
                            .semantics { contentDescription = move },
                    ) {
                        Icon(painterResource(R.drawable.ic_drag), contentDescription = null, tint = AppTheme.colors.textMuted, modifier = Modifier.size(HANDLE_ICON))
                    }
                }
                Spacer(Modifier.height(LIST_GAP))
                Column(Modifier.ref(251)) {
                    WayHeader(map, line, at, changed, base)
                    val words = adviceText(edit.advice, map.suggesting) ?: if (!allowed) stringResource(R.string.display_way_pickup_first) else null
                    if (words != null) {
                        Text(
                            words,
                            fontFamily = DisplayFont,
                            fontSize = LIST_SMALL_SP,
                            color = if (!allowed) AppTheme.colors.danger else AppTheme.colors.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (note != null) {
                        Text(
                            note,
                            fontFamily = DisplayFont,
                            fontSize = LIST_SMALL_SP,
                            color = AppTheme.colors.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.ref(233),
                        )
                    }
                }
                Spacer(Modifier.height(LIST_GAP))
                // The legs add up from the vehicle, each with the stop before it.
                val arrivals = IntArray(shown.size) { -1 }
                var t = 0
                shown.indices.forEach { i ->
                    val leg = line?.legs?.getOrNull(i)?.seconds ?: return@forEach
                    t += leg
                    if (fromNext) arrivals[i] = t
                    t += OrderPlanner.DWELL_SECONDS
                }
                LazyColumn(
                    state = list,
                    verticalArrangement = Arrangement.spacedBy(ROW_GAP),
                    // Last to be measured: on a small screen or a large window, the trips scroll and the buttons stay.
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = LIST_MAX_HEIGHT),
                ) {
                    itemsIndexed(shown, key = { i, _ -> keys[i] }) { i, item ->
                        val dragged = reorder.draggingKey == keys[i]
                        val picked = edit.picked?.sameTrip(item) == true
                        // Its handle drags it at once; the rest of its line after a long press.
                        val begin: () -> Unit = {
                            onUse()
                            edit.dragging = true
                            reorder.start(keys[i])
                        }
                        val finish: () -> Unit = {
                            reorder.end()
                            edit.dragging = false
                        }
                        val follow: (PointerInputChange, Offset) -> Unit = { change, amount ->
                            change.consume()
                            reorder.drag(amount.y)
                        }
                        TripRow(
                            item,
                            letter = LETTERS[i],
                            color = AppTheme.colors.wayStops[i % AppTheme.colors.wayStops.size],
                            looked = i == at,
                            picked = picked,
                            clash = i in clashing,
                            legSeconds = line?.legs?.getOrNull(i)?.seconds,
                            arrival = arrivals[i].takeIf { it >= 0 }?.let { now().plusSeconds(it.toLong()) },
                            onClick = {
                                onUse()
                                edit.picked = if (picked) null else item
                                if (!picked) map.lookAt(i)
                            },
                            onEarlier = if (picked && i > 0) ({ onUse(); edit.move(shown, i, i - 1) }) else null,
                            onLater = if (picked && i < shown.lastIndex) ({ onUse(); edit.move(shown, i, i + 1) }) else null,
                            onRemove = if (shown.size > 1) ({ onUse(); onRemove(item) }) else null,
                            handle = Modifier.pointerInput(keys[i]) {
                                detectDragGestures(onDragStart = { begin() }, onDragEnd = finish, onDragCancel = finish, onDrag = follow)
                            },
                            row = Modifier.pointerInput(keys[i]) {
                                detectDragGesturesAfterLongPress(onDragStart = { begin() }, onDragEnd = finish, onDragCancel = finish, onDrag = follow)
                            },
                            // The others slide out of its way.
                            modifier = (if (dragged) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
                                .zIndex(if (dragged) 1f else 0f)
                                .graphicsLayer { translationY = if (dragged) reorder.offset else 0f },
                        )
                    }
                }
                Spacer(Modifier.height(LIST_GAP))
                Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP), verticalAlignment = Alignment.CenterVertically) {
                    ListButton(stringResource(R.string.display_way_add_earlier_short), 269, enabled = onAddEarlier != null, description = stringResource(R.string.display_way_add_earlier)) {
                        onUse()
                        onAddEarlier?.invoke()
                    }
                    ListButton(stringResource(R.string.display_way_add_short), 260, enabled = onAdd != null, description = stringResource(R.string.display_way_add)) {
                        onUse()
                        onAdd?.invoke()
                    }
                    ListButton(
                        stringResource(if (map.suggesting) R.string.display_way_asking else R.string.display_way_suggest),
                        248,
                        enabled = map.located && !map.suggesting && shown.size > 1,
                    ) {
                        onUse()
                        suggestOrder(map, shown, fromNext, now())
                    }
                }
                if (changed) {
                    Spacer(Modifier.height(BUTTON_GAP))
                    Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP), verticalAlignment = Alignment.CenterVertically) {
                        ListButton(stringResource(R.string.display_way_undo), 249) {
                            onUse()
                            edit.preview = null
                            edit.picked = null
                            edit.advice = null
                        }
                        val ids = shown.mapNotNull { it.id }.takeIf { it.size == shown.size }
                        if (onOrder != null && ids != null) {
                            ListButton(stringResource(R.string.display_way_apply), 250, strong = true, enabled = allowed && edit.sent != ids) {
                                onUse()
                                edit.sent = ids
                                onOrder(ids)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The minutes and distance to the stop looked at, the whole way's, and what the order tried saves or costs. */
@Composable
private fun WayHeader(map: RouteMap, line: RouteLine?, at: Int, changed: Boolean, base: Int?) {
    if (!map.located || line == null) {
        Text(
            if (!map.located) stringResource(R.string.passenger_finding_position) else "…",
            fontFamily = DisplayFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = LIST_HEAD_SP,
            color = AppTheme.colors.text,
        )
        return
    }
    val (minutes, meters) = line.to(at)
    Text(
        stringResource(R.string.display_way_to, LETTERS[at], minutes, distanceWords(meters)),
        fontFamily = DisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = LIST_HEAD_SP,
        color = AppTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        val whole = wholeSeconds(line.legs.map { it.seconds })
        Text(stringResource(R.string.display_way_whole, (whole + 30) / 60), fontFamily = DisplayFont, fontSize = LIST_SMALL_SP, color = AppTheme.colors.textMuted)
        if (changed && base != null) {
            val diff = (whole - base) / 60
            Text(
                "   " + (if (diff > 0) "+" else if (diff < 0) "−" else "±") + abs(diff) + " min",
                fontFamily = DigitFont,
                fontWeight = FontWeight.Bold,
                fontSize = LIST_SMALL_SP,
                color = if (diff <= 0) AppTheme.colors.success else AppTheme.colors.danger,
            )
        }
    }
}

/** What Google's travel times said: the order is the best already, or what the best saves; or that it is asking. */
@Composable
private fun adviceText(advice: WayEdit.Advice?, asking: Boolean): String? = when {
    asking -> stringResource(R.string.display_way_asking)
    advice == null -> null
    advice.same -> stringResource(R.string.display_way_best_already)
    advice.unknownBefore -> stringResource(R.string.display_way_suggested_instead, advice.lateAfter)
    else -> stringResource(R.string.display_way_suggested, (advice.savedSeconds + 30) / 60, advice.lateAfter, advice.lateBefore)
}

/**
 * One trip of the way, one small line: the handle to drag it ([handle]), its letter, time, a dot
 * for its kind (green: pick-up, light: drop-off, as in YouDrive), the start of its street, and its
 * leg's minutes over when it is reached. Picked, its arrows move it a step.
 */
@Composable
private fun TripRow(
    item: DisplayItem,
    letter: Char,
    color: Color,
    looked: Boolean,
    picked: Boolean,
    clash: Boolean,
    legSeconds: Int?,
    arrival: LocalTime?,
    onClick: () -> Unit,
    onEarlier: (() -> Unit)?,
    onLater: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    handle: Modifier = Modifier,
    row: Modifier = Modifier,
) {
    val border = when {
        clash -> BorderStroke(ROW_BORDER, AppTheme.colors.danger)
        picked -> BorderStroke(ROW_BORDER, AppTheme.colors.highlight)
        else -> BorderStroke(ROW_BORDER, Color.Transparent)
    }
    val description = stringResource(R.string.display_way_trip, letter.toString(), item.title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .ref(245, centered = true)
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clip(RoundedCornerShape(ROW_CORNER))
            .background(AppTheme.colors.tonal)
            .border(border, RoundedCornerShape(ROW_CORNER))
            .then(row),
    ) {
        // The handle: a finger on it drags the trip up or down.
        Box(
            contentAlignment = Alignment.Center,
            modifier = handle.size(width = HANDLE_WIDTH, height = ROW_HEIGHT),
        ) {
            Icon(painterResource(R.drawable.ic_drag), contentDescription = stringResource(R.string.display_way_drag), tint = AppTheme.colors.textMuted, modifier = Modifier.size(HANDLE_ICON))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ROW_HEIGHT)
                .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(end = 6.dp),
        ) {
            // Its letter large in its own colour, as on the map, on nothing; the time of the one looked
            // at red and beating, as on the map.
            WayLetter(letter, color)
            Spacer(Modifier.width(6.dp))
            WayTime(item.time ?: "–", color, looked, ROW_TIME_SP)
            item.kind?.let { kind ->
                Spacer(Modifier.width(5.dp))
                Box(
                    Modifier
                        .size(KIND_DOT)
                        .clip(CircleShape)
                        .background(if (kind == TripKind.DROP_OFF) AppTheme.colors.text else AppTheme.colors.pickUp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                DisplayItem.streetPart(item.title),
                fontFamily = DisplayFont,
                fontSize = ROW_STREET_SP,
                color = AppTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    legSeconds?.let { "+" + (it + 30) / 60 + " min" } ?: " ",
                    fontFamily = DigitFont,
                    fontSize = ROW_SMALL_SP,
                    color = AppTheme.colors.textMuted,
                )
                if (arrival != null) {
                    val status = TimeStatus.of(TripTimes.minutesUntil(item.time, arrival.hour * 60 + arrival.minute))
                    Text(
                        "≈ " + String.format(Locale.ROOT, "%02d:%02d", arrival.hour, arrival.minute),
                        fontFamily = DigitFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = ROW_SMALL_SP,
                        color = when {
                            item.time == null -> AppTheme.colors.textMuted
                            status.late -> AppTheme.colors.danger
                            status == TimeStatus.ON_TIME -> AppTheme.colors.success
                            else -> AppTheme.colors.warning
                        },
                    )
                }
            }
        }
        if (onEarlier != null || onLater != null) {
            MoveButton(R.string.display_way_earlier, 246, up = true, onEarlier)
            MoveButton(R.string.display_way_later, 247, up = false, onLater)
        }
        if (onRemove != null) RemoveButton(onRemove)
        Spacer(Modifier.width(4.dp))
    }
}

/** An arrow on the picked trip: one place earlier (up) or later (down). */
@Composable
private fun MoveButton(label: Int, ref: Int, up: Boolean, onClick: (() -> Unit)?) {
    val description = stringResource(label)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .refCorner(ref)
            .padding(start = 4.dp)
            .size(MOVE_SIZE)
            .clip(CircleShape)
            .background(if (onClick != null) AppTheme.colors.highlight else AppTheme.colors.card)
            .clickable(enabled = onClick != null, onClickLabel = description, role = Role.Button) { onClick?.invoke() }
            .semantics { contentDescription = description },
    ) {
        Icon(
            painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = if (onClick != null) AppTheme.colors.onInfo else AppTheme.colors.textMuted,
            modifier = Modifier.size(MOVE_ICON).rotate(if (up) -90f else 90f),
        )
    }
}

/** × on a trip: off the way on this map (the phone's route keeps it). */
@Composable
private fun RemoveButton(onClick: () -> Unit) {
    val description = stringResource(R.string.display_way_remove)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .refCorner(268)
            .padding(start = 4.dp)
            .size(REMOVE_SIZE)
            .clip(CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = AppTheme.colors.textMuted, modifier = Modifier.size(REMOVE_ICON))
    }
}

/** A word button under the list; [strong] in the highlight colour. */
@Composable
private fun ListButton(text: String, ref: Int, strong: Boolean = false, enabled: Boolean = true, description: String? = null, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .ref(ref, centered = true)
            .heightIn(min = BUTTON_HEIGHT)
            .clip(RoundedCornerShape(BUTTON_CORNER))
            .background(if (strong && enabled) AppTheme.colors.highlight else AppTheme.colors.tonal)
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = BUTTON_SP,
            color = when {
                !enabled -> AppTheme.colors.textMuted
                strong -> AppTheme.colors.onInfo
                else -> AppTheme.colors.text
            },
        )
    }
}

/** A key for each trip of the list, the same while it moves: its number, else what it shows (made unique). */
private fun rowKeys(trips: List<DisplayItem>): List<String> {
    val seen = HashMap<String, Int>()
    return trips.map { t ->
        val base = t.id?.let { "id$it" } ?: "${t.time}|${t.title}|${t.kind}"
        val n = seen.merge(base, 1, Int::plus)!!
        if (n == 1) base else "$base#$n"
    }
}

/** The drop-offs (and their pick-ups) that come before their passenger is picked up. */
private fun clashes(trips: List<OrderPlanner.Trip>): Set<Int> {
    val out = HashSet<Int>()
    trips.forEachIndexed { i, t ->
        if (t.pickUp != false || t.rider == null) return@forEachIndexed
        val pick = trips.indexOfFirst { it.rider == t.rider && it.pickUp == true }
        if (pick > i) {
            out += i
            out += pick
        }
    }
    return out
}

/** The whole way: every leg and the stops between them. */
private fun wholeSeconds(legs: List<Int>): Int = legs.sum() + OrderPlanner.DWELL_SECONDS * (legs.size - 1).coerceAtLeast(0)

/** "5,3 km" / "800 m", the Swedish way. */
private fun distanceWords(meters: Int): String =
    if (meters >= 1000) String.format(Locale.forLanguageTag("sv-SE"), "%.1f km", meters / 1000f) else "$meters m"

private const val LETTERS = "ABCDEFGHIJK"
private const val LIST_IN_MS = 400
private const val LIST_OUT_MS = 200
private const val LIST_GROUND = 0.92f
private val LIST_WIDTH = 320.dp
private val LIST_MAX_HEIGHT = 420.dp
private val LIST_CORNER = 16.dp
private val LIST_PAD = 10.dp
private val LIST_GAP = 8.dp
private val LIST_HEAD_SP = 16.sp
private val LIST_SMALL_SP = 12.sp
private val ROW_GAP = 4.dp
private val ROW_HEIGHT = 40.dp
private val ROW_CORNER = 10.dp
private val ROW_BORDER = 2.dp
private val ROW_TIME_SP = 14.sp
private val ROW_STREET_SP = 14.sp
private val ROW_SMALL_SP = 11.sp
private val HANDLE_WIDTH = 30.dp
private val HANDLE_ICON = 18.dp
private val LETTER_SIZE = 30.dp
private val LETTER_SP = 24.sp
private val KIND_DOT = 8.dp
private val MOVE_SIZE: Dp = 30.dp
private val MOVE_ICON = 18.dp
private val BUTTON_HEIGHT = 36.dp
private val BUTTON_GAP = 6.dp
private val BUTTON_CORNER = 10.dp
private val BUTTON_SP = 14.sp
private val BAR_HEIGHT = 28.dp
/** The time of the trip looked at beats this much, this fast. */
private const val LOOKED_BEAT = 1.18f
private const val LOOKED_BEAT_MS = 450
private val REMOVE_SIZE = 26.dp
private val REMOVE_ICON = 16.dp

/** A way's trip time in its stop's [color]; the one [looked] at red and beating, as on the map. */
@Composable
internal fun WayTime(time: String, color: Color, looked: Boolean, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val beat = if (looked) {
        rememberInfiniteTransition(label = "looked").animateFloat(1f, LOOKED_BEAT, infiniteRepeatable(tween(LOOKED_BEAT_MS), RepeatMode.Reverse), label = "beat")
    } else {
        null
    }
    Text(
        time,
        fontFamily = DigitFont,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        color = if (looked) AppTheme.colors.danger else color,
        maxLines = 1,
        modifier = modifier.graphicsLayer {
            val b = beat?.value ?: 1f
            scaleX = b
            scaleY = b
        },
    )
}

/**
 * A stop's letter, as the map writes it: large and bold in the stop's own [color], on nothing (the
 * row shows through), centred in its box.
 */
@Composable
internal fun WayLetter(letter: Char, color: Color, size: Dp = LETTER_SIZE, fontSize: TextUnit = LETTER_SP) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Text(
            letter.toString(),
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            color = color,
            style = TextStyle(lineHeight = 1.0.em, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)),
        )
    }
}
