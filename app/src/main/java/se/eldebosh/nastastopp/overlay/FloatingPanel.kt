package se.eldebosh.nastastopp.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.display.DisplayItem
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.core.display.TimeStatus
import se.eldebosh.nastastopp.core.geo.GeoLogic
import se.eldebosh.nastastopp.core.geo.StreetInfo
import se.eldebosh.nastastopp.core.link.LinkMessage
import se.eldebosh.nastastopp.core.link.LinkMessage.Remote.Action
import se.eldebosh.nastastopp.core.nav.OrderPlanner
import se.eldebosh.nastastopp.core.parse.TripKind
import se.eldebosh.nastastopp.core.parse.TripTimes
import se.eldebosh.nastastopp.core.route.Announcements
import se.eldebosh.nastastopp.ui.DoneMarks
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.screens.PersonGlyph
import se.eldebosh.nastastopp.ui.screens.ThenLabel
import se.eldebosh.nastastopp.ui.screens.TimeFace
import se.eldebosh.nastastopp.ui.screens.WayLetter
import se.eldebosh.nastastopp.ui.screens.WayTime
import se.eldebosh.nastastopp.ui.screens.plannerTrips
import se.eldebosh.nastastopp.ui.screens.statusColor
import se.eldebosh.nastastopp.ui.theme.AppTheme
import se.eldebosh.nastastopp.ui.theme.DigitFont
import se.eldebosh.nastastopp.ui.theme.DisplayFont
import se.eldebosh.nastastopp.ui.theme.DisplayTheme
import java.time.LocalTime

/** What the floating panel does on the driver's taps, beyond its [PanelSource]. */
internal interface PanelActions {
    fun minimize()

    fun close()

    fun openApp()

    fun toggleSayStreet()

    /** A short message (a button with nothing to do). */
    fun toast(text: Int)

    /** Where the panel's bar is in its window (a finger on it moves the window). */
    fun barAt(bounds: Rect)
}

/**
 * The floating panel, in the passenger display's look (black, its fonts and its time face) and in
 * a window the driver moves by its bar and sizes from any edge or corner ([OverlayManager]):
 *
 *     ● Galaxy Tab   13:14   3/11              ⌂ – ×      the link to the display, the clock (tap:
 *     (45) Drottninggatan · Centrum               🔊       says it), trip n/total; the speed, the street
 *     👤 07:36 Karlstad                                    the trip shown: its figure, its time in the
 *        Västra Torggatan 12                               clock's style, town and area; its street and
 *        Anna Testsson                                     number (tap: say it; long press: the display's
 *     › Hamngatan 7 · Storgatan 14 · …                    map for it); the passenger; the coming trips
 *     [⏮ Back] [══════ ⏭ Next ══════]                     (scroll, tap: shown above and on the display)
 *     the display's map, while it is open: its buttons and its list of trips, run from here
 *
 * While the passenger display's map is open ([PanelSource.mapView]), the panel runs it as if the
 * driver's hand were on it: Google's satellite picture, the car, the stop, the whole way, Google
 * Earth and street photos, closing it; and the way's trips, in order, moved with their arrows,
 * taken off, added, Google's best order, back, and used ([LinkMessage.Remote]). The display draws
 * each change at once and says back what its map shows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FloatingPanel(source: PanelSource, actions: PanelActions, sayStreetOn: Boolean, fill: Boolean = false, time: () -> LocalTime = { LocalTime.now() }) {
    val snapshot by source.snapshot.collectAsState()
    val street by (source.street?.state ?: NO_STREET).collectAsState()
    val mapView by (source.mapView ?: NO_MAP).collectAsState()
    val display by (source.displayName ?: NO_NAME).collectAsState()
    val now by rememberTick(time)
    val live = snapshot?.takeIf { it.active && it.current != null } ?: return
    val current = live.current ?: return
    DisplayTheme(dark = true) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val colors = AppTheme.colors
            val shape = RoundedCornerShape(PANEL_CORNER)
            // The trips in order, done ones first; the one shown at the top ([shownAt]), the next by default.
            val all = live.earlier + listOf(current) + live.upcoming
            val home = live.earlier.size
            var shownId by remember { mutableStateOf<Long?>(null) }
            var shownAt by remember { mutableStateOf<DisplayItem?>(null) }
            val shown = shownAt?.let { s -> all.firstOrNull { it.sameAs(s) } } ?: current
            val away = !shown.sameAs(current)
            // Back to the next stop a while after the last look elsewhere.
            LaunchedEffect(shown, away) {
                if (!away) return@LaunchedEffect
                delay(BROWSE_RETURN_MS)
                shownAt = null
            }
            LaunchedEffect(current.id, current.title) { shownAt = null }
            val show = { item: DisplayItem? ->
                shownAt = item
                // The display shows the same trip (or goes back to the next stop).
                source.remote(LinkMessage.Remote(Action.SHOW_TRIP, id = item?.takeUnless { it.sameAs(current) }?.id))
                shownId = item?.id
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    // Sized by the driver: as big as its window (larger than what it shows if he wants); else as tall as its content.
                    .then(if (fill) Modifier.fillMaxHeight() else Modifier)
                    .semantics { testTagsAsResourceId = true }
                    .shadow(PANEL_SHADOW, shape)
                    .clip(shape)
                    .background(colors.background.copy(alpha = PANEL_GROUND))
                    .border(1.dp, colors.cardBorder, shape)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                TopBar(live, display, now, away, onHome = { show(null) }, source = source, actions = actions)
                val info = street
                if (source.street != null) StreetRow(info, source, sayStreetOn, actions)
                Hero(shown, source, isNext = !away, now = now, map = mapView, actions = actions)
                ComingRow(all, home, shown, now, onPick = { show(it) }, onLong = { item -> openMap(item, item.sameAs(current), mapView, source, actions) }, source = source)
                Moves(live, source, actions)
                val view = mapView
                AnimatedVisibility(view?.open == true, enter = fadeIn(), exit = fadeOut()) {
                    if (view != null) MapPart(view, all, source)
                }
            }
        }
    }
}

private val NO_STREET = MutableStateFlow<StreetInfo?>(null)
private val NO_MAP = MutableStateFlow<LinkMessage.MapView?>(null)
private val NO_NAME = MutableStateFlow<String?>(null)

/** The same trip on another snapshot: its number, else what it shows. */
private fun DisplayItem.sameAs(other: DisplayItem): Boolean =
    if (id != null && other.id != null) id == other.id else trip == other.trip

/** A long press on a trip: the display's map for it (its long press there), when the display has a map. */
private fun openMap(item: DisplayItem, isNext: Boolean, map: LinkMessage.MapView?, source: PanelSource, actions: PanelActions) {
    // A tablet's own panel sits over its display: nothing to open from here.
    if (source.mapView == null) return
    if (map?.hasMap != true) {
        actions.toast(R.string.panel_no_display_map)
        return
    }
    source.remote(LinkMessage.Remote(Action.OPEN_MAP, id = if (isNext) null else item.id))
}

@Composable
private fun rememberTick(time: () -> LocalTime): State<LocalTime> {
    val read by rememberUpdatedState(time)
    val now = remember { mutableStateOf(read()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.value = read()
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }
    return now
}

/**
 * The bar (a finger on it moves the window): the link to the display (a dot and its name), the
 * clock (8; a tap says the time, on the display when one is linked), trip n/total (9; a tap opens the app), Home while
 * another trip is shown (278), minimise (6) and close (7).
 */
@Composable
private fun TopBar(live: DisplaySnapshot, display: String?, now: LocalTime, away: Boolean, onHome: () -> Unit, source: PanelSource, actions: PanelActions) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BAR_HEIGHT)
            .onGloballyPositioned { actions.barAt(it.boundsInWindow()) },
    ) {
        if (source.mapView != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.ref(279)) {
                Box(Modifier.size(8.dp).background(if (display != null) colors.success else colors.outline, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(
                    display?.take(NAME_CHARS) ?: stringResource(R.string.panel_no_display),
                    fontFamily = DisplayFont,
                    fontSize = 13.sp,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(NAME_WIDTH),
                )
            }
        }
        val clock = stringResource(R.string.panel_say_time)
        Text(
            String.format(java.util.Locale.ROOT, "%02d:%02d", now.hour, now.minute),
            fontFamily = DigitFont,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = colors.text,
            modifier = Modifier
                .ref(8)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = clock, role = Role.Button) { source.sayTime() }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            "${live.completed + 1}/${live.completed + live.remaining}",
            fontFamily = DigitFont,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = colors.textMuted,
            modifier = Modifier
                .ref(9)
                .clip(RoundedCornerShape(50))
                .background(colors.tonal)
                .clickable(onClickLabel = stringResource(R.string.overlay_open_app_desc), role = Role.Button, onClick = actions::openApp)
                .padding(horizontal = 8.dp, vertical = 1.dp),
        )
        Spacer(Modifier.weight(1f))
        if (away) {
            val beat = rememberInfiniteTransition(label = "home")
            val scale by beat.animateFloat(1f, HOME_BEAT, infiniteRepeatable(tween(HOME_BEAT_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beat")
            RoundControl(R.drawable.ic_home, R.string.display_home, 278, highlight = true, scale = { scale }, onClick = onHome)
            Spacer(Modifier.width(6.dp))
        }
        RoundControl(R.drawable.ic_remove, R.string.overlay_minimize_desc, 6, onClick = actions::minimize)
        Spacer(Modifier.width(6.dp))
        RoundControl(R.drawable.ic_close, R.string.overlay_close_desc, 7, onClick = actions::close)
    }
}

@Composable
private fun RoundControl(icon: Int, label: Int, ref: Int, highlight: Boolean = false, scale: () -> Float = { 1f }, enabled: Boolean = true, size: Dp = CONTROL, turn: Float = 0f, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val description = stringResource(label)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .refCorner(ref)
            .graphicsLayer {
                scaleX = scale()
                scaleY = scale()
            }
            .size(size)
            .clip(CircleShape)
            .background(if (highlight) colors.highlight else colors.tonal)
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = when {
                highlight -> colors.onInfo
                enabled -> colors.text
                else -> colors.textMuted.copy(alpha = 0.5f)
            },
            modifier = Modifier.size(size * 0.55f).rotate(turn),
        )
    }
}

/** The speed in its own circle (15) and the street the vehicle is on (2: tap says it), its area (3), and the street's speaker (4). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreetRow(info: StreetInfo?, source: PanelSource, sayStreetOn: Boolean, actions: PanelActions) {
    val colors = AppTheme.colors
    var speed by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(source) {
        while (true) {
            speed = source.street?.speedNow()
            delay(1_000L)
        }
    }
    val trip = source.trip()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        val speedDescription = stringResource(R.string.overlay_speed_desc)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .ref(15)
                .size(SPEED)
                .clip(CircleShape)
                .background(colors.tonal)
                .border(3.dp, colors.highlight, CircleShape)
                .semantics { contentDescription = speedDescription },
        ) {
            BasicText(
                speed?.toString() ?: "–",
                style = TextStyle(fontFamily = DigitFont, fontWeight = FontWeight.Bold, color = colors.text, textAlign = TextAlign.Center),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 24.sp),
                modifier = Modifier.padding(horizontal = 5.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        val streetDescription = stringResource(R.string.overlay_street_desc)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .ref(2)
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.tonal)
                .combinedClickable(onClickLabel = streetDescription, role = Role.Button, onLongClick = source::repeat, onClick = source::sayStreet)
                .padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    info?.street ?: info?.area ?: trip?.area.orEmpty(),
                    style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, color = colors.text),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 22.sp),
                )
                val area = info?.area.takeIf { info?.street != null }
                if (!area.isNullOrEmpty()) {
                    Text(area, fontFamily = DisplayFont, fontSize = 12.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.ref(3))
                }
            }
            RoundControl(
                if (sayStreetOn) R.drawable.ic_speaker else R.drawable.ic_speaker_off,
                if (sayStreetOn) R.string.overlay_say_street_on else R.string.overlay_say_street_off,
                4,
                size = 34.dp,
                onClick = actions::toggleSayStreet,
            )
        }
    }
}

/**
 * The trip shown (the next stop unless another is looked at), as on the display: over it its
 * passenger's figure, its time in the clock's style (230's look; 10) and its town and area (19);
 * its street and number large (13: a tap says them; a long press opens the display's map for
 * it); the passenger's first + last name (18, the phone only) and how it stands (11).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Hero(item: DisplayItem, source: PanelSource, isNext: Boolean, now: LocalTime, map: LinkMessage.MapView?, actions: PanelActions) {
    val colors = AppTheme.colors
    val until = TripTimes.minutesUntil(item.time, now.hour * 60 + now.minute)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // The time as large as the window's width allows, up to [HERO_TIME].
    val timeSize = minOf(HERO_TIME.value, (maxWidth.value * HERO_TIME_PER_DP)).coerceAtLeast(HERO_TIME_MIN.value).sp
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.card != null || item.lastName != null) {
                PersonGlyph(colors.highlight, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
            }
            if (item.doneInYouDrive || item.doneHere) {
                DoneMarks(youDrive = item.doneInYouDrive, here = item.doneHere, size = 8.dp)
                Spacer(Modifier.width(6.dp))
            }
            if (item.time != null) TimeFace(item.time, timeSize, Modifier.ref(10), breathing = false)
            item.subtitle?.let {
                Spacer(Modifier.width(10.dp))
                Text(it, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.ref(19))
            }
        }
        val say = stringResource(R.string.overlay_stop_street_desc)
        val mapLabel = stringResource(R.string.panel_open_display_map)
        BasicText(
            item.title,
            style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, color = colors.text, textAlign = TextAlign.Center, lineHeight = 1.0.em),
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 40.sp, stepSize = 1.sp),
            modifier = Modifier
                .ref(13)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClickLabel = say,
                    role = Role.Button,
                    onLongClickLabel = mapLabel,
                    onLongClick = { openMap(item, isNext, map, source, actions) },
                    onClick = {
                        if (isNext) source.sayStop() else source.say(Announcements.at(item.time, GeoLogic.fullSpokenName(item.said ?: item.title, item.subtitle, null), coming = !(item.doneHere || item.doneInYouDrive)))
                    },
                )
                .padding(vertical = 2.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            source.fullName(item)?.let { name ->
                Text(name, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.ref(18).weight(1f, fill = false))
                Spacer(Modifier.width(10.dp))
            }
            if (until != null) {
                val status = TimeStatus.of(until)
                Box(Modifier.size(STATUS_BAR_W, STATUS_BAR_H).clip(CircleShape).background(statusColor(status)))
                Spacer(Modifier.width(5.dp))
                Text(
                    se.eldebosh.nastastopp.util.TimeLabels.until(androidx.compose.ui.platform.LocalContext.current, until),
                    fontFamily = DigitFont,
                    fontSize = 13.sp,
                    color = statusColor(status),
                    modifier = Modifier.ref(11),
                )
            }
        }
    }
    }
}

/**
 * The trips in order at the bottom, as the display's bottom line: each its street and number, its
 * time under it larger in the highlight colour, a bar for how it stands; a swipe scrolls them, and
 * the one that settles in the middle, or a tapped one, is shown above and on the display (277); a
 * long press opens the display's map for it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComingRow(all: List<DisplayItem>, home: Int, shown: DisplayItem, now: LocalTime, onPick: (DisplayItem?) -> Unit, onLong: (DisplayItem) -> Unit, source: PanelSource) {
    val colors = AppTheme.colors
    val list = rememberLazyListState(initialFirstVisibleItemIndex = home.coerceAtMost(all.lastIndex))
    val snap = rememberSnapFlingBehavior(list, SnapPosition.Start)
    // A swipe settles on a trip: shown above (the next stop's is the one before the first shown).
    var swiped by remember { mutableStateOf(false) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { moving ->
            if (moving) {
                swiped = true
            } else if (swiped) {
                swiped = false
                val first = list.firstVisibleItemIndex
                all.getOrNull(first)?.let { onPick(it) }
            }
        }
    }
    LazyRow(
        state = list,
        flingBehavior = snap,
        contentPadding = PaddingValues(end = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        itemsIndexed(all, key = { i, t -> t.id ?: i.toLong() }) { i, t ->
            val lit = t.sameAs(shown)
            val description = stringResource(R.string.display_say_trip)
            Column(
                Modifier
                    .ref(277)
                    .width(COMING_WIDTH)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (lit) colors.tonalHigh else Color.Transparent)
                    .combinedClickable(onClickLabel = description, role = Role.Button, onLongClick = { onLong(t) }, onClick = { onPick(t) })
                    .graphicsLayer { alpha = if (i < home) DONE_ALPHA else 1f }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                if (i == home) ThenLabel(text = stringResource(R.string.passenger_next_stop), color = colors.highlight, size = 9.sp)
                Text(t.title, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                if (t.time != null) Text(t.time, fontFamily = DigitFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.highlight, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(10.dp)) {
                    val until = TripTimes.minutesUntil(t.time, now.hour * 60 + now.minute)
                    if (until != null && i >= home) Box(Modifier.size(STATUS_BAR_W, STATUS_BAR_H).clip(CircleShape).background(statusColor(TimeStatus.of(until))))
                    if (t.doneInYouDrive || t.doneHere) {
                        Spacer(Modifier.width(4.dp))
                        DoneMarks(youDrive = t.doneInYouDrive, here = t.doneHere, size = 7.dp)
                    }
                }
            }
        }
    }
}

/** Back (1: undoes the last Next) and Next (5: a long press repeats the announcement), as before. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Moves(live: DisplaySnapshot, source: PanelSource, actions: PanelActions) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        val back = stringResource(R.string.overlay_back)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .ref(1)
                .height(MOVE_HEIGHT)
                .clip(RoundedCornerShape(MOVE_HEIGHT / 2))
                .background(colors.tonal)
                .clickable(onClickLabel = back, role = Role.Button) { if (!source.back()) actions.toast(R.string.overlay_no_previous) }
                .semantics(mergeDescendants = true) { contentDescription = back }
                .padding(horizontal = 14.dp),
        ) {
            val dim = if (live.completed == 0) 0.35f else 1f
            Icon(painterResource(R.drawable.ic_previous), contentDescription = null, tint = colors.text.copy(alpha = dim), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(back, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.text.copy(alpha = dim))
        }
        Spacer(Modifier.width(8.dp))
        val next = stringResource(R.string.overlay_next)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .ref(5)
                .weight(1f)
                .height(MOVE_HEIGHT)
                .clip(RoundedCornerShape(MOVE_HEIGHT / 2))
                .background(colors.highlight)
                .combinedClickable(onClickLabel = next, role = Role.Button, onLongClick = source::repeat, onClick = source::next)
                .semantics(mergeDescendants = true) { contentDescription = next },
        ) {
            Icon(painterResource(R.drawable.ic_next), contentDescription = null, tint = colors.onInfo, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(next, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.onInfo)
        }
    }
}

/**
 * The display's map while it is open, run from here: its buttons (280–286, as 270, 239–244 there)
 * and its list of trips in order (287–290: each lettered in its colour, its time, street, last
 * name and leg's minutes, moved with ↑ ↓ or taken off with ×; a tap looks at it), "+ Earlier",
 * "+ Trip", "Suggest" (291–293), and, for an order tried, "Undo" and "Use" (294, 295). Each sends
 * the control to the display, which draws it at once and says back what its map shows.
 */
@Composable
private fun MapPart(view: LinkMessage.MapView, all: List<DisplayItem>, source: PanelSource) {
    val colors = AppTheme.colors
    fun send(action: Action, id: Long? = null, ids: List<Long>? = null, on: Boolean? = null) = source.remote(LinkMessage.Remote(action, id, ids, on))
    Column(
        Modifier
            .ref(296)
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.panel_map_title), fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.text, modifier = Modifier.weight(1f))
            RoundControl(R.drawable.ic_close, R.string.display_map_close, 286, highlight = true, size = 34.dp) { send(Action.CLOSE_MAP) }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundControl(R.drawable.ic_layers, if (view.satellite) R.string.display_map_plain else R.string.display_map_satellite, 280, highlight = view.satellite, size = MAP_BUTTON) { send(Action.SATELLITE, on = !view.satellite) }
            RoundControl(R.drawable.ic_my_location, R.string.display_map_car, 281, size = MAP_BUTTON) { send(Action.TO_CAR) }
            RoundControl(R.drawable.ic_pin, R.string.display_map_stop, 282, size = MAP_BUTTON) { send(Action.TO_STOP) }
            RoundControl(R.drawable.ic_zoom_out_map, R.string.display_map_whole, 283, size = MAP_BUTTON) { send(Action.WHOLE) }
            RoundControl(R.drawable.ic_flight, R.string.display_map_tour, 284, size = MAP_BUTTON) { send(Action.EARTH) }
            RoundControl(R.drawable.ic_street, R.string.display_map_street, 285, size = MAP_BUTTON) { send(Action.STREET_PHOTOS) }
        }
        Spacer(Modifier.height(6.dp))
        val way = view.ids.mapNotNull { id -> all.firstOrNull { it.id == id } }
        val allowed = way.size < 2 || OrderPlanner.allowed(way.indices.toList(), plannerTrips(way))
        way.forEachIndexed { i, t ->
            WayRow(
                i,
                t,
                looked = i == view.at,
                minutes = view.minutes.getOrNull(i),
                first = i == 0,
                last = i == way.lastIndex,
                onLook = { send(Action.LOOK_AT, id = t.id) },
                onMove = { to ->
                    val ids = view.ids.toMutableList().apply { add(to, removeAt(i)) }
                    send(Action.TRY_ORDER, ids = ids)
                },
                onRemove = { send(Action.REMOVE, id = t.id) },
                canRemove = way.size > 1,
                name = source.fullName(t),
            )
        }
        if (!allowed) Text(stringResource(R.string.display_way_pickup_first), fontFamily = DisplayFont, fontSize = 12.sp, color = colors.danger)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton(stringResource(R.string.display_way_add_earlier_short), 291, enabled = view.canAddEarlier) { send(Action.ADD_EARLIER) }
            PanelButton(stringResource(R.string.display_way_add_short), 292, enabled = view.canAdd) { send(Action.ADD) }
            PanelButton(stringResource(if (view.suggesting) R.string.display_way_asking else R.string.display_way_suggest), 293, enabled = view.located && !view.suggesting && way.size > 1) { send(Action.SUGGEST) }
        }
        if (view.changed) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelButton(stringResource(R.string.display_way_undo), 294) { send(Action.UNDO) }
                PanelButton(stringResource(R.string.display_way_apply), 295, strong = true, enabled = allowed) { send(Action.APPLY) }
            }
        }
    }
}

/** A trip of the display's way: its letter large and its time in its colour (the time of the one looked at red and beating), street, name, leg's minutes; ↑ ↓ ×. */
@Composable
private fun WayRow(
    i: Int,
    t: DisplayItem,
    looked: Boolean,
    minutes: Int?,
    first: Boolean,
    last: Boolean,
    onLook: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    name: String?,
) {
    val colors = AppTheme.colors
    val stop = colors.wayStops[i % colors.wayStops.size]
    val description = "${LETTERS[i]}: ${t.title}"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .ref(287)
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (looked) colors.tonalHigh else colors.tonal)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onLook)
            .semantics { contentDescription = description }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        WayLetter(LETTERS[i], stop, size = 28.dp, fontSize = 22.sp)
        Spacer(Modifier.width(4.dp))
        WayTime(t.time.orEmpty(), stop, looked, 15.sp, Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val more = listOfNotNull(name ?: t.lastName, minutes?.let { "$it min" }).joinToString(" · ")
            if (more.isNotEmpty()) Text(more, fontFamily = DisplayFont, fontSize = 12.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundControl(R.drawable.ic_chevron, R.string.display_way_earlier, 288, enabled = !first, size = 30.dp, turn = -90f) { onMove(i - 1) }
        Spacer(Modifier.width(3.dp))
        RoundControl(R.drawable.ic_chevron, R.string.display_way_later, 289, enabled = !last, size = 30.dp, turn = 90f) { onMove(i + 1) }
        Spacer(Modifier.width(3.dp))
        RoundControl(R.drawable.ic_close, R.string.display_way_remove, 290, enabled = canRemove, size = 30.dp, onClick = onRemove)
    }
}

@Composable
private fun PanelButton(text: String, ref: Int, strong: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Text(
        text,
        fontFamily = DisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = when {
            !enabled -> colors.textMuted.copy(alpha = 0.5f)
            strong -> colors.onInfo
            else -> colors.text
        },
        maxLines = 1,
        modifier = Modifier
            .ref(ref)
            .clip(RoundedCornerShape(50))
            .background(if (strong && enabled) colors.highlight else colors.tonal)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

private const val LETTERS = "ABCDEFGHIJK"
private const val NAME_CHARS = 10
private val NAME_WIDTH = 90.dp
private val PANEL_CORNER = 22.dp
private val PANEL_SHADOW = 10.dp
private const val PANEL_GROUND = 0.94f
private val BAR_HEIGHT = 40.dp
private val CONTROL = 34.dp
private val SPEED = 52.dp
private val HERO_TIME = 56.sp
private val HERO_TIME_MIN = 28.sp
private const val HERO_TIME_PER_DP = 0.16f
private val COMING_WIDTH = 118.dp
private val STATUS_BAR_W = 22.dp
private val STATUS_BAR_H = 4.dp
private val MOVE_HEIGHT = 48.dp
private val MAP_BUTTON = 40.dp
private const val DONE_ALPHA = 0.55f
private const val HOME_BEAT = 1.12f
private const val HOME_BEAT_MS = 700
private const val BROWSE_RETURN_MS = 20_000L
