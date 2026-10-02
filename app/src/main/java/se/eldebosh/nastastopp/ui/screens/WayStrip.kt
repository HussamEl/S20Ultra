package se.eldebosh.nastastopp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs

/**
 * The order the driver is trying on his map ([preview], null: the phone's), the trip he picked to
 * move ([picked]), the order he sent to the phone ([sent], until it comes back) and what Google's
 * travel times said of the order ([advice]).
 */
@Stable
internal class WayEdit {
    var preview by mutableStateOf<List<DisplayItem>?>(null)
    var picked by mutableStateOf<DisplayItem?>(null)
    var sent by mutableStateOf<List<Long>?>(null)
    var advice by mutableStateOf<Advice?>(null)

    /** The best order is the one shown ([same]), or it saves [savedSeconds] and leaves [lateAfter] minutes late (was [lateBefore]). */
    data class Advice(val same: Boolean, val savedSeconds: Int = 0, val lateBefore: Int = 0, val lateAfter: Int = 0)

    fun clear() {
        preview = null
        picked = null
        sent = null
        advice = null
    }

    /**
     * The phone's order changed under the one tried: it is dropped when it now is the phone's (sent
     * and taken) or no longer has the same trips.
     */
    fun settle(window: List<DisplayItem>) {
        val p = preview ?: return
        val sameTrips = p.size == window.size && p.all { t -> window.any { it.sameTrip(t) } }
        val taken = p.indices.all { p[it].sameTrip(window[it]) }
        if (!sameTrips || taken) {
            preview = null
            sent = null
        }
    }

    /** Moves the trip at [index] of [shown] one place earlier ([step] -1) or later (1). */
    fun move(shown: List<DisplayItem>, index: Int, step: Int) {
        val to = index + step
        if (index !in shown.indices || to !in shown.indices) return
        preview = shown.toMutableList().apply { add(to, removeAt(index)) }
        advice = null
    }
}

/** The same trip in another snapshot: its number, or what it shows. */
internal fun DisplayItem.sameTrip(other: DisplayItem): Boolean =
    if (id != null && other.id != null) id == other.id else trip == other.trip

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
 * The driver's way on his map (the tablet), at its bottom: its trips lettered as on the map (A, B,
 * C…; the one he looks at red), each with its time, the start of its street and its leg's minutes;
 * when the way starts at the next stop, when each is reached, in the clock's status colours (a
 * stop takes [OrderPlanner.DWELL_SECONDS]). Above them: the minutes and distance to the one looked
 * at, the whole way's, and how much the order tried saves or costs; what keeps the map or the way
 * from coming ([note], 233).
 *
 * A tap picks a trip (the map turns to it) and gives it arrows (246, 247) to move it earlier or
 * later: the map shows the new order at once with its times. "Suggest" (248) asks Google for the
 * travel times between them all and puts the best order up ([OrderPlanner]); "Undo" (249) goes
 * back; "Use" (250) sends the order to the phone ([onOrder]). An order with a passenger's
 * drop-off before their pick-up cannot be used.
 */
@Composable
internal fun WayStrip(
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
    onUse: () -> Unit,
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
            edit.advice = WayEdit.Advice(false, s.current.seconds - s.best.seconds, s.current.lateMinutes, s.best.lateMinutes)
        }
    }
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(STRIP_IN_MS)), exit = fadeOut(tween(STRIP_OUT_MS))) {
        Column(
            Modifier
                .padding(start = STRIP_EDGE, end = STRIP_RIGHT_ROOM, bottom = STRIP_EDGE)
                .clip(RoundedCornerShape(STRIP_CORNER))
                .background(AppTheme.colors.card.copy(alpha = STRIP_GROUND))
                .padding(STRIP_PAD),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.ref(251)) {
                Column(Modifier.weight(1f)) {
                    WayHeader(map, line, at, changed, base)
                    val words = adviceText(edit.advice, map.suggesting) ?: if (!allowed) stringResource(R.string.display_way_pickup_first) else null
                    if (words != null) {
                        Text(
                            words,
                            fontFamily = DisplayFont,
                            fontSize = STRIP_SMALL_SP,
                            color = if (!allowed) AppTheme.colors.danger else AppTheme.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (note != null) {
                        Text(
                            note,
                            fontFamily = DisplayFont,
                            fontSize = STRIP_SMALL_SP,
                            color = AppTheme.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.ref(233),
                        )
                    }
                }
                StripButton(
                    stringResource(if (map.suggesting) R.string.display_way_asking else R.string.display_way_suggest),
                    248,
                    enabled = map.located && !map.suggesting && shown.size > 1,
                ) {
                    onUse()
                    // Lateness counts only from the next stop: a way that starts later skips the trips before it.
                    map.suggest(shown.map { it.mapStop }, if (fromNext) trips else trips.map { it.copy(booked = null) }, now().toSecondOfDay())
                }
                if (changed) {
                    StripButton(stringResource(R.string.display_way_undo), 249) {
                        onUse()
                        edit.preview = null
                        edit.picked = null
                        edit.advice = null
                    }
                    val ids = shown.mapNotNull { it.id }.takeIf { it.size == shown.size }
                    if (onOrder != null && ids != null) {
                        StripButton(stringResource(R.string.display_way_apply), 250, strong = true, enabled = allowed && edit.sent != ids) {
                            onUse()
                            edit.sent = ids
                            onOrder(ids)
                        }
                    }
                }
            }
            Spacer(Modifier.height(STRIP_GAP))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(STRIP_GAP)) {
                var t = 0
                shown.forEachIndexed { i, item ->
                    val leg = line?.legs?.getOrNull(i)?.seconds
                    if (leg != null) t += leg
                    val arrival = if (leg != null && fromNext) t else null
                    if (leg != null) t += OrderPlanner.DWELL_SECONDS
                    val picked = edit.picked?.sameTrip(item) == true
                    TripChip(
                        item,
                        letter = LETTERS[i],
                        looked = i == at,
                        picked = picked,
                        clash = i in clashing,
                        legSeconds = leg,
                        arrival = arrival?.let { now().plusSeconds(it.toLong()) },
                        onClick = {
                            onUse()
                            edit.picked = if (picked) null else item
                            if (!picked) map.lookAt(i)
                        },
                        onEarlier = if (picked && i > 0) ({ onUse(); edit.move(shown, i, -1) }) else null,
                        onLater = if (picked && i < shown.lastIndex) ({ onUse(); edit.move(shown, i, 1) }) else null,
                    )
                }
            }
        }
    }
}

/** The minutes and distance to the stop looked at, the whole way's, and what the order tried saves or costs. */
@Composable
private fun WayHeader(map: RouteMap, line: RouteLine?, at: Int, changed: Boolean, base: Int?) {
    val text = when {
        !map.located -> stringResource(R.string.passenger_finding_position)
        line == null -> "…"
        else -> {
            val (minutes, meters) = line.to(at)
            listOf(
                stringResource(R.string.display_way_to, LETTERS[at], minutes, distanceWords(meters)),
                stringResource(R.string.display_way_whole, (wholeSeconds(line.legs.map { it.seconds }) + 30) / 60),
            ).joinToString("   ·   ")
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = STRIP_HEAD_SP, color = AppTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val whole = line?.let { wholeSeconds(it.legs.map { l -> l.seconds }) }
        if (changed && whole != null && base != null) {
            val diff = (whole - base) / 60
            Text(
                "   " + (if (diff > 0) "+" else if (diff < 0) "−" else "±") + abs(diff) + " min",
                fontFamily = DigitFont,
                fontWeight = FontWeight.Bold,
                fontSize = STRIP_HEAD_SP,
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
    else -> stringResource(R.string.display_way_suggested, (advice.savedSeconds + 30) / 60, advice.lateAfter, advice.lateBefore)
}

/**
 * One trip of the way: its letter, time, a dot for its kind (green: pick-up, light: drop-off, as
 * in YouDrive), the start of its street, its leg and when it is reached.
 */
@Composable
private fun TripChip(
    item: DisplayItem,
    letter: Char,
    looked: Boolean,
    picked: Boolean,
    clash: Boolean,
    legSeconds: Int?,
    arrival: LocalTime?,
    onClick: () -> Unit,
    onEarlier: (() -> Unit)?,
    onLater: (() -> Unit)?,
) {
    val border = when {
        clash -> BorderStroke(CHIP_BORDER, AppTheme.colors.danger)
        picked -> BorderStroke(CHIP_BORDER, AppTheme.colors.highlight)
        else -> BorderStroke(CHIP_BORDER, Color.Transparent)
    }
    val description = stringResource(R.string.display_way_trip, letter.toString(), item.title)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .ref(245, centered = true)
                .width(CHIP_WIDTH)
                .clip(RoundedCornerShape(CHIP_CORNER))
                .background(AppTheme.colors.tonal)
                .border(border, RoundedCornerShape(CHIP_CORNER))
                .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(CHIP_PAD),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(LETTER_SIZE)
                        .clip(CircleShape)
                        .background(if (looked) AppTheme.colors.danger else AppTheme.colors.info),
                ) {
                    Text(letter.toString(), fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = LETTER_SP, color = if (looked) AppTheme.colors.onStatus else AppTheme.colors.onInfo)
                }
                Spacer(Modifier.width(8.dp))
                Text(item.time ?: "–", fontFamily = DigitFont, fontWeight = FontWeight.Bold, fontSize = CHIP_TIME_SP, color = AppTheme.colors.text)
                item.kind?.let { kind ->
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(KIND_DOT)
                            .clip(CircleShape)
                            .background(if (kind == TripKind.DROP_OFF) AppTheme.colors.text else AppTheme.colors.pickUp),
                    )
                }
            }
            Text(
                DisplayItem.streetPart(item.title),
                fontFamily = DisplayFont,
                fontSize = CHIP_STREET_SP,
                color = AppTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    legSeconds?.let { "+" + (it + 30) / 60 + " min" } ?: " ",
                    fontFamily = DigitFont,
                    fontSize = CHIP_SMALL_SP,
                    color = AppTheme.colors.textMuted,
                )
                if (arrival != null) {
                    val status = TimeStatus.of(TripTimes.minutesUntil(item.time, arrival.hour * 60 + arrival.minute))
                    Spacer(Modifier.weight(1f))
                    Text(
                        "≈ " + String.format(Locale.ROOT, "%02d:%02d", arrival.hour, arrival.minute),
                        fontFamily = DigitFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = CHIP_SMALL_SP,
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
            Row(horizontalArrangement = Arrangement.spacedBy(STRIP_GAP), modifier = Modifier.padding(top = 6.dp)) {
                MoveButton(R.string.display_way_earlier, 246, flip = true, onEarlier)
                MoveButton(R.string.display_way_later, 247, flip = false, onLater)
            }
        }
    }
}

/** An arrow under the picked trip: one place earlier or later. */
@Composable
private fun MoveButton(label: Int, ref: Int, flip: Boolean, onClick: (() -> Unit)?) {
    val description = stringResource(label)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .refCorner(ref)
            .size(MOVE_SIZE)
            .clip(CircleShape)
            .background(if (onClick != null) AppTheme.colors.highlight else AppTheme.colors.tonal)
            .clickable(enabled = onClick != null, onClickLabel = description, role = Role.Button) { onClick?.invoke() }
            .semantics { contentDescription = description },
    ) {
        Icon(
            painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = if (onClick != null) AppTheme.colors.onInfo else AppTheme.colors.textMuted,
            modifier = Modifier.size(MOVE_ICON).rotate(if (flip) 180f else 0f),
        )
    }
}

/** A word button on the strip; [strong] in the highlight colour. */
@Composable
private fun StripButton(text: String, ref: Int, strong: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = STRIP_GAP)
            .ref(ref, centered = true)
            .clip(RoundedCornerShape(BUTTON_CORNER))
            .background(if (strong && enabled) AppTheme.colors.highlight else AppTheme.colors.tonal)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = STRIP_BUTTON_SP,
            color = when {
                !enabled -> AppTheme.colors.textMuted
                strong -> AppTheme.colors.onInfo
                else -> AppTheme.colors.text
            },
        )
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
private const val STRIP_IN_MS = 400
private const val STRIP_OUT_MS = 200
private const val STRIP_GROUND = 0.9f
private val STRIP_EDGE = 20.dp
private val STRIP_RIGHT_ROOM = 104.dp
private val STRIP_CORNER = 20.dp
private val STRIP_PAD = 14.dp
private val STRIP_GAP = 10.dp
private val STRIP_HEAD_SP = 22.sp
private val STRIP_SMALL_SP = 15.sp
private val STRIP_BUTTON_SP = 18.sp
private val BUTTON_CORNER = 14.dp
private val CHIP_WIDTH = 158.dp
private val CHIP_CORNER = 14.dp
private val CHIP_PAD = 10.dp
private val CHIP_BORDER = 3.dp
private val CHIP_TIME_SP = 20.sp
private val CHIP_STREET_SP = 17.sp
private val CHIP_SMALL_SP = 14.sp
private val LETTER_SIZE = 30.dp
private val LETTER_SP = 18.sp
private val KIND_DOT = 10.dp
private val MOVE_SIZE = 44.dp
private val MOVE_ICON = 26.dp
