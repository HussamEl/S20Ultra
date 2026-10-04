package se.eldebosh.nastastopp.ui.screens

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.view.ViewGroup
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.time.LocalTime
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.display.DisplayItem
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.core.display.TimeStatus
import se.eldebosh.nastastopp.core.geo.GeoLogic
import se.eldebosh.nastastopp.core.link.LinkMessage
import se.eldebosh.nastastopp.core.nav.DisplayEta
import se.eldebosh.nastastopp.core.nav.OrderPlanner
import se.eldebosh.nastastopp.core.parse.TripKinds
import se.eldebosh.nastastopp.core.parse.TripTimes
import se.eldebosh.nastastopp.core.route.Announcement
import se.eldebosh.nastastopp.core.route.Announcements
import se.eldebosh.nastastopp.core.weather.DisplayWeather
import se.eldebosh.nastastopp.core.weather.WeatherKind
import se.eldebosh.nastastopp.core.youdrive.CardNotes
import se.eldebosh.nastastopp.core.youdrive.TripCardText
import se.eldebosh.nastastopp.settings.WindowPlace
import se.eldebosh.nastastopp.settings.WindowPlaces
import se.eldebosh.nastastopp.ui.DoneMarks
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.AppTheme
import se.eldebosh.nastastopp.ui.theme.DigitFont
import se.eldebosh.nastastopp.ui.theme.DisplayFont
import se.eldebosh.nastastopp.ui.theme.DisplayTheme

/**
 * Passenger display: made to be read by the passengers from their seats, while the driver works
 * it from the phone. The next stop starts at the top; at the bottom the clock stands in the left
 * corner with the next stop's time beside it, then "Därefter" and the trip after it, in short.
 * Everything is only what it shows: no frames. The words for the passengers are Swedish,
 * like the announcements; the connection line (for the driver) follows the app's language.
 *
 * It moves with the route:
 * - when an announcement is spoken ([spoken] goes up by one each time, and a tap on the next stop
 *   counts too), the screen before goes completely; the next stop pops in alone and is said with
 *   its time; it stays a moment and fades away; then "Därefter" pops in its place and is said;
 *   then the whole screen comes back (the voice keeps silent while the screen changes, [voice]);
 * - a tap on "Därefter", the trip after it or a trip in the strip a swipe along the bottom line
 *   brings out says it and shows it the same way for a few seconds;
 * - a tap on the clock says the time;
 * - when the minute changes, the time grows into the middle of the screen over five seconds,
 *   stays a while and goes back faster (never while something is being said).
 *
 * Made for landscape (a tablet); held upright, the stops page up and down.
 *
 * @param status the phone's name on a remote display (null on the driver's own device).
 * @param onSay says what a tap on the clock or a card asks for, on this device.
 * @param time the time of day (tests set it).
 * @param weatherWidget a weather app's widget hosted on this tablet (207), shown in place of SMHI's weather.
 * @param routeMap the tablet's Google map (208): only when the map sign is tapped or a trip
 *   pressed long, as the driver's own map (moved by touch and buttons, closed by its ×); it is
 *   never one of the moments. The display opens Google's apps only from its 242 and 244.
 * @param mapLive the tablet knows where it is.
 * @param onWantPosition the map is asked for: the tablet's location permission, if not given yet.
 * @param dark the black look ([DisplayColors]) or the light one; [onToggleLook] switches it (223).
 * @param onOrder sends the order the driver set on his map to the phone (the trips' numbers).
 */
@Composable
fun PassengerDisplayScreen(
    snapshot: DisplaySnapshot?,
    status: String?,
    connected: Boolean,
    onSpeak: () -> Unit,
    onExit: () -> Unit,
    detail: String? = null,
    spoken: Int = 0,
    onSay: (Announcement) -> Unit = {},
    time: () -> LocalTime = { LocalTime.now() },
    weatherWidget: (@Composable (Modifier) -> Unit)? = null,
    routeMap: RouteMap? = null,
    mapLive: Boolean = false,
    onWantPosition: (() -> Unit)? = null,
    dark: Boolean = true,
    onToggleLook: (() -> Unit)? = null,
    onOrder: ((List<Long>) -> Unit)? = null,
    onEarth: ((Double, Double) -> Unit)? = null,
    onStreetPhotos: ((Double, Double) -> Unit)? = null,
    places: WindowPlaces? = null,
    /** The controls the driver used on the phone's floating panel, carried out here as if tapped. */
    remote: Flow<LinkMessage.Remote>? = null,
    /** What this display's map shows, for the phone's floating panel. */
    onMapView: ((LinkMessage.MapView) -> Unit)? = null,
    /** The car moved in the last two minutes (the tablet's motion): the moments and the map's flights come only then. */
    awake: Boolean = true,
    /** How much the car shakes now (0–1), for the motion sign on the top line; null without a sensor. */
    motion: (() -> Float)? = null,
    /**
     * Each part of a next-stop announcement once this device's voice has said it
     * ([se.eldebosh.nastastopp.tts.Announcer.said]); null when this device does not say it (the
     * display then goes by the text's length).
     */
    voice: Flow<Int>? = null,
) {
    KeepScreenOnFullscreen()
    // The trip card and the driver's list of trips open where he last left them, as big.
    val windows = places ?: remember { WindowPlaces.InMemory() }
    val cardPlace = remember(windows) { WindowState(CARD_WINDOW, windows, WindowPlace(0.5f, 0.5f)) }
    val listPlace = remember(windows) { WindowState(LIST_WINDOW, windows, WindowPlace(1f, 0.5f)) }
    var tapped by remember { mutableIntStateOf(0) }
    val cue = spoken + tapped
    // Taps on a card or an address: said on this device, and the time keeps still meanwhile.
    var said by remember { mutableIntStateOf(0) }
    val say: (Announcement) -> Unit = {
        said++
        onSay(it)
    }
    val now = rememberNow(time)
    val live = snapshot?.takeIf { it.active && it.current != null }
    val moments = rememberMoments(
        now,
        cue + said,
        snapshot?.announcementSv,
        hasWeather = live?.weather != null || weatherWidget != null,
        hasEta = live?.eta != null,
        awake = awake,
    )
    // Everything but the time (or the weather, or the travel time) steps back while it shows, and
    // is hidden behind a solid ground at its largest.
    val rest = Modifier.stepBack { moments.back }
    // How the next stop's time stands, for the clock's colon (none without a route).
    val nextTime = live?.current?.time
    val hasNext = live != null
    val timeStatus by remember(nextTime, hasNext) {
        derivedStateOf { if (hasNext) TimeStatus.of(TripTimes.minutesUntil(nextTime, now.value.hour * 60 + now.value.minute)) else null }
    }
    // A long press on a trip (or the map sign for the next stop): the driver's own map of the way
    // to it, filling the screen until he closes it, moved by his fingers and buttons. The way goes
    // from the car through the trip before it and the two after it ([DisplaySnapshot.around]), and
    // each trip he adds (+), and comes as soon as the tablet knows where it is. On it the driver can
    // try another order and send it to the phone ([WayList]).
    var wayTrip by remember { mutableStateOf<DisplayItem?>(null) }
    var added by remember { mutableIntStateOf(0) }
    var earlier by remember { mutableIntStateOf(0) }
    // The trips the driver took off the way on this map (×); the phone's route keeps them.
    var removed by remember { mutableStateOf(emptyList<DisplayItem>()) }
    val edit = remember { WayEdit() }
    val map = routeMap?.takeIf { !it.refused }
    val showWay: ((DisplayItem, Boolean) -> Unit)? = map?.let { _ ->
        { item, isNext ->
            onWantPosition?.invoke()
            wayTrip = if (isNext) null else item.trip
            added = 0
            earlier = 0
            removed = emptyList()
            edit.clear()
            moments.playFocus()
        }
    }
    val ahead = live?.ahead.orEmpty()
    val wayAt = wayTrip?.let { t -> ahead.indexOfFirst { it.sameTrip(t) } }?.takeIf { it >= 0 } ?: 0
    val (around, aroundAt) = DisplaySnapshot.around(ahead, wayAt, added, earlier)
    val window = around.filterNot { t -> removed.any { it.sameTrip(t) } }
    val windowAt = around.getOrNull(aroundAt)?.let { looked -> window.indexOfFirst { it.sameTrip(looked) } }?.takeIf { it >= 0 } ?: 0
    val windowKey = window.map { it.mapStop.key }
    LaunchedEffect(windowKey) { edit.settle(window) }
    val wayShown = edit.preview ?: window
    val openedAt = window.getOrNull(windowAt)?.let { looked -> wayShown.indexOfFirst { it.sameTrip(looked) } }?.takeIf { it >= 0 } ?: 0
    // The trip picked in the list is the one looked at: the red letter, the minutes to it, and 242 / 244.
    val wayShownAt = edit.lookedIndex(wayShown, openedAt)
    // Where the map sign is: the map grows out of it.
    var pinAt by remember { mutableStateOf(Offset.Unspecified) }
    // The trip whose YouDrive card is open (235, from its person figure), over everything until a
    // tap; nothing comes over it.
    var openCard by remember { mutableStateOf<DisplayItem?>(null) }
    // The card closes when the phone's link breaks or the route ends.
    LaunchedEffect(connected, live != null) {
        if (!connected || live == null) openCard = null
    }
    LaunchedEffect(openCard != null) {
        if (openCard != null) moments.settle()
    }
    LaunchedEffect(moments.holding) { if (!moments.holding) routeMap?.unfocus() }
    // The driver's own map (the map sign, a trip pressed long): his touches move it, and it stays
    // while he uses it.
    val held = moments.holding && moments.shown == Moments.Info.FOCUS
    DisposableEffect(routeMap, moments) {
        routeMap?.onTouch = { moments.touched() }
        onDispose { routeMap?.onTouch = null }
    }
    LaunchedEffect(held) {
        if (held) return@LaunchedEffect
        edit.clear()
        added = 0
        earlier = 0
        removed = emptyList()
    }
    // The driver's way on his map; an order he tries a moment after his last tap on the arrows, or
    // once he lets go of a trip he drags.
    val wayKey = wayShown.map { it.mapStop.key }
    LaunchedEffect(map, held, wayKey, wayShownAt, edit.dragging) {
        if (!held || map == null || wayShown.isEmpty() || edit.dragging) return@LaunchedEffect
        if (edit.preview != null) delay(ORDER_ASK_MS)
        map.focus(wayShown.map { it.mapStop }, wayShownAt)
    }
    val canAdd = DisplaySnapshot.canAdd(ahead, wayAt, added, earlier)
    val canAddEarlier = DisplaySnapshot.canAddEarlier(ahead, wayAt, added, earlier)
    val removeTrip = { trip: DisplayItem ->
        removed = removed + trip
        edit.preview = edit.preview?.filterNot { it.sameTrip(trip) }
        if (edit.picked?.sameTrip(trip) == true) edit.picked = null
    }
    // The stop looked at: its own point, else where Google's way to it ends.
    val lookedPoint: () -> Pair<Double, Double>? = {
        val looked = wayShown.getOrNull(wayShownAt)
        val own = looked?.let { item -> item.lat?.let { lat -> item.lng?.let { lng -> lat to lng } } }
        own ?: routeMap?.route?.takeIf { routeMap.routeKey == RouteMap.keyOf(wayShown.map { it.mapStop }) }?.legs?.getOrNull(wayShownAt)?.path?.lastOrNull()
    }
    // The way's trips as Google gave their legs, for the phone: the minutes of each.
    val wayLine = routeMap?.route?.takeIf { routeMap.routeKey == RouteMap.keyOf(wayShown.map { it.mapStop }) }?.legs?.takeIf { it.size == wayShown.size }
    val wayAllowed = OrderPlanner.allowed(wayShown.indices.toList(), plannerTrips(wayShown))
    // What the map shows, told to the phone's floating panel each time it changes.
    if (onMapView != null) {
        val view = LinkMessage.MapView(
            hasMap = map != null,
            open = held,
            ids = if (held) wayShown.mapNotNull { it.id } else emptyList(),
            at = if (held) wayShownAt else 0,
            minutes = if (held) wayShown.indices.map { i -> wayLine?.get(i)?.let { (it.seconds + 30) / 60 } } else emptyList(),
            changed = held && edit.preview != null,
            allowed = wayAllowed,
            canAdd = held && canAdd,
            canAddEarlier = held && canAddEarlier,
            satellite = map?.satellite == true,
            suggesting = map?.suggesting == true,
            located = map?.located == true,
        )
        LaunchedEffect(view, connected) { onMapView(view) }
    }
    // The trip shown at the top (the next stop, or one paged to): reported by the stage with the
    // next stop it belongs to (so a stale one is never shown) and whether the screen is focused on it.
    var shownTrip by remember { mutableStateOf<Triple<DisplayItem, DisplayItem, Boolean>?>(null) }
    val reported = live?.current?.let { next -> shownTrip?.takeIf { it.first == next.trip } }
    val tripShown = reported?.second ?: live?.current
    // Focused (a trip tapped, or the next stop and then "Därefter" while the announcement says
    // them): only that trip is shown, its address at the top and its time large under it; the
    // bottom line, the dots and the top line's signs fade away.
    val focus = reported?.third == true
    // The strip of all the trips: a swipe along the bottom line brings it out in the line's place,
    // and the top follows it.
    val browse = remember { Browse() }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = live?.earlier?.size ?: 0)
    val snap = rememberSnapFlingBehavior(list, SnapPosition.Center)
    val lineSwiped by browse.drags.collectIsDraggedAsState()
    val listSwiped by list.interactionSource.collectIsDraggedAsState()
    val swiping = lineSwiped || listSwiped
    // Home (200), or the strip put away after a while: the stage goes back to the next stop.
    var homeCalls by remember { mutableIntStateOf(0) }
    // A trip the phone's panel shows: the stage pages to it (its number, and a count so the same
    // trip asked again is shown again).
    var showCall by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    // The phone's floating panel: each of its controls as if tapped here.
    if (remote != null) {
        val act by rememberUpdatedState { r: LinkMessage.Remote ->
            fun trip(id: Long?): DisplayItem? = if (id == null) live?.current else ahead.firstOrNull { it.id == id }
            fun onMap(block: (RouteMap) -> Unit) {
                if (!held || map == null) return
                moments.touched()
                block(map)
            }
            when (r.action) {
                LinkMessage.Remote.Action.SHOW_TRIP -> {
                    val id = r.id
                    if (id == null || id == live?.current?.id) homeCalls++ else showCall = id to ((showCall?.second ?: 0) + 1)
                }
                LinkMessage.Remote.Action.OPEN_MAP -> trip(r.id)?.let { item -> showWay?.invoke(item, r.id == null || item.id == live?.current?.id) }
                LinkMessage.Remote.Action.CLOSE_MAP -> if (held) moments.settle()
                LinkMessage.Remote.Action.SATELLITE -> onMap { it.showSatellite(r.on ?: !it.satellite) }
                LinkMessage.Remote.Action.TO_CAR -> onMap { it.toCar() }
                LinkMessage.Remote.Action.TO_STOP -> onMap { it.toStop() }
                LinkMessage.Remote.Action.WHOLE -> onMap { it.whole() }
                LinkMessage.Remote.Action.LOOK_AT -> onMap { m ->
                    val i = wayShown.indexOfFirst { it.id == r.id }
                    if (i >= 0) {
                        edit.picked = wayShown[i]
                        m.lookAt(i)
                    }
                }
                LinkMessage.Remote.Action.TRY_ORDER -> onMap {
                    val ids = r.ids.orEmpty()
                    val tried = ids.mapNotNull { id -> wayShown.firstOrNull { it.id == id } }
                    if (tried.size == wayShown.size && tried.size == ids.toSet().size) {
                        edit.preview = tried
                        edit.advice = null
                    }
                }
                LinkMessage.Remote.Action.UNDO -> onMap {
                    edit.preview = null
                    edit.picked = null
                    edit.advice = null
                }
                LinkMessage.Remote.Action.APPLY -> onMap {
                    val ids = wayShown.mapNotNull { it.id }.takeIf { it.size == wayShown.size }
                    if (ids != null && edit.preview != null && wayAllowed && edit.sent != ids) {
                        edit.sent = ids
                        onOrder?.invoke(ids)
                    }
                }
                LinkMessage.Remote.Action.ADD -> onMap { if (canAdd) added++ }
                LinkMessage.Remote.Action.ADD_EARLIER -> onMap { if (canAddEarlier) earlier++ }
                LinkMessage.Remote.Action.REMOVE -> onMap { wayShown.firstOrNull { it.id == r.id }?.let(removeTrip) }
                LinkMessage.Remote.Action.SUGGEST -> onMap { m ->
                    if (m.located && !m.suggesting && wayShown.size > 1) suggestOrder(m, wayShown, fromNext = wayAt - windowAt == 0, now.value)
                }
                LinkMessage.Remote.Action.SAY_TIME -> {
                    onSay(Announcements.clock(now.value.hour, now.value.minute))
                    moments.playTime(tapped = true)
                }
                LinkMessage.Remote.Action.EARTH -> onMap { lookedPoint()?.let { (lat, lng) -> onEarth?.invoke(lat, lng) } }
                LinkMessage.Remote.Action.STREET_PHOTOS -> onMap { lookedPoint()?.let { (lat, lng) -> onStreetPhotos?.invoke(lat, lng) } }
            }
        }
        LaunchedEffect(remote) { remote.collect { act(it) } }
    }
    LaunchedEffect(swiping, list.isScrollInProgress, browse.open) {
        if (swiping) {
            browse.open = true
            return@LaunchedEffect
        }
        // Out while it is swiped or still gliding, and a few seconds more.
        if (!browse.open || list.isScrollInProgress) return@LaunchedEffect
        delay(BROWSE_LINGER_MS)
        browse.open = false
        homeCalls++
    }
    // Put away, it opens next time with the next stop in its middle.
    val home = live?.earlier?.size ?: 0
    LaunchedEffect(browse.open, home, live?.current?.trip) {
        if (browse.open) return@LaunchedEffect
        delay(BROWSE_OUT_MS.toLong())
        list.requestScrollToItem(home)
    }
    LaunchedEffect(live?.current?.trip) { browse.open = false }
    val away = tripShown != null && tripShown.trip != live?.current?.trip
    // Busy: the minute's moments wait (two clocks never move at once).
    val busy = openCard != null || focus || browse.open || away
    LaunchedEffect(busy) { moments.paused = busy }
    val lineHidden = focus || browse.open
    val line by animateFloatAsState(if (lineHidden) 0f else 1f, tween(if (lineHidden) CLOCK_FADE_MS else CLOCK_BACK_MS), label = "line")
    val chrome by animateFloatAsState(if (focus) 0f else 1f, tween(if (focus) CLOCK_FADE_MS else CLOCK_BACK_MS), label = "chrome")
    // Black (or light, 223); Swedish for the passengers, read left to right whatever the app's language.
    // The arrows' light: one frame loop, while there is a route to show.
    val arrowPhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(live != null) {
        if (live == null) return@LaunchedEffect
        while (true) withInfiniteAnimationFrameMillis { arrowPhase.floatValue = (it % ARROW_FLOW_MS).toFloat() / ARROW_FLOW_MS }
    }
    DisplayTheme(dark) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalArrowPhase provides { arrowPhase.floatValue }) {
    // Black to the screen's very edge (under a camera's cutout too); the content keeps clear of it.
    BoxWithConstraints(Modifier.fillMaxSize().background(AppTheme.colors.background).safeDrawingPadding()) {
        val landscape = maxWidth > maxHeight
        val width = maxWidth
        val density = LocalDensity.current
        var screen by remember { mutableStateOf(Rect.Zero) }
        // On a narrow phone the clock leaves room for the next stop's time beside it.
        val minuteSize = if (landscape) MINUTE_SP_WIDE else ((width - NARROW_RIGHT_ROOM).value / CLOCK_WIDTH_PER_SP).coerceIn(MINUTE_SP_MIN, MINUTE_SP_NARROW).sp
        val tripSize = if (landscape) HERO_TIME_SP else HERO_TIME_SP_NARROW
        val side = if (landscape) 32.dp else 20.dp
        // Room under the clock's digits for its seconds, and the dots under all.
        val bottomPad = with(density) { (minuteSize * (SECONDS_DROP + SECOND_SHARE * DIGIT_HEIGHT)).toDp() } + SECONDS_CLEAR
        // The clock at the bottom, as laid out (the coming trips follow it).
        var clockLine by remember { mutableStateOf(IntSize.Zero) }
        // The map lies under everything, unseen until its moment, so it loads once and stays ready.
        if (routeMap != null) {
            MapLayer(routeMap, moments, held, from = { if (pinAt.isSpecified) pinAt - screen.topLeft else Offset.Unspecified })
        }
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { screen = it.boundsInRoot() }
                .padding(start = side, end = side, top = 12.dp, bottom = bottomPad),
        ) {
            // The top line, small and quiet: the connection at the left, the signs and the battery
            // at the right, hidden until a tap or a swipe down on the line (298) shows them for a
            // while; the motion sign (297) always at the far right. The address starts just under it.
            var topShown by remember { mutableStateOf(false) }
            var topCalls by remember { mutableIntStateOf(0) }
            LaunchedEffect(topShown, topCalls) {
                if (!topShown) return@LaunchedEffect
                delay(TOP_SHOWN_MS)
                topShown = false
            }
            val showTop = {
                topShown = true
                topCalls++
            }
            val reveal = stringResource(R.string.display_show_signs)
            Row(
                Modifier
                    .ref(298)
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget)
                    .pointerInput(Unit) { detectTapGestures { showTop() } }
                    .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 0) showTop() } }
                    .semantics { contentDescription = reveal },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(topShown && status != null, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                    if (status != null) {
                        ConnectionSign(status, connected, wide = landscape, onExit = onExit, enabled = !focus, modifier = rest.graphicsLayer { alpha = chrome })
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(rest.graphicsLayer { alpha = chrome }.padding(start = 16.dp), horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(topShown, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                    TopLine(
                        // The way to the next stop: the display's map, or Google Maps on its address.
                        onMap = live?.current?.let { next -> showWay?.let { { it(next, true) } } },
                        onMapPlaced = { pinAt = it },
                        weather = live?.weather,
                        hasWeather = live?.weather != null || weatherWidget != null,
                        onWeather = { moments.playInfo(Moments.Info.WEATHER) },
                        dark = dark,
                        onToggleLook = onToggleLook,
                        enabled = !focus,
                    )
                    }
                    if (detail != null) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                            color = AppTheme.colors.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.ref(95),
                        )
                    }
                }
                if (motion != null) MotionSign(motion, awake)
            }
            val lineHeight = with(density) { clockLine.height.toDp() }

            if (live == null) {
                Box(rest.weight(1f).fillMaxWidth().padding(bottom = lineHeight), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.passenger_waiting),
                        fontFamily = DisplayFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 44.sp,
                        color = AppTheme.colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                return@Column
            }

            Stage(
                live,
                landscape,
                cue,
                voice = voice,
                clockSize = minuteSize,
                line = lineHeight,
                besideClock = clockLine.width,
                below = bottomPad,
                tripSize = tripSize,
                browse = browse,
                list = list,
                snap = snap,
                homeCalls = homeCalls,
                showCall = showCall,
                // A tap on the next stop's address says the announcement here.
                onSpeakNext = {
                    tapped++
                    onSpeak()
                },
                onSay = say,
                showWay = showWay,
                onShown = { next, item, byTap -> shownTrip = Triple(next, item, byTap) },
                lineBack = { moments.lineBack },
                nowMinutes = { now.value.hour * 60 + now.value.minute },
                // The passenger's last name, tapped: said here and shown large.
                onCard = { openCard = it },
                onName = { name ->
                    onSay(Announcements.passenger(name))
                    moments.playInfo(Moments.Info.NAME)
                },
                homeButton = {
                    // Back to the next stop, while the display shows anything else: small, beating
                    // gently, at the left of the dots.
                    HomeButton(
                        visible = away || focus || browse.open,
                        onClick = {
                            browse.open = false
                            homeCalls++
                        },
                    )
                },
                modifier = rest.weight(1f).fillMaxWidth(),
            )
        }
        // The clock in the bottom-left corner; the coming trips follow it ([Stage]). Only its digits
        // count: the line is as tall as their ink, and the seconds hang under the minutes. A swipe
        // along it brings out the strip
        // of all the trips in its place (while the strip is out, the line takes no touches but the
        // swipe that brought it).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = side, bottom = bottomPad)
                .onSizeChanged { clockLine = it }
                .then(
                    if (live != null && !focus && (!browse.open || lineSwiped || list.isScrollInProgress)) {
                        Modifier.scrollable(list, Orientation.Horizontal, reverseDirection = true, flingBehavior = snap, interactionSource = browse.drags)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Clock(
                now = now,
                minuteSize = minuteSize,
                grow = { moments.grow.value },
                hue = AppTheme.colors.showHues[moments.hue % AppTheme.colors.showHues.size],
                status = timeStatus,
                screen = { screen },
                // Said here, and the time springs out to fill the screen meanwhile.
                onClick = if (lineHidden) {
                    null
                } else {
                    {
                        onSay(Announcements.clock(now.value.hour, now.value.minute))
                        moments.playTime(tapped = true)
                    }
                },
                modifier = Modifier.graphicsLayer { alpha = line }.ref(88).ink(minuteSize),
            )
        }
        // The weather or the travel time, in the middle while it shows.
        InfoMoment(moments, live?.weather, live?.eta, landscape, weatherWidget, live?.current, Modifier.align(Alignment.Center))
        if (routeMap != null) {
            MapMomentText(
                moments,
                live?.eta,
                routeMap,
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(MAP_OVER_Z).padding(bottom = maxHeight * MAP_TEXT_LOW),
            )
            MapControls(
                routeMap,
                visible = held && moments.infoShown,
                onUse = { moments.touched() },
                onClose = { moments.settle() },
                at = lookedPoint,
                onEarth = onEarth,
                onStreetPhotos = onStreetPhotos,
                modifier = Modifier.fillMaxSize().zIndex(MAP_OVER_Z),
            )
            WayList(
                visible = held && moments.infoShown && wayShown.isNotEmpty(),
                map = routeMap,
                edit = edit,
                shown = wayShown,
                at = wayShownAt,
                window = window,
                fromNext = wayAt - windowAt == 0,
                now = { now.value },
                note = mapNote(routeMap),
                onOrder = onOrder,
                onAdd = if (canAdd) ({ added++ }) else null,
                onAddEarlier = if (canAddEarlier) ({ earlier++ }) else null,
                onRemove = removeTrip,
                onUse = { moments.touched() },
                place = listPlace,
                modifier = Modifier.fillMaxSize().zIndex(MAP_OVER_Z),
            )
        }
        // While the time, the weather or the travel time fills the screen, a tap anywhere brings the
        // screen back at once (the driver's own map takes his touches, and closes on its ×).
        if (moments.showing && !held) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { moments.settle() } },
            )
        }
        // The trip's YouDrive card, for the driver: over everything until a tap, never said.
        var lastCard by remember { mutableStateOf<DisplayItem?>(null) }
        if (openCard != null) lastCard = openCard
        AnimatedVisibility(
            openCard != null,
            modifier = Modifier.zIndex(CARD_Z),
            enter = fadeIn(tween(CARD_IN_MS)) + scaleIn(tween(CARD_IN_MS, easing = FastOutSlowInEasing), initialScale = 0.94f),
            exit = fadeOut(tween(CARD_OUT_MS)),
        ) {
            lastCard?.let { TripCard(it, landscape, cardPlace, onClose = { openCard = null }) }
        }
    }
    }
    }
}

/**
 * The strip of all the trips, done and coming, out at the bottom in the clock's line while the
 * passengers look through it ([open]): a swipe along the bottom line (the clock, the next stop's
 * time, "Därefter") brings it out and scrolls it, the trip in its middle shown at the top; it goes
 * a few seconds after the last swipe.
 */
@Stable
private class Browse {
    var open by mutableStateOf(false)

    /** Swipes on the bottom line (the strip's own come through its state). */
    val drags = MutableInteractionSource()
}

/** The time of day, ticking with the seconds. */
@Composable
private fun rememberNow(time: () -> LocalTime): State<LocalTime> {
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
 * What takes the middle of the screen for a moment, over everything else:
 * - the time with its seconds, each time the minute changes: [grow] 0 → 1 over five seconds, in a
 *   new colour each time ([hue]). As it nears its largest, the rest of the screen fades gradually
 *   behind a solid ground ([solid]), which stays two seconds; then the time goes back, faster,
 *   while the ground clears again gradually. A tap on the clock springs it out the same way and
 *   holds it a little longer;
 * - the weather in the middle of each minute, and a little later the tablet's map of the way to
 *   the next stop, or Google Maps' travel time alone ([info] 0 → 1 → 0 over seven seconds, ten
 *   for the map; [shown] says which).
 *
 * A tap anywhere ([settle]) and anything said send it straight back.
 */
@Stable
private class Moments(private val scope: CoroutineScope) {
    val grow = Animatable(0f)
    val solid = Animatable(0f)
    val info = Animatable(0f)
    var shown by mutableStateOf(Info.WEATHER)
        private set
    var hue by mutableIntStateOf(0)
        private set

    /** How long [info] takes to go, as it goes (the map goes along with it). */
    var outMs = INFO_OUT_MS
        private set

    /** The map shows the way to a trip the driver pressed long: nothing else comes until a tap. */
    var holding by mutableStateOf(false)
        private set

    /**
     * The display is busy (a trip shown large or said, the strip out, a page away from the next
     * stop, a trip's card open): the minute's own moments do not come, so two clocks never move at
     * once. A tap still brings what it asks for.
     */
    var paused by mutableStateOf(false)

    /** [info] shows something (read without following every frame of it). */
    val infoShown: Boolean by derivedStateOf { info.value > 0f }
    private var job: Job? = null

    /** How far everything else steps back: 0 in view, 1 gone. */
    val back: Float get() = maxOf(REST_FADE * grow.value, solid.value, info.value)

    /** How far the bottom line beside the clock steps back: gone before the growing clock reaches it. */
    val lineBack: Float get() = maxOf(minOf(1f, LINE_FADE_SPEED * grow.value), back)

    /** Something fills the middle now (a tap brings the screen back). */
    val showing: Boolean by derivedStateOf { grow.value > 0f || info.value > 0f }

    val idle: Boolean get() = job?.isActive != true && grow.value == 0f && info.value == 0f

    /** The time fills the screen: slowly as the minute changes, springing out when [tapped]. */
    fun playTime(tapped: Boolean = false) {
        if (holding) return
        job?.cancel()
        hue++
        outMs = SETTLE_MS
        job = scope.launch {
            launch { info.animateTo(0f, tween(SETTLE_MS)) }
            coroutineScope {
                if (tapped) {
                    launch { grow.animateTo(1f, spring(dampingRatio = TAP_SPRING_DAMPING, stiffness = Spring.StiffnessVeryLow)) }
                    delay(TAP_SOLID_AFTER_MS)
                } else {
                    launch { grow.animateTo(1f, tween(GROW_MS, easing = FastOutSlowInEasing)) }
                    delay((GROW_MS - SOLID_IN_MS).toLong())
                }
                solid.animateTo(1f, tween(SOLID_IN_MS, easing = FastOutSlowInEasing))
            }
            delay(if (tapped) TAP_HOLD_MS else SOLID_HOLD_MS)
            coroutineScope {
                launch { solid.animateTo(0f, tween(SOLID_OUT_MS, easing = FastOutSlowInEasing)) }
                grow.animateTo(0f, tween(SHRINK_MS, easing = FastOutLinearInEasing))
            }
        }
    }

    fun playInfo(which: Info) {
        if (holding) return
        job?.cancel()
        shown = which
        outMs = INFO_OUT_MS
        job = scope.launch {
            info.animateTo(1f, tween(INFO_IN_MS, easing = FastOutSlowInEasing))
            delay(if (which == Info.MAP) MAP_HOLD_MS else INFO_HOLD_MS)
            info.animateTo(0f, tween(INFO_OUT_MS, easing = FastOutSlowInEasing))
        }
    }

    /** The driver's map with the way to a trip, held until it is closed (or long unused). */
    fun playFocus() {
        job?.cancel()
        shown = Info.FOCUS
        holding = true
        outMs = INFO_OUT_MS
        job = scope.launch {
            launch { solid.animateTo(0f, tween(SETTLE_MS)) }
            launch { grow.animateTo(0f, tween(SETTLE_MS)) }
            info.animateTo(1f, tween(INFO_IN_MS, easing = FastOutSlowInEasing))
            delay(FOCUS_MAX_MS)
            holding = false
            info.animateTo(0f, tween(INFO_OUT_MS, easing = FastOutSlowInEasing))
        }
    }

    /** The held map is being used: it stays [FOCUS_MAX_MS] from now. */
    fun touched() {
        if (!holding) return
        job?.cancel()
        job = scope.launch {
            launch { info.animateTo(1f, tween(SETTLE_MS)) }
            delay(FOCUS_MAX_MS)
            holding = false
            info.animateTo(0f, tween(INFO_OUT_MS, easing = FastOutSlowInEasing))
        }
    }

    /** Straight back: a tap, or something being said. */
    fun settle() {
        holding = false
        job?.cancel()
        outMs = SETTLE_MS
        job = scope.launch {
            launch { solid.animateTo(0f, tween(SETTLE_MS)) }
            launch { info.animateTo(0f, tween(SETTLE_MS)) }
            grow.animateTo(0f, tween(SETTLE_MS))
        }
    }

    enum class Info { WEATHER, ETA, MAP, FOCUS, NAME }
}

/**
 * The display's moments in turn: the time, the weather, then the map (else Google Maps' travel
 * time), each [MOMENT_GAP_S] seconds after the one before has gone (one with nothing to show is
 * skipped). Nothing comes while something is being said ([cue] goes up with each announcement and
 * tap; a new cue sends it straight back), while the display is busy, or while the car has stood
 * still for two minutes ([awake] false).
 */
@Composable
private fun rememberMoments(now: State<LocalTime>, cue: Int, spokenText: String?, hasWeather: Boolean, hasEta: Boolean, awake: Boolean): Moments {
    val scope = rememberCoroutineScope()
    val moments = remember(scope) { Moments(scope) }
    var quiet by remember { mutableStateOf(false) }
    LaunchedEffect(cue) {
        if (cue == 0) return@LaunchedEffect
        quiet = true
        moments.settle()
        delay(msToSay(spokenText))
        quiet = false
    }
    val weather by rememberUpdatedState(hasWeather)
    val eta by rememberUpdatedState(hasEta)
    val moving by rememberUpdatedState(awake)
    val calm by rememberUpdatedState(!quiet)
    LaunchedEffect(moments) {
        var turn = 0
        var restS = 0
        while (true) {
            delay(1_000L)
            // The gap counts only while nothing else shows and the car moves.
            if (!calm || !moments.idle || moments.paused || !moving) {
                restS = 0
                continue
            }
            if (++restS < MOMENT_GAP_S) continue
            restS = 0
            // The next moment that has something to show.
            for (tried in 0 until MOMENT_TURNS) {
                val which = turn
                turn = (turn + 1) % MOMENT_TURNS
                val played = when (which) {
                    0 -> true.also { moments.playTime() }
                    1 -> weather.also { if (it) moments.playInfo(Moments.Info.WEATHER) }
                    // Google Maps' travel time (no map: it is only the driver's, on his tap).
                    else -> eta.also { if (it) moments.playInfo(Moments.Info.ETA) }
                }
                if (played) break
            }
        }
    }
    return moments
}

/**
 * The weather, Google Maps' travel time or the next stop's passenger's last name (with its
 * street, [next]), large in the middle while [moments] shows it.
 */
@Composable
private fun InfoMoment(
    moments: Moments,
    weather: DisplayWeather?,
    eta: DisplayEta?,
    landscape: Boolean,
    weatherWidget: (@Composable (Modifier) -> Unit)?,
    next: DisplayItem?,
    modifier: Modifier = Modifier,
) {
    if (!moments.infoShown || moments.shown == Moments.Info.MAP || moments.shown == Moments.Info.FOCUS) return
    val hue = AppTheme.colors.showHues[(moments.hue + 2) % AppTheme.colors.showHues.size]
    val big = if (landscape) INFO_SP_WIDE else INFO_SP_NARROW
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.graphicsLayer {
            val p = moments.info.value
            alpha = p
            scaleX = 0.7f + 0.3f * p
            scaleY = 0.7f + 0.3f * p
        },
    ) {
        when (moments.shown) {
            // The weather app's own widget, large; it is only looked at (a tap brings the screen back).
            Moments.Info.WEATHER -> if (weatherWidget != null) {
                val w = if (landscape) WIDGET_WIDE_W else WIDGET_NARROW_W
                weatherWidget(Modifier.ref(211).size(w, w * WIDGET_ASPECT))
            } else if (weather != null) {
                WeatherGlyph(weather.kind, hue, Modifier.size(if (landscape) 220.dp else 120.dp))
                Spacer(Modifier.width(if (landscape) 40.dp else 16.dp))
                Column(Modifier.ref(204)) {
                    Text(
                        "${weather.tempC}°",
                        fontFamily = DigitFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = big,
                        color = hue,
                        style = TextStyle(lineHeight = 1.0.em),
                    )
                    Text(
                        weather.swedish,
                        fontFamily = DisplayFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = big * 0.24f,
                        color = AppTheme.colors.textMuted,
                    )
                }
            }
            Moments.Info.ETA -> if (eta != null) {
                RouteGlyph(hue, Modifier.size(if (landscape) 200.dp else 110.dp))
                Spacer(Modifier.width(if (landscape) 40.dp else 16.dp))
                Column(Modifier.ref(205)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${eta.minutes}",
                            fontFamily = DigitFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = big,
                            color = hue,
                            style = TextStyle(lineHeight = 1.0.em),
                        )
                        Text(
                            " min",
                            fontFamily = DisplayFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = big * 0.35f,
                            color = hue,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    Text(
                        listOfNotNull(eta.meters?.let(::distanceText), stringResource(R.string.passenger_to_next_stop)).joinToString("  ·  "),
                        fontFamily = DisplayFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = big * 0.2f,
                        color = AppTheme.colors.textMuted,
                    )
                }
            }
            Moments.Info.NAME -> next?.lastName?.let { name ->
                Box(Modifier.fillMaxWidth(NAME_WIDTH), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.ref(232)) {
                        BasicText(
                            name,
                            style = TextStyle(
                                fontFamily = DisplayFont,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.highlight,
                                textAlign = TextAlign.Center,
                                textDirection = TextDirection.Content,
                                lineHeight = 1.0.em,
                            ),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = MIN_TITLE_SP.sp, maxFontSize = if (landscape) NAME_SP_WIDE else NAME_SP_NARROW, stepSize = 2.sp),
                        )
                        Text(
                            next.title,
                            fontFamily = DisplayFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = if (landscape) 44.sp else 28.sp,
                            color = AppTheme.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(textDirection = TextDirection.Content),
                        )
                    }
                }
            }
            Moments.Info.MAP, Moments.Info.FOCUS -> Unit
        }
    }
}

/**
 * The tablet's map of the way to the next stop, under everything else and filling the screen:
 * unseen until its moment, when it grows out of the map sign ([from], in this layer) as all else
 * steps back, like the time grows out of the clock. Its page does the growing and the edges that
 * melt into the screen's ground ([RouteMap.reveal]): the map's view itself is never scaled or
 * faded here, as a WebView drawn that way can stay blank.
 *
 * The driver's own map ([held]) comes over everything else, which has stepped back, so his
 * touches reach it.
 */
@Composable
private fun MapLayer(map: RouteMap, moments: Moments, held: Boolean, from: () -> Offset) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val on = (moments.shown == Moments.Info.MAP || moments.shown == Moments.Info.FOCUS) && moments.info.targetValue > 0f
    val driver = moments.shown == Moments.Info.FOCUS
    LaunchedEffect(map, on, driver) {
        if (on) {
            val o = from()
            val known = o.isSpecified && size.width > 0 && size.height > 0
            // The minute's map follows the car, then may fly to the next stop (the map knows how often).
            map.reveal(if (known) o.x / size.width else 0.5f, if (known) o.y / size.height else 0.5f, INFO_IN_MS, held = driver, tour = false)
        } else {
            map.conceal(moments.outMs)
        }
    }
    // A view's touches also go to what lies under it: here they stop, so a touch on the map never
    // reaches the stepped-back screen (an address said, the map closed).
    if (held) {
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(MAP_HELD_Z - MAP_FLOOR_BELOW)
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        )
    }
    AndroidView(
        factory = {
            (map.view.parent as? ViewGroup)?.removeView(map.view)
            map.view
        },
        modifier = Modifier.fillMaxSize().zIndex(if (held) MAP_HELD_Z else 0f).onSizeChanged { size = it }.ref(210),
    )
}

/**
 * The driver's buttons on his own map, small at the left: the satellite picture or the map (270),
 * the car (239), the stop looked at (240), the whole way (241); his fingers zoom. Google's own
 * apps at the stop looked at ([at]):
 * Google Earth flying to it in 3D (242, [onEarth]) and Google Maps' street photos of it (244,
 * [onStreetPhotos]), which cost the driver's key nothing; and × at the top (243). Each use keeps
 * the map open ([onUse]).
 */
@Composable
private fun MapControls(
    map: RouteMap,
    visible: Boolean,
    onUse: () -> Unit,
    onClose: () -> Unit,
    at: () -> Pair<Double, Double>?,
    onEarth: ((Double, Double) -> Unit)?,
    onStreetPhotos: ((Double, Double) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(INFO_IN_MS)), exit = fadeOut(tween(SETTLE_MS))) {
        Box(Modifier.fillMaxSize().padding(MAP_CONTROLS_EDGE)) {
            MapButton(R.drawable.ic_close, R.string.display_map_close, 243, highlight = true, modifier = Modifier.align(Alignment.TopEnd), onClick = onClose)
            Column(
                verticalArrangement = Arrangement.spacedBy(MAP_CONTROLS_GAP),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                fun use(action: () -> Unit): () -> Unit = {
                    onUse()
                    action()
                }
                MapButton(
                    R.drawable.ic_layers,
                    if (map.satellite) R.string.display_map_plain else R.string.display_map_satellite,
                    270,
                    highlight = map.satellite,
                    onClick = use { map.showSatellite(!map.satellite) },
                )
                Spacer(Modifier.height(MAP_CONTROLS_GAP))
                MapButton(R.drawable.ic_my_location, R.string.display_map_car, 239, onClick = use { map.toCar() })
                MapButton(R.drawable.ic_pin, R.string.display_map_stop, 240, onClick = use { map.toStop() })
                MapButton(R.drawable.ic_zoom_out_map, R.string.display_map_whole, 241, onClick = use { map.whole() })
                // Google's own apps, at the stop's point: never a map billed on the driver's key.
                val point = at()
                if (onEarth != null) {
                    Spacer(Modifier.height(MAP_CONTROLS_GAP))
                    MapButton(R.drawable.ic_flight, R.string.display_map_tour, 242, enabled = point != null, onClick = use { point?.let { (lat, lng) -> onEarth(lat, lng) } })
                }
                if (onStreetPhotos != null) {
                    MapButton(R.drawable.ic_street, R.string.display_map_street, 244, enabled = point != null, onClick = use { point?.let { (lat, lng) -> onStreetPhotos(lat, lng) } })
                }
            }
        }
    }
}

/** A round button on the driver's map, light over the map in both looks. */
@Composable
private fun MapButton(icon: Int, label: Int, ref: Int, onClick: () -> Unit, modifier: Modifier = Modifier, highlight: Boolean = false, enabled: Boolean = true) {
    val description = stringResource(label)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .refCorner(ref)
            .shadow(AppTheme.effects.currentShadow, CircleShape)
            .size(MAP_BUTTON)
            .clip(CircleShape)
            .background(if (highlight) AppTheme.colors.highlight else AppTheme.colors.card)
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = when {
                highlight -> AppTheme.colors.onInfo
                enabled -> AppTheme.colors.text
                else -> AppTheme.colors.textMuted
            },
            modifier = Modifier.size(MAP_ICON),
        )
    }
}

/**
 * Under the minute's map, in its faded bottom: the minutes and the distance to the next stop
 * (Google Maps' own when it navigates); until the tablet knows where it is, a word that it is
 * looking. Under them, small, what keeps the map or the way from coming ([mapNote]). The driver's
 * own map has its list of trips instead ([WayList]).
 */
@Composable
private fun MapMomentText(moments: Moments, eta: DisplayEta?, map: RouteMap, modifier: Modifier = Modifier) {
    if (moments.shown != Moments.Info.MAP || !moments.infoShown) return
    // Google Maps' own time counts first; else the map's way to the next stop (its first leg).
    val toNext = map.route?.to(0)
    val minutes = if (map.located) eta?.minutes ?: toNext?.first else null
    val meters = eta?.meters ?: toNext?.second
    val note = mapNote(map)
    val hue = AppTheme.colors.showHues[(moments.hue + 2) % AppTheme.colors.showHues.size]
    val big = MAP_SP
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.ref(205).graphicsLayer {
            val p = moments.info.value
            alpha = p
            translationY = (1f - p) * 40.dp.toPx()
        },
    ) {
        if (!map.located) {
            Text(
                stringResource(R.string.passenger_finding_position),
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Medium,
                fontSize = big * 0.22f,
                color = AppTheme.colors.textMuted,
            )
        }
        if (minutes != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$minutes",
                    fontFamily = DigitFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = big,
                    color = hue,
                    style = TextStyle(lineHeight = 1.0.em),
                )
                Text(
                    " min",
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = big * 0.35f,
                    color = hue,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            Text(
                listOfNotNull(meters?.let(::distanceText), stringResource(R.string.passenger_to_next_stop)).joinToString("  ·  "),
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Medium,
                fontSize = big * 0.2f,
                color = AppTheme.colors.textMuted,
            )
        }
        if (note != null) {
            Text(
                note,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Medium,
                fontSize = big * 0.13f,
                color = AppTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).ref(233, centered = true),
            )
        }
    }
}

/**
 * Why the map or the way does not come, in a few words, or null: Google's script did not load,
 * its pictures did not come, the page stopped (its own words), the map is still loading; and
 * Google's answer when it gave no way.
 */
@Composable
private fun mapNote(map: RouteMap): String? {
    val page = when (map.trouble?.takeIf { !map.tiles }) {
        RouteMap.Trouble.NO_SCRIPT -> stringResource(R.string.passenger_map_no_script)
        RouteMap.Trouble.NO_TILES -> stringResource(R.string.passenger_map_no_tiles)
        RouteMap.Trouble.PAGE -> stringResource(R.string.passenger_map_stopped, map.troubleDetail ?: "?")
        null -> if (!map.ready) stringResource(R.string.passenger_map_loading) else null
    }
    val way = map.routeAnswer?.let { if (it == 0) stringResource(R.string.passenger_way_no_answer) else stringResource(R.string.passenger_way_refused, it) }
    return listOfNotNull(page, way).joinToString("  ·  ").ifEmpty { null }
}

/** "5,3 km" / "800 m", the Swedish way. */
private fun distanceText(meters: Int): String =
    if (meters >= 1000) String.format(Locale.forLanguageTag("sv-SE"), "%.1f km", meters / 1000f) else "$meters m"

/** A simple picture of the weather: sun, cloud, fog, rain, sleet, snow or a flash, in [color]. */
@Composable
private fun WeatherGlyph(kind: WeatherKind, color: Color, modifier: Modifier = Modifier) {
    val sun = AppTheme.colors.accent
    val cloud = AppTheme.colors.textMuted
    Canvas(modifier) {
        val w = size.width
        fun drawCloud(cx: Float, cy: Float, s: Float, c: Color) {
            drawCircle(c, radius = s * 0.22f, center = Offset(cx - s * 0.2f, cy + s * 0.04f))
            drawCircle(c, radius = s * 0.3f, center = Offset(cx + s * 0.04f, cy - s * 0.08f))
            drawCircle(c, radius = s * 0.2f, center = Offset(cx + s * 0.28f, cy + s * 0.06f))
            drawRoundRect(c, topLeft = Offset(cx - s * 0.42f, cy + s * 0.02f), size = Size(s * 0.9f, s * 0.24f), cornerRadius = CornerRadius(s * 0.12f))
        }
        fun drawSun(cx: Float, cy: Float, r: Float) {
            drawCircle(sun, radius = r, center = Offset(cx, cy))
            for (i in 0 until 8) {
                val a = (i * 45.0) * Math.PI / 180
                val from = Offset(cx + (r * 1.3f * cos(a)).toFloat(), cy + (r * 1.3f * sin(a)).toFloat())
                val to = Offset(cx + (r * 1.7f * cos(a)).toFloat(), cy + (r * 1.7f * sin(a)).toFloat())
                drawLine(sun, from, to, strokeWidth = r * 0.22f, cap = StrokeCap.Round)
            }
        }
        when (kind) {
            WeatherKind.CLEAR -> drawSun(w / 2, w / 2, w * 0.2f)
            WeatherKind.PARTLY -> {
                drawSun(w * 0.62f, w * 0.36f, w * 0.15f)
                drawCloud(w * 0.46f, w * 0.58f, w * 0.8f, cloud)
            }
            WeatherKind.CLOUDY -> drawCloud(w / 2, w / 2, w, cloud)
            WeatherKind.FOG -> for (i in 0 until 4) {
                drawLine(cloud, Offset(w * (0.18f + 0.06f * (i % 2)), w * (0.3f + 0.13f * i)), Offset(w * (0.82f - 0.06f * (i % 2)), w * (0.3f + 0.13f * i)), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
            }
            else -> {
                drawCloud(w / 2, w * 0.4f, w * 0.9f, cloud)
                for (i in 0 until 3) {
                    val x = w * (0.3f + 0.2f * i)
                    when (kind) {
                        WeatherKind.SNOW -> drawCircle(color, radius = w * 0.045f, center = Offset(x, w * 0.8f))
                        WeatherKind.SLEET -> if (i % 2 == 0) {
                            drawCircle(color, radius = w * 0.04f, center = Offset(x, w * 0.8f))
                        } else {
                            drawLine(color, Offset(x + w * 0.03f, w * 0.7f), Offset(x - w * 0.03f, w * 0.88f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
                        }
                        WeatherKind.THUNDER -> if (i == 1) {
                            val bolt = Path().apply {
                                moveTo(x + w * 0.04f, w * 0.64f)
                                lineTo(x - w * 0.05f, w * 0.8f)
                                lineTo(x + w * 0.02f, w * 0.8f)
                                lineTo(x - w * 0.04f, w * 0.96f)
                            }
                            drawPath(bolt, sun, style = Stroke(width = w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        else -> drawLine(color, Offset(x + w * 0.03f, w * 0.7f), Offset(x - w * 0.03f, w * 0.88f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}

/** A person, simply and lightly: an outlined head over outlined shoulders, in [color]. */
@Composable
internal fun PersonGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val line = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        drawCircle(color, radius = w * 0.19f, center = Offset(w / 2f, w * 0.3f), style = line)
        // The shoulders: the top half of an oval standing on the bottom.
        drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * 0.16f, w * 0.62f), size = Size(w * 0.68f, w * 0.6f), style = line)
    }
}

/** A road winding from a dot to a pin: the way to the next stop. */
@Composable
private fun RouteGlyph(color: Color, modifier: Modifier = Modifier) {
    val road = AppTheme.colors.textMuted
    val hole = AppTheme.colors.background
    Canvas(modifier) {
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.18f, w * 0.84f)
            cubicTo(w * 0.7f, w * 0.84f, w * 0.18f, w * 0.44f, w * 0.62f, w * 0.38f)
        }
        drawPath(path, road, style = Stroke(width = w * 0.05f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.06f, w * 0.06f))))
        drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.18f, w * 0.84f))
        val pin = Offset(w * 0.66f, w * 0.26f)
        drawCircle(color, radius = w * 0.13f, center = pin)
        drawCircle(hole, radius = w * 0.05f, center = pin)
    }
}

/**
 * The next stop and the trips around it, changing together: the new next stop grows out of
 * "Därefter". A spotlight follows what is said.
 *
 * The next stop's address starts at the top. On the bottom line, after the clock and the next
 * stop's time ([besideClock] in), come "Därefter" and the trip after it, small: their time and the
 * start of their street. The passengers can look around without moving the route: at the top they
 * page from the next stop on to the following trips and back to the ones done, each with its time
 * under its address; a swipe along the bottom line brings out the strip of all the trips in the
 * line's place ([browse]), the top following the trip in its middle. The dots at the very bottom
 * say where the top is. A tap on "Därefter", the trip after it or a trip in the strip says it and
 * shows it for a few seconds. Home ([homeCalls]), an announcement and half a minute left alone
 * bring the next stop back. A tap on the address says it: the next stop's announcement
 * ([onSpeakNext]), or another trip's time and place.
 *
 * The stops keep [line] free at their bottom, for the bottom line; the dots go [below] further down.
 */
@Composable
private fun Stage(
    snapshot: DisplaySnapshot,
    landscape: Boolean,
    cue: Int,
    voice: Flow<Int>?,
    clockSize: TextUnit,
    line: Dp,
    besideClock: Int,
    below: Dp,
    tripSize: TextUnit,
    browse: Browse,
    list: LazyListState,
    snap: FlingBehavior,
    homeCalls: Int,
    showCall: Pair<Long, Int>?,
    onSpeakNext: () -> Unit,
    onSay: (Announcement) -> Unit,
    showWay: ((DisplayItem, Boolean) -> Unit)?,
    onShown: (next: DisplayItem, shown: DisplayItem, tapped: Boolean) -> Unit,
    onCard: (DisplayItem) -> Unit,
    onName: (String) -> Unit,
    lineBack: () -> Float,
    nowMinutes: () -> Int,
    homeButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrow = arrowSize(tripSize)
    val voiceNow by rememberUpdatedState(voice)
    Box(modifier) {
        AnimatedContent(
            targetState = snapshot,
            contentKey = { it.current?.trip },
            // The stop being left goes completely before anything of the new one shows; the new
            // next stop then pops in ([pop]).
            transitionSpec = {
                (fadeIn(tween(1, delayMillis = OUT_MS)) togetherWith fadeOut(tween(OUT_MS)) + scaleOut(tween(OUT_MS), targetScale = 0.92f))
                    .using(SizeTransform(clip = false))
            },
            label = "next stop",
        ) { shown ->
            val current = shown.current ?: return@AnimatedContent
            // The stops being left take no part in the strip (it has one state).
            val latest = current.trip == snapshot.current?.trip
            val spotlight = rememberSpotlight()
            // How far the trip at the top has come into view (0 gone, 1 in place): it pops in and fades away.
            val pop = remember { Animatable(0f) }
            val earlier = shown.earlier
            val upcoming = shown.upcoming
            // The trips done, then the coming ones ("cards"); card [home] is "Därefter". The pages
            // at the top (and the strip) are the same with the next stop between them, at [home].
            val home = earlier.size
            val cardCount = earlier.size + upcoming.size
            val cardItem = { k: Int -> if (k < home) earlier[k] else upcoming[k - home] }
            val pageOfCard = { k: Int -> if (k < home) k else k + 1 }
            val pageItem = { p: Int -> if (p == home) current else cardItem(cardOf(p, home)) }
            val pager = rememberPagerState(initialPage = home) { cardCount + 1 }
            val goHome: suspend () -> Unit = { pager.animateScrollToPage(home) }
            val away = pager.currentPage != home
            // The trip at the top, for the focused time.
            val inMiddle = pageItem(pager.currentPage)
            // A trip still coming says where the car is going; a trip done, its time and place.
            // Each name once ("Centralsjukhuset, huvudentrén, Karlstad", never "… Karlstad, Karlstad").
            // A coming trip's word, over its time and in what is said: "Därefter" for the trip after
            // the next stop, "Sen" for the later ones.
            val thenWord = stringResource(R.string.passenger_then)
            val laterWord = stringResource(R.string.passenger_later)
            val wordOf = { page: Int -> if (page == home + 1) thenWord else if (page > home + 1) laterWord else null }
            val sayingOf = { item: DisplayItem, coming: Boolean, word: String? -> Announcements.at(item.time, GeoLogic.fullSpokenName(item.said ?: item.title, item.subtitle, null), coming, word) }
            val sayTrip = { item: DisplayItem, coming: Boolean -> onSay(sayingOf(item, coming, null)) }
            // The announcement, in step with the voice: the screen before goes completely; the next
            // stop pops in, its address and time are said; it stays a moment and fades away; then
            // "Därefter" pops in its place and is said; then home again. The voice keeps silent
            // while the screen changes ([Announcements.parts]). On the device that speaks, the
            // display moves on as each part has been said ([voice]); elsewhere it goes by the
            // text's length.
            LaunchedEffect(cue) {
                if (cue == 0) return@LaunchedEffect
                val parts = Announcements.parts(shown.announcementSv.orEmpty())
                val awaitPart: suspend (Int, Long) -> Unit = { k, estimate ->
                    val heard = voiceNow
                    if (heard == null) {
                        delay(estimate.coerceAtLeast(0L))
                    } else {
                        withTimeoutOrNull(estimate.coerceAtLeast(0L) + VOICE_SLACK_MS) { heard.first { it == k } }
                    }
                }
                spotlight.play {
                    if (pop.value > 0f) pop.animateTo(0f, tween(OUT_MS)) else delay(OUT_MS.toLong())
                    pager.scrollToPage(home)
                    on = NEXT_STOP
                    pop.animateTo(1f, POP_IN)
                    awaitPart(0, Announcements.LEAD_MS - OUT_MS - POP_MS + msToSayPart(parts[0]))
                    delay(NEXT_HOLD_MS)
                    if (upcoming.isNotEmpty() && parts.size > 1) {
                        pop.animateTo(0f, tween(FADE_MS, easing = LinearEasing))
                        on = home
                        pager.scrollToPage(pageOfCard(home))
                        pop.animateTo(1f, POP_IN)
                        awaitPart(1, Announcements.GAP_MS - NEXT_HOLD_MS - FADE_MS - POP_MS + msToSayPart(parts[1]))
                        delay(THEN_HOLD_MS)
                    }
                    pop.animateTo(0f, tween(FADE_MS, easing = LinearEasing))
                    pager.scrollToPage(home)
                }
            }
            // Nothing shown: the trip at the top comes into view (a new stop once the one before has gone).
            LaunchedEffect(spotlight.playing) {
                if (spotlight.playing) return@LaunchedEffect
                if (pop.value == 0f) delay(OUT_MS.toLong())
                pop.animateTo(1f, POP_IN)
            }
            // A trip the phone's panel shows: paged to here too (home again after a while, as when browsed).
            val showBefore = remember { showCall }
            LaunchedEffect(showCall) {
                val (id, _) = showCall?.takeIf { it != showBefore } ?: return@LaunchedEffect
                val page = (0 until pager.pageCount).firstOrNull { pageItem(it).id == id } ?: return@LaunchedEffect
                spotlight.stop()
                pager.animateScrollToPage(page)
            }
            // Home: whatever is shown gives way to the next stop (not for the calls made before).
            val calledBefore = remember { homeCalls }
            LaunchedEffect(homeCalls) {
                if (homeCalls == calledBefore) return@LaunchedEffect
                spotlight.stop()
                goHome()
            }
            // A tapped trip: said, and shown at the top for a moment, then home again.
            // Shown the way an announcement shows a stop: the screen before goes, the trip pops in
            // (its time first, then its address) and is said; it stays two seconds after, fades
            // to black, and the next stop comes back.
            val showCard = { k: Int ->
                val saying = sayingOf(cardItem(k), k >= home, wordOf(pageOfCard(k)))
                spotlight.play {
                    if (pop.value > 0f) pop.animateTo(0f, tween(OUT_MS)) else delay(OUT_MS.toLong())
                    on = k
                    pager.scrollToPage(pageOfCard(k))
                    pop.animateTo(1f, POP_IN)
                    delay(Announcements.LEAD_MS - OUT_MS - POP_MS)
                    onSay(saying)
                    delay(msToSayPart(saying.swedish) + NEXT_HOLD_MS)
                    pop.animateTo(0f, tween(FADE_MS, easing = LinearEasing))
                    pager.scrollToPage(home)
                }
            }
            // A trip picked in the strip: the strip goes and the trip is shown (the next stop is announced).
            val pick = { p: Int ->
                browse.open = false
                if (p == home) onSpeakNext() else showCard(cardOf(p, home))
            }
            // Looked around and left there: back after a while.
            LaunchedEffect(away, pager.isScrollInProgress, browse.open) {
                if (!away || pager.isScrollInProgress || browse.open) return@LaunchedEffect
                delay(BROWSE_RETURN_MS)
                goHome()
            }
            // Out, the strip leads: the top shows the trip in its middle, once the strip has been
            // laid out afresh (before that its middle is where it was last put away).
            var following by remember { mutableStateOf(false) }
            LaunchedEffect(browse.open, latest) {
                following = false
                if (!browse.open || !latest) return@LaunchedEffect
                snapshotFlow { list.layoutInfo }.drop(1).first()
                following = true
            }
            val middle by remember {
                derivedStateOf {
                    val info = list.layoutInfo
                    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index
                }
            }
            LaunchedEffect(middle, following) {
                val p = middle ?: return@LaunchedEffect
                if (following && p < pager.pageCount && p != pager.currentPage) pager.animateScrollToPage(p)
            }
            val spot = spotlight.on
            // While something is said or a tapped trip is shown, the screen shows only that trip:
            // its address with its time over it.
            val focus = spotlight.playing
            LaunchedEffect(inMiddle, focus) { onShown(current.trip, inMiddle, focus) }
            // Focused, the bottom line fades out a moment after a tap on it (so it is seen
            // swelling); a new stop's comes once the one before has gone.
            val thenShown = remember { Animatable(0f) }
            LaunchedEffect(focus, browse.open) {
                if (focus || browse.open) {
                    thenShown.animateTo(0f, tween(STRIP_FADE_MS, delayMillis = if (focus) STRIP_FADE_DELAY_MS else 0))
                } else {
                    if (thenShown.value == 0f) delay(OUT_MS.toLong())
                    thenShown.animateTo(1f, tween(STRIP_BACK_MS))
                }
            }
            val dotsShown by animateFloatAsState(if (focus) 0f else 1f, tween(if (focus) CLOCK_FADE_MS else CLOCK_BACK_MS), label = "dots")
            // A swipe on "Därefter" or the trip after it brings out the strip like one on the clock.
            val swipe = if (focus) Modifier else Modifier.scrollable(list, Orientation.Horizontal, reverseDirection = true, flingBehavior = snap, interactionSource = browse.drags)
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    val page = @Composable { index: Int ->
                        val focused = if (index == home) spot == NEXT_STOP else spot == cardOf(index, home)
                        val item = pageItem(index)
                        StopHero(
                            item,
                            when {
                                index == home -> HeroRole.NEXT
                                index < home -> HeroRole.EARLIER
                                else -> HeroRole.LATER
                            },
                            landscape,
                            focused = focused,
                            // A trip that is said lights up here while it is shown; the rest steps back.
                            dimmed = index != home && spot != NONE && !focused,
                            timeSize = clockSize * HERO_TIME_OF_CLOCK,
                            // The trip's word over its time: only the next stop is "Nästa", the one after it "Därefter".
                            label = if (index == home) stringResource(R.string.passenger_next) else wordOf(index),
                            onClick = if (index == home) onSpeakNext else { { onSay(sayingOf(item, index > home, wordOf(index))) } },
                            onLongClick = showWay?.let { { it(item, index == home) } },
                            // Only the next stop's name is shown (beside its figure) and said.
                            onName = if (index == home) item.lastName?.let { name -> { onName(name) } } else null,
                            onCard = item.card?.let { { onCard(item) } },
                            // Popping in and fading away inside its page (the pager's touches stay as they are).
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                val p = pop.value
                                alpha = p.coerceIn(0f, 1f)
                                val grown = POP_FROM + (1f - POP_FROM) * p
                                scaleX = grown
                                scaleY = grown
                            },
                        )
                    }
                    val pages = Modifier.weight(1f).fillMaxWidth()
                    if (landscape) {
                        HorizontalPager(pager, pages, userScrollEnabled = !browse.open) { page(it) }
                    } else {
                        VerticalPager(pager, pages, userScrollEnabled = !browse.open) { page(it) }
                    }
                    ThenLine(besideClock, line) {
                        BoxWithConstraints(Modifier.graphicsLayer { alpha = thenShown.value * (1f - lineBack()) }) {
                            // The next stop ("Nästa"), then "Därefter" and the trips after it, as
                            // many as the line has room for, up to four, each as wide; the light of
                            // the arrow flows on from the clock to the first.
                            val room = maxWidth - arrow.width - arrow.height * ARROW_SPACE * 2
                            val count = minOf(COMING_MOST, upcoming.size + 1, (room / COMING_MIN_WIDTH).toInt().coerceAtLeast(1))
                            val each = room / count
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NextArrow(visible = true, first = ARROW_COUNT, modifier = Modifier.ref(227).padding(horizontal = arrow.height * ARROW_SPACE).size(arrow))
                                ComingTrip(
                                    current,
                                    landscape,
                                    label = stringResource(R.string.passenger_next),
                                    tapped = false,
                                    status = current.time?.let { TimeStatus.of(TripTimes.minutesUntil(it, nowMinutes())) },
                                    onClick = onSpeakNext,
                                    onLongClick = showWay?.let { { it(current, true) } },
                                    onCard = current.card?.let { { onCard(current) } },
                                    modifier = Modifier.ref(NEXT_CHIP_REF).width(each).then(swipe),
                                )
                                for (k in 0 until count - 1) {
                                    val trip = upcoming[k]
                                    ComingTrip(
                                        trip,
                                        landscape,
                                        label = if (k == 0) stringResource(R.string.passenger_then) else null,
                                        tapped = spot == home + k,
                                        status = trip.time?.let { TimeStatus.of(TripTimes.minutesUntil(it, nowMinutes())) },
                                        onClick = { showCard(home + k) },
                                        onLongClick = showWay?.let { { it(trip, false) } },
                                        onCard = trip.card?.let { { onCard(trip) } },
                                        modifier = Modifier.ref(COMING_REFS[k]).width(each).then(swipe),
                                    )
                                }
                            }
                        }
                    }
                }
                // Out: the strip in the bottom line's place, the trip in its middle lit.
                AnimatedVisibility(
                    browse.open && latest,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(line),
                    enter = fadeIn(tween(BROWSE_IN_MS)) + slideInVertically(tween(BROWSE_IN_MS, easing = FastOutSlowInEasing)) { it / 3 },
                    exit = fadeOut(tween(BROWSE_OUT_MS)),
                ) {
                    TripStrip(
                        count = cardCount + 1,
                        item = pageItem,
                        home = home,
                        landscape = landscape,
                        list = list,
                        snap = snap,
                        middle = if (following) middle else home,
                        onPick = pick,
                        onLongPick = showWay?.let { { p: Int -> it(pageItem(p), p == home) } },
                        onCard = onCard,
                    )
                }
                // The dots saying which trip the top shows: small, floating at the very bottom, and
                // Home at their left while it is wanted.
                BesideDots(
                    Modifier.align(Alignment.BottomCenter).offset(y = below - DOTS_LOW),
                    dots = {
                        PageDots(
                            pager.currentPage,
                            pager.pageCount,
                            home,
                            Modifier.graphicsLayer { alpha = dotsShown }.ref(201, centered = true),
                        )
                    },
                    left = homeButton,
                )
            }
        }
    }
}

/** The card (the trips done, then the coming ones) of the stop on [page], whose next stop is page [home]. */
private fun cardOf(page: Int, home: Int) = if (page < home) page else page - 1

/**
 * What the screen points at while something is said or a tapped trip is shown: the next stop
 * ([NEXT_STOP]), a trip's card (its place in the strip) or nothing ([NONE]). A new [play]
 * replaces the one running.
 */
@Stable
private class Spotlight(private val scope: CoroutineScope) {
    var on by mutableIntStateOf(NONE)

    /** Something is being said or shown: the rest of the screen steps aside meanwhile. */
    var playing by mutableStateOf(false)
        private set
    private var job: Job? = null
    private var runs = 0

    fun play(steps: suspend Spotlight.() -> Unit) {
        job?.cancel()
        val run = ++runs
        on = NONE
        playing = true
        job = scope.launch {
            try {
                steps()
            } finally {
                // Only the last one played ends the showing (one replaced by another does not).
                if (run == runs) {
                    on = NONE
                    playing = false
                }
            }
        }
    }

    /** Whatever is being shown goes (Home). */
    fun stop() {
        runs++
        job?.cancel()
        on = NONE
        playing = false
    }
}

@Composable
private fun rememberSpotlight(): Spotlight {
    val scope = rememberCoroutineScope()
    return remember(scope) { Spotlight(scope) }
}

/**
 * The rest of the bottom line, [content] (the coming trips, "Därefter" first): right after the
 * clock's line ([besideClock] wide), in the width left beside it, level with its middle (it is
 * [line] tall).
 */
@Composable
private fun ThenLine(besideClock: Int, line: Dp, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val room = line.roundToPx()
        val width = constraints.maxWidth
        val p = measurables.firstOrNull()?.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = (width - besideClock).coerceAtLeast(0)))
        layout(width, room) { p?.place(besideClock, (room - (p.height)) / 2) }
    }
}

/**
 * Two small chevrons pointing right, in bright yellow, lighting up one after another, over and
 * over, so the eye flows from the clock to the next stop's time beside it, on to "Därefter" and
 * to the trip after it. The three arrows go by one light over their six chevrons: this one's are
 * chevrons [first] and [first] + 1.
 */
@Composable
private fun NextArrow(visible: Boolean, first: Int, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "arrow")
    // One light for all the arrows ([LocalArrowPhase]), read only as they are drawn.
    val phase = LocalArrowPhase.current
    val color = AppTheme.colors.accent
    Canvas(modifier.graphicsLayer { alpha = shown }) {
        val step = size.width / ARROW_COUNT
        val wide = minOf(step * 0.6f, size.height * 0.5f)
        val stroke = size.height * 0.12f
        for (i in 0 until ARROW_COUNT) {
            // A light running along the chevrons: each is brightest a little after the one before.
            val wave = 0.5f + 0.5f * cos(2.0 * Math.PI * (phase() - (first + i).toFloat() / (ARROW_LINES * ARROW_COUNT))).toFloat()
            val a = ARROW_LOW + (ARROW_HIGH - ARROW_LOW) * wave * wave * wave * wave
            val left = step * i + (step - wide) / 2f
            val path = Path().apply {
                moveTo(left, size.height * 0.12f)
                lineTo(left + wide, size.height / 2f)
                lineTo(left, size.height * 0.88f)
            }
            drawPath(path, color.copy(alpha = a), style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Where the light running along the arrows is (0 → 1), from one frame loop for all of them. */
private val LocalArrowPhase = staticCompositionLocalOf<() -> Float> { { 0f } }

/** An arrow of the bottom line, for a trip's time of [tripSize]: about as tall as its hours' digits. */
@Composable
private fun arrowSize(tripSize: TextUnit): DpSize = with(LocalDensity.current) {
    val tall = (tripSize * ARROW_TALL).toDp()
    DpSize(tall * ARROW_LONG, tall)
}

/**
 * Lays out a line of digits of [size] as tall as its ink only: the room its font keeps above and
 * below the digits hangs over, so the line can sit close to the screen's edge and to what is beside it.
 */
private fun Modifier.ink(size: TextUnit): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val em = size.toPx()
    val above = (em * (DIGIT_ASCENT - DIGIT_HEIGHT)).roundToInt()
    val below = (em * DIGIT_DESCENT).roundToInt()
    // Something smaller than a line of digits (the done marks alone) keeps its own size.
    if (placeable.height <= above + below) return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    layout(placeable.width, placeable.height - above - below) { placeable.place(0, -above) }
}

/** Lays this out just above its place, taking no height: a label over what follows it. */
private fun Modifier.hangAbove(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, 0) { placeable.place(0, -placeable.height) }
}

/**
 * The motion sign (297), always at the top line's far right: a short upright line that waves like a
 * snake as the car shakes (the tablet's accelerometer, [level] 0–1), so the sensor is seen working;
 * calm and grey once the car has stood still two minutes ([awake] false: the moments rest).
 */
@Composable
private fun MotionSign(level: () -> Float, awake: Boolean) {
    val phase = remember { mutableFloatStateOf(0f) }
    val shake = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { t ->
                val dt = if (last == 0L) 0L else t - last
                last = t
                // Smoothed, and the waves run faster the more it shakes.
                shake.floatValue += (level() - shake.floatValue) * 0.1f
                phase.floatValue = (phase.floatValue + dt / 1000f * (MOTION_SPEED_REST + MOTION_SPEED_SHAKE * shake.floatValue)) % 1f
            }
        }
    }
    val color = if (awake) AppTheme.colors.highlight else AppTheme.colors.textMuted
    val description = stringResource(if (awake) R.string.display_motion_on else R.string.display_motion_off)
    Canvas(
        Modifier
            .refCorner(297)
            .padding(start = 8.dp)
            .size(MOTION_WIDTH, MOTION_HEIGHT)
            .semantics { contentDescription = description },
    ) {
        val path = Path()
        val steps = 24
        val amp = MOTION_REST_AMP.toPx() + (size.width / 2f - MOTION_REST_AMP.toPx() - 2.dp.toPx()) * shake.floatValue
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val x = size.width / 2f + amp * sin(2f * PI.toFloat() * (MOTION_WAVES * f - phase.floatValue))
            val y = f * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * The connection, in the top-left corner, small and quiet (it is for the driver): a dot (green
 * while the phone is linked) and the phone's name; a tap offers to close the display ([onExit]).
 * On the driver's own device there is no connection: the system's Back closes the display.
 */
@Composable
private fun ConnectionSign(status: String, connected: Boolean, wide: Boolean, onExit: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    // A tap opens a small menu to close the display (there is no × of its own).
    Box(modifier) {
        var menu by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .ref(87)
                .heightIn(min = TouchTarget)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClickLabel = stringResource(R.string.display_exit), role = Role.Button) { menu = true }
                .padding(horizontal = 6.dp),
        ) {
            Box(Modifier.size(9.dp).background(if (connected) AppTheme.colors.success else AppTheme.colors.danger, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = if (wide) STATUS_MAX_WIDTH else STATUS_MAX_NARROW),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.display_exit), style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_stop), contentDescription = null) },
                onClick = {
                    menu = false
                    onExit()
                },
                modifier = Modifier.ref(86).heightIn(min = TouchTarget),
            )
        }
    }
}

/**
 * The signs in the top-right corner, small and quiet, each only its picture with no frame: the map
 * sign ([onMap]: the way to the next stop), the weather sign, which brings the weather up, the look
 * sign ([onToggleLook]: black or light), and the device's battery ([BatterySign]).
 */
@Composable
private fun TopLine(
    onMap: (() -> Unit)?,
    onMapPlaced: (Offset) -> Unit,
    weather: DisplayWeather?,
    hasWeather: Boolean,
    onWeather: () -> Unit,
    dark: Boolean,
    onToggleLook: (() -> Unit)?,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onMap != null) MapSign(onMap, enabled, onMapPlaced)
        if (hasWeather) WeatherSign(weather, onWeather, enabled)
        if (onToggleLook != null) {
            // The look it switches to: the sun for light, the moon for black.
            val description = stringResource(if (dark) R.string.display_look_light else R.string.display_look_dark)
            IconButton(onClick = onToggleLook, enabled = enabled, modifier = Modifier.refCorner(223).size(TouchTarget)) {
                Icon(painterResource(if (dark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode), contentDescription = description, tint = AppTheme.colors.textMuted, modifier = Modifier.size(TOP_ICON))
            }
        }
        BatterySign()
    }
}

/**
 * This device's battery (271): a small battery drawn as full as it is, and its percent; green while
 * it charges, red when it is low. From the system's sticky battery broadcast: no permission.
 */
@Composable
private fun BatterySign() {
    val context = LocalContext.current
    var level by remember { mutableStateOf<Int?>(null) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level = if (raw >= 0 && scale > 0) (raw * 100 / scale) else null
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = plugged == BatteryManager.BATTERY_STATUS_CHARGING || plugged == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)?.let { receiver.onReceive(context, it) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val percent = level ?: return
    val colors = AppTheme.colors
    val fill = when {
        charging -> colors.success
        percent <= BATTERY_LOW -> colors.danger
        else -> colors.textMuted
    }
    val description = stringResource(R.string.display_battery, percent)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .refCorner(271)
            .heightIn(min = TouchTarget)
            .padding(horizontal = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Canvas(Modifier.size(BATTERY_WIDTH, BATTERY_HEIGHT)) {
            val stroke = 1.5.dp.toPx()
            val cap = size.width * 0.1f
            val body = Size(size.width - cap, size.height)
            drawRoundRect(colors.textMuted, size = Size(body.width - stroke, body.height - stroke), topLeft = Offset(stroke / 2, stroke / 2), cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(stroke))
            drawRoundRect(colors.textMuted, topLeft = Offset(body.width, size.height * 0.3f), size = Size(cap, size.height * 0.4f), cornerRadius = CornerRadius(1.dp.toPx()))
            val inset = stroke * 2
            drawRoundRect(fill, topLeft = Offset(inset, inset), size = Size((body.width - 2 * inset) * percent / 100f, body.height - 2 * inset), cornerRadius = CornerRadius(1.5.dp.toPx()))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "$percent%",
            fontFamily = DigitFont,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = if (charging) colors.success else colors.textMuted,
        )
    }
}

/** The weather in small (the picture and the degrees; the picture alone for a weather app's widget): a tap brings it up large. */
@Composable
private fun WeatherSign(weather: DisplayWeather?, onClick: () -> Unit, enabled: Boolean) {
    val description = stringResource(R.string.display_weather)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .refCorner(222)
            .heightIn(min = TouchTarget)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 6.dp),
    ) {
        WeatherGlyph(weather?.kind ?: WeatherKind.PARTLY, AppTheme.colors.highlight, Modifier.size(TOP_ICON + 4.dp))
        if (weather != null) {
            Spacer(Modifier.width(3.dp))
            Text(
                "${weather.tempC}°",
                fontFamily = DigitFont,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = AppTheme.colors.textMuted,
            )
        }
    }
}

/** Which stop the top shows: one done, the next stop, or one coming after it. */
private enum class HeroRole { EARLIER, NEXT, LATER }

/**
 * A stop shown over the whole top (every address the display shows large moves the same way):
 * its time in the clock's style as high as it can stand, a little smaller than the clock
 * ([timeSize]), with the passenger's figure at its left and the town and area at its right
 * ([HeroLine]); right under it the street and number, at [TITLE_SHARE] of the size that would
 * fill the rest of the height (as many lines as it needs, a long word broken if it must). The time
 * and the address never move toward each other.
 *
 * On the next stop and on a trip said or tapped ([Stars], while it is [focused] or the next
 * stop, and not [dimmed]) the whole address becomes the star: it goes to the middle and grows to
 * nearly fill it, in yellow, a flash of light crossing it, while everything else shrinks and
 * fades; then back (the time's colon turns yellow meanwhile). Then the time is the star (five
 * times its size, in the middle), with the trip's word over it ([label]: "Nästa", "Därefter",
 * "Sen"), the town and area drawing away; back in place, the word comes and goes and swells
 * twice. A rest, and again. When it comes into view the time comes first, then the address. A tap says it ([onClick]). When it is [focused],
 * the address comes into view again ([entering]) and lights up; while something else is said,
 * it is [dimmed].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopHero(
    current: DisplayItem,
    role: HeroRole,
    landscape: Boolean,
    focused: Boolean,
    dimmed: Boolean,
    timeSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
    onName: (() -> Unit)? = null,
    onCard: (() -> Unit)? = null,
) {
    val next = role == HeroRole.NEXT
    // Focused, the address comes into view out of a soft blur with a sweep of light, then the area.
    val title = rememberEntrance(focused, current.trip, ENTER_TITLE_DELAY_MS)
    val area = rememberEntrance(focused, current.trip, ENTER_AREA_DELAY_MS)
    val shine = AppTheme.colors.text.copy(alpha = SHINE_ALPHA)
    val shade by animateFloatAsState(if (dimmed) DIM else 1f, tween(DIM_MS), label = "dim")
    val titleColor by animateColorAsState(if (focused) AppTheme.colors.highlight else AppTheme.colors.text, tween(FOCUS_MS / 2), label = "street")
    val swellColor = AppTheme.colors.swell
    val flash = AppTheme.colors.text.copy(alpha = FLASH_ALPHA)
    val stars = rememberStars(active = (next || focused) && !dimmed, restart = focused)
    val star by remember(stars) { derivedStateOf { stars.who() } }
    val drawn = LocalDensity.current
    val description = stringResource(if (next) R.string.display_repeat else R.string.display_say_trip)
    BoxWithConstraints(
        modifier
            .graphicsLayer { alpha = shade }
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClickLabel = description,
                role = Role.Button,
                onLongClickLabel = if (onLongClick != null) stringResource(R.string.display_show_way) else null,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        val style = TextStyle(
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            textDirection = TextDirection.Content,
            lineHeight = 1.0.em,
        )
        val measurer = rememberTextMeasurer()
        Layout(
            content = {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val room = with(LocalDensity.current) { (maxHeight * HERO_TIME_FILL).toSp() }
                    val wide = with(LocalDensity.current) { (maxWidth * HERO_TIME_WIDTH).toSp() }
                    // Over the address, on one line: the passenger's figure (a tap opens the trip's
                    // YouDrive card, [onCard]; without a card, on the next stop, it shows the last
                    // name beside it for a while, and a tap on the name says it and shows it large),
                    // the trip's time, a little smaller than the clock, and its town and area.
                    val size = minOf(room.value, wide.value / timeEms(current.time), timeSize.value).coerceAtLeast(HERO_TIME_MIN.value).sp
                    HeroLine(current, next, landscape, focused, size, area, shine, onName, onCard, stars = stars, label = label)
                }
                // The whole address, as one star in its turn.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val width = constraints.maxWidth
                    val roomPx = constraints.maxHeight
                    // A share of the size at which the address would fill the room under the time.
                    val titleSp = remember(current.title, width, roomPx) {
                        var size = MAX_TITLE_SP
                        while (size > MIN_TITLE_SP) {
                            val laid = measurer.measure(current.title, style.copy(fontSize = size.sp), constraints = Constraints(maxWidth = width))
                            if (laid.size.height <= roomPx) break
                            size -= 4f
                        }
                        (size * TITLE_SHARE).coerceAtLeast(MIN_TITLE_SP)
                    }
                    BasicText(
                        current.title,
                        style = style.copy(fontSize = titleSp.sp),
                        color = { if (stars.who() == ADDRESS) lerp(titleColor, swellColor, stars.p()) else titleColor },
                        // Its letters' feet may reach past its line.
                        overflow = TextOverflow.Visible,
                        modifier = Modifier
                            .ref(91)
                            .fillMaxWidth()
                            .entering(title, shine)
                            .star(stars, ADDRESS, flash = flash),
                    )
                }
            },
            modifier = Modifier.fillMaxSize().onPlaced { stars.hero = it },
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val loose = Constraints(maxWidth = w)
            val time = measurables[0].measure(loose.copy(maxHeight = constraints.maxHeight))
            val address = measurables[1].measure(loose.copy(maxHeight = (constraints.maxHeight - time.height).coerceAtLeast(0)))
            val who = star
            layout(w, constraints.maxHeight) {
                // The time as high as it can stand, the address right under it; the star over all.
                time.place((w - time.width) / 2, 0, zIndex = if (who == CLOCK) 1f else 0f)
                address.place((w - address.width) / 2, time.height, zIndex = if (who == ADDRESS) 1f else 0f)
            }
        }
    }
}

/**
 * The stars of a large address, in turn: the whole address ([ADDRESS]), then the time ([CLOCK]),
 * each [STAR_MS] (growing [STAR_GROW_MS], held while a flash of light crosses it, going back
 * [STAR_BACK_MS]); then the trip's word over the time ([LABEL], [LABEL_MS]); then a rest of
 * [STAR_REST_MS]; and again. Where each one stands is kept as it is placed, so the star can go to
 * the middle of the [hero].
 */
@Stable
private class Stars {
    val ms = mutableLongStateOf(0L)
    var running by mutableStateOf(false)
    var hero: LayoutCoordinates? = null
    val placed = arrayOfNulls<LayoutCoordinates>(2)

    private fun t(): Long = ms.longValue % (2 * STAR_MS + LABEL_MS + STAR_REST_MS)

    /** Which part shines now: [ADDRESS], [CLOCK], [LABEL], or [NO_STAR]. */
    fun who(): Int {
        if (!running) return NO_STAR
        val t = t()
        return when {
            t < 2 * STAR_MS -> (t / STAR_MS).toInt()
            t < 2 * STAR_MS + LABEL_MS -> LABEL
            else -> NO_STAR
        }
    }

    /** How far it has come: for a star 0 → 1 → 0 (grown, held, back); for [LABEL] 0 → 1 over its time. */
    fun p(): Float {
        if (!running) return 0f
        val t = t()
        if (t >= 2 * STAR_MS) return ((t - 2 * STAR_MS).toFloat() / LABEL_MS).coerceIn(0f, 1f)
        val at = t % STAR_MS
        return when {
            at < STAR_GROW_MS -> FastOutSlowInEasing.transform(at.toFloat() / STAR_GROW_MS)
            at < STAR_MS - STAR_BACK_MS -> 1f
            else -> 1f - FastOutSlowInEasing.transform((at - (STAR_MS - STAR_BACK_MS)).toFloat() / STAR_BACK_MS)
        }
    }

    /** Where the flash crossing the star is while it is held (0 → 1), else null. */
    fun flash(): Float? {
        if (!running) return null
        val t = t()
        if (t >= 2 * STAR_MS) return null
        val at = t % STAR_MS - STAR_GROW_MS
        val held = STAR_MS - STAR_GROW_MS - STAR_BACK_MS
        return if (at in 0 until held) at.toFloat() / held else null
    }

    /** How large star [key] grows, to stand in the middle of the [hero] (null when not placed yet). */
    fun grownTo(key: Int): Pair<Offset, Float>? {
        val hero = hero?.takeIf { it.isAttached } ?: return null
        val at = placed.getOrNull(key)?.takeIf { it.isAttached } ?: return null
        val box = hero.localBoundingBoxOf(at, clipBounds = false)
        if (box.width <= 0f || box.height <= 0f) return null
        val most = if (key == CLOCK) CLOCK_STAR else ADDRESS_STAR
        val fill = if (key == CLOCK) CLOCK_STAR_FILL else ADDRESS_STAR_FILL
        val scale = minOf(most, hero.size.width * fill / box.width, hero.size.height * fill / box.height).coerceAtLeast(1f)
        return Offset(hero.size.width / 2f - box.center.x, hero.size.height / 2f - box.center.y) to scale
    }
}

@Composable
private fun rememberStars(active: Boolean, restart: Boolean): Stars {
    val stars = remember { Stars() }
    LaunchedEffect(stars, active, restart) {
        stars.running = active
        stars.ms.longValue = 0L
        if (!active) return@LaunchedEffect
        // A short wait first: the time and the address have come into view.
        val start = withInfiniteAnimationFrameMillis { it } + STAR_FIRST_MS
        while (true) withInfiniteAnimationFrameMillis { stars.ms.longValue = (it - start).coerceAtLeast(0L) }
    }
    return stars
}

/**
 * Part [key] of a large address in [stars]' turn: as the star it goes to the middle and grows;
 * while another is the star it shrinks and fades ([away] also draws it aside, -1 left or 1
 * right, while the time is the star). A part that is never a star has [key] [NO_STAR].
 */
private fun Modifier.star(stars: Stars, key: Int, away: Float = 0f, flash: Color? = null): Modifier = this
    .onPlaced { if (key == ADDRESS || key == CLOCK) stars.placed[key] = it }
    .graphicsLayer {
        // Its own layer while a flash crosses it, so the light lands on its ink only.
        compositingStrategy = if (flash != null && stars.who() == key) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        val who = stars.who()
        if (who == NO_STAR || who == LABEL) return@graphicsLayer
        val p = stars.p()
        if (who == key) {
            val (move, scale) = stars.grownTo(key) ?: return@graphicsLayer
            translationX = move.x * p
            translationY = move.y * p
            val s = 1f + (scale - 1f) * p
            scaleX = s
            scaleY = s
        } else {
            val s = 1f - STAR_OTHERS_SHRINK * p
            scaleX = s
            scaleY = s
            alpha = 1f - STAR_OTHERS_FADE * p
            if (who == CLOCK) translationX = away * STAR_AWAY.toPx() * p
        }
    }
    .then(
        if (flash == null) {
            Modifier
        } else {
            Modifier.drawWithContent {
                drawContent()
                val at = if (stars.who() == key) stars.flash() else null
                if (at != null) {
                    val band = size.width * FLASH_BAND
                    val x = -band + (size.width + 2f * band) * at
                    drawRect(
                        Brush.linearGradient(listOf(Color.Transparent, flash, Color.Transparent), start = Offset(x - band / 2f, 0f), end = Offset(x + band / 2f, size.height)),
                        blendMode = BlendMode.SrcAtop,
                    )
                }
            }
        },
    )

/** How wide a trip's [time] is in the clock's style, in ems of its minutes' size. */
private fun timeEms(time: String?): Float {
    val hour = time?.substringBefore(':').orEmpty()
    // A digit is about 0.6 em; the colon and the room beside it about 0.5 em of the hours' size.
    return (hour.length * HOUR_SHARE + HOUR_SHARE * 0.5f + 2f) * 0.62f
}

/**
 * The line over a stop's address: the passenger's figure (231 on the next stop, 234 on another)
 * and, on the next stop without a card, its last name for a while (236); the trip's time (230) in
 * the clock's style ([TimeFace]: the hours smaller, the colon's dots, the minutes large; no
 * seconds) at [timeSize]; and its town and area (92). In [stars]' turn the time is a star with
 * the trip's word ([label]) over it, its colon turns yellow while the address is the
 * star, and the figure, the town and the area shrink and draw aside. The figure and the name step
 * aside while the stop is [focused]. The time is only here: never again on the same page.
 */
@Composable
private fun HeroLine(
    current: DisplayItem,
    next: Boolean,
    landscape: Boolean,
    focused: Boolean,
    timeSize: TextUnit,
    area: Entrance,
    shine: Color,
    onName: (() -> Unit)?,
    onCard: (() -> Unit)?,
    stars: Stars? = null,
    label: String? = null,
) {
    val lineSp = if (landscape) HERO_LINE_SP else HERO_LINE_SP_NARROW
    val swell = AppTheme.colors.swell
    val accent = AppTheme.colors.accent
    val name = current.lastName?.takeIf { onName != null }
    val signShown by animateFloatAsState(if (focused) 0f else 1f, tween(CLOCK_FADE_MS), label = "name")
    var open by remember(current.trip) { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        delay(NAME_OPEN_MS)
        open = false
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp).entering(area, shine),
    ) {
        if (onCard != null || name != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .ref(if (next) 231 else 234, centered = true)
                    .then(if (stars != null) Modifier.star(stars, NO_STAR, away = -1f) else Modifier)
                    .graphicsLayer { alpha = signShown }
                    .size(TouchTarget)
                    .clip(CircleShape)
                    .clickable(
                        enabled = !focused,
                        onClickLabel = stringResource(if (onCard != null) R.string.display_show_card else R.string.display_show_name),
                        role = Role.Button,
                    ) { if (onCard != null) onCard() else open = !open },
            ) {
                PersonGlyph(AppTheme.colors.highlight, Modifier.size(with(LocalDensity.current) { (lineSp * PERSON_SIZE * 1.4f).toDp() }))
            }
            if (name != null) AnimatedVisibility(
                open,
                enter = fadeIn(tween(NAME_IN_MS)) + expandHorizontally(tween(NAME_IN_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
                exit = fadeOut(tween(NAME_OUT_MS)) + shrinkHorizontally(tween(NAME_OUT_MS), shrinkTowards = Alignment.Start),
            ) {
                Text(
                    name,
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = lineSp,
                    color = AppTheme.colors.highlight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content),
                    modifier = Modifier
                        .ref(236)
                        .graphicsLayer { alpha = signShown }
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !focused, onClickLabel = stringResource(R.string.display_say_name), role = Role.Button, onClick = { onName?.invoke() })
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        // Where the trip was marked done (in YouDrive, or here), small before its time.
        if (current.doneInYouDrive || current.doneHere) {
            DoneMarks(youDrive = current.doneInYouDrive, here = current.doneHere, size = 10.dp)
            Spacer(Modifier.width(6.dp))
        }
        if (current.time != null) {
            if (stars == null) {
                TimeFace(current.time, timeSize, Modifier.ref(230), breathing = false)
            } else {
                val addressShines = { stars.who() == ADDRESS }
                val flash = AppTheme.colors.text.copy(alpha = FLASH_ALPHA)
                Column(Modifier.zIndex(if (stars.who() == CLOCK) 1f else 0f).star(stars, CLOCK, flash = flash), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (label != null) TimeWord(label, lineSp, stars)
                    TimeFace(
                        current.time,
                        timeSize,
                        Modifier.ref(230),
                        breathing = false,
                        colon = { if (addressShines()) lerp(accent, swell, stars.p()) else accent },
                    )
                }
            }
        }
        current.subtitle?.let {
            Spacer(Modifier.width(14.dp))
            Text(
                it,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = lineSp,
                color = AppTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Content),
                modifier = Modifier
                    .ref(92)
                    .weight(1f, fill = false)
                    .then(if (stars != null) Modifier.star(stars, NO_STAR, away = 1f) else Modifier),
            )
        }
    }
}

/**
 * The trip's word ("Nästa", "Därefter") over its time, in the accent yellow: it grows with the
 * time while the time is the star; once the time is back in place it comes, swells twice and
 * goes. Its line stays, empty, the rest of the time.
 */
@Composable
private fun TimeWord(word: String, size: TextUnit, stars: Stars) {
    Text(
        word.uppercase(),
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = size * TIME_WORD_SHARE,
        letterSpacing = 2.sp,
        color = AppTheme.colors.accent,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .graphicsLayer {
                val who = stars.who()
                val p = stars.p()
                when (who) {
                    CLOCK -> alpha = p
                    LABEL -> {
                        alpha = sin(PI * p).toFloat().coerceIn(0f, 1f)
                        val beat = sin(2 * PI * p).toFloat()
                        val s = 1f + TIME_WORD_SWELL * beat * beat
                        scaleX = s
                        scaleY = s
                    }
                    else -> alpha = 0f
                }
            },
    )
}

/**
 * How what the display focuses on comes into view: [appear] 0 → 1 (out of a soft blur, rising a
 * little and settling to its size, drawn at its own size so it stays sharp), then [shine] 0 → 1 (a
 * band of light sweeping once across it).
 */
@Stable
private class Entrance(start: Float) {
    val appear = Animatable(start)
    val shine = Animatable(1f)

    suspend fun play(delayMs: Long) {
        appear.snapTo(0f)
        shine.snapTo(0f)
        delay(delayMs)
        coroutineScope {
            launch { appear.animateTo(1f, tween(APPEAR_MS, easing = FastOutSlowInEasing)) }
            delay(SHINE_AFTER_MS)
            shine.animateTo(1f, tween(SHINE_MS, easing = FastOutSlowInEasing))
        }
    }
}

/** An [Entrance] that plays, after [delayMs], each time [active] turns on or [key] changes while it is on. */
@Composable
private fun rememberEntrance(active: Boolean, key: Any?, delayMs: Long): Entrance {
    val entrance = remember { Entrance(if (active) 0f else 1f) }
    LaunchedEffect(active, key) {
        if (active) {
            entrance.play(delayMs)
        } else {
            entrance.appear.snapTo(1f)
            entrance.shine.snapTo(1f)
        }
    }
    return entrance
}

/** Draws this as [entrance] brings it in, with the sweep of light in [shine] over its own ink only. */
private fun Modifier.entering(entrance: Entrance, shine: Color): Modifier = this
    .graphicsLayer {
        val p = entrance.appear.value
        alpha = (p * ENTER_ALPHA_SPEED).coerceAtMost(1f)
        translationY = (1f - p) * ENTER_RISE.toPx()
        val s = ENTER_SCALE + (1f - ENTER_SCALE) * p
        scaleX = s
        scaleY = s
        val blur = (1f - p) * ENTER_BLUR.toPx()
        renderEffect = if (blur >= 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(blur, blur, TileMode.Decal) else null
        // Its own layer while the light sweeps, so it lands on the letters and not around them;
        // otherwise none, so a star can grow out of it.
        val sweeping = entrance.shine.value in 0f..0.999f || p < 1f
        compositingStrategy = if (sweeping) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithContent {
        drawContent()
        val t = entrance.shine.value
        if (t > 0f && t < 1f) {
            val band = size.width * SHINE_BAND
            val x = -band + (size.width + 2f * band) * t
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, shine, Color.Transparent),
                    start = Offset(x - band / 2f, 0f),
                    end = Offset(x + band / 2f, size.height * 0.5f),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }

@Composable
internal fun ThenLabel(modifier: Modifier = Modifier, text: String = stringResource(R.string.passenger_then), color: Color = AppTheme.colors.textMuted, size: TextUnit = 13.sp) {
    Text(
        text.uppercase(),
        fontFamily = DisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = size,
        letterSpacing = 1.5.sp,
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * A coming trip on the bottom line, with no frame: its street and number (cut short where it would
 * take more room), its time under it, larger and in the highlight colour, and how it stands under
 * that ([status]: a short bar in the clock colon's colours; the done marks where it was marked
 * done). The next stop and "Därefter" have their word ([label]) floating just above them. It
 * swells while the announcement says it or after a tap ([tapped]), which says it and shows it at
 * the top ([onClick]). A long press asks for the way to it ([onLongClick]); its figure opens its
 * YouDrive card ([onCard]).
 */
@Composable
private fun ComingTrip(
    item: DisplayItem,
    landscape: Boolean,
    label: String?,
    tapped: Boolean,
    status: TimeStatus?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onCard: (() -> Unit)? = null,
) {
    val size = if (landscape) COMING_SP else COMING_SP_NARROW
    val swell by animateFloatAsState(if (tapped) CARD_TAP_SWELL else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "swell")
    val description = stringResource(R.string.display_say_trip)
    Column(
        modifier
            .graphicsLayer {
                scaleX = swell
                scaleY = swell
                // It grows up from the screen's bottom.
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClickLabel = description,
                role = Role.Button,
                onLongClickLabel = if (onLongClick != null) stringResource(R.string.display_show_way) else null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        if (label != null) ThenLabel(modifier = Modifier.hangAbove().padding(bottom = 1.dp), text = label, size = THEN_LABEL_SP)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.title,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = size,
                color = AppTheme.colors.text,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Content),
                modifier = Modifier.weight(1f, fill = false),
            )
            // The passenger's figure: a tap opens the trip's YouDrive card.
            if (onCard != null) PersonSign(onCard, size, AppTheme.colors.textMuted, box = SMALL_TOUCH)
        }
        if (item.time != null) {
            Text(
                item.time,
                fontFamily = DigitFont,
                fontWeight = FontWeight.Bold,
                fontSize = size * COMING_TIME_SHARE,
                color = AppTheme.colors.highlight,
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
            if (status != null) {
                Box(Modifier.size(COMING_BAR_WIDTH, COMING_BAR_HEIGHT).clip(CircleShape).background(statusColor(status)))
            }
            if (item.doneInYouDrive || item.doneHere) {
                Spacer(Modifier.width(6.dp))
                DoneMarks(youDrive = item.doneInYouDrive, here = item.doneHere, size = 8.dp)
            }
        }
    }
}

/** The small outlined figure (234) beside a trip: a tap opens the trip's YouDrive card. */
@Composable
private fun PersonSign(onClick: () -> Unit, textSize: TextUnit, color: Color, box: Dp = TouchTarget) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .ref(234, centered = true)
            .size(box)
            .clip(CircleShape)
            .clickable(onClickLabel = stringResource(R.string.display_show_card), role = Role.Button, onClick = onClick),
    ) {
        PersonGlyph(color, Modifier.size(with(LocalDensity.current) { (textSize * PERSON_SIZE * 1.6f).toDp() }))
    }
}

/**
 * All the trips, the ones done and the coming ones, side by side in the bottom line's place while
 * the passengers look through them ([Browse]); [count] of them, the next stop at [home]. Each is as
 * wide, and the strip can bring any of them to its middle, where it settles; the trip in the
 * [middle] is lit (and shown at the top). Each shows its time, its street and number and its area;
 * the next stop and "Därefter" are marked, the trips done are quieter. A tap on one says it and
 * shows it ([onPick]); a long press asks for the way to it ([onLongPick]).
 */
@Composable
private fun TripStrip(
    count: Int,
    item: (Int) -> DisplayItem,
    home: Int,
    landscape: Boolean,
    list: LazyListState,
    snap: FlingBehavior,
    middle: Int?,
    onPick: (Int) -> Unit,
    onLongPick: ((Int) -> Unit)?,
    onCard: (DisplayItem) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth * if (landscape) STRIP_TRIP_SHARE else STRIP_TRIP_SHARE_NARROW
        LazyRow(
            state = list,
            flingBehavior = snap,
            // Room at both ends, so the first and the last trip can stand in the middle too.
            contentPadding = PaddingValues(horizontal = (maxWidth - width) / 2),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count) { p ->
                StripTrip(
                    item(p),
                    label = when (p) {
                        home -> stringResource(R.string.passenger_next_stop)
                        home + 1 -> stringResource(R.string.passenger_then)
                        else -> null
                    },
                    next = p == home,
                    done = p < home,
                    lit = p == middle,
                    onClick = { onPick(p) },
                    onLongClick = onLongPick?.let { { it(p) } },
                    onCard = item(p).card?.let { { onCard(item(p)) } },
                    modifier = Modifier.ref(226).width(width),
                )
            }
        }
    }
}

/** A trip in the strip ([TripStrip]): its [label] (next stop, "Därefter") over its time, street and area; [lit] in the middle. */
@Composable
private fun StripTrip(
    item: DisplayItem,
    label: String?,
    next: Boolean,
    done: Boolean,
    lit: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onCard: (() -> Unit)? = null,
) {
    val description = stringResource(R.string.display_say_trip)
    val grow by animateFloatAsState(if (lit) STRIP_LIT_SCALE else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "lit")
    val street by animateColorAsState(if (lit) AppTheme.colors.highlight else AppTheme.colors.text, tween(300), label = "street")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
                alpha = if (done && !lit) DONE_CARD_ALPHA else 1f
            }
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClickLabel = description,
                role = Role.Button,
                onLongClickLabel = if (onLongClick != null) stringResource(R.string.display_show_way) else null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        // Every trip keeps the label's line, so their times stand level.
        ThenLabel(text = label ?: " ", color = if (next) AppTheme.colors.highlight else AppTheme.colors.textMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.time != null) {
                Text(
                    item.time,
                    fontFamily = DigitFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = STRIP_TIME_SP,
                    color = if (next || lit) AppTheme.colors.highlight else AppTheme.colors.time,
                )
            }
            if (item.doneInYouDrive || item.doneHere) {
                Spacer(Modifier.width(8.dp))
                DoneMarks(youDrive = item.doneInYouDrive, here = item.doneHere, size = 12.dp)
            }
            // The passenger's figure: a tap opens the trip's YouDrive card.
            if (onCard != null) PersonSign(onCard, STRIP_TIME_SP, AppTheme.colors.textMuted, box = SMALL_TOUCH)
        }
        Text(
            item.title,
            fontFamily = DisplayFont,
            fontWeight = if (next) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = STRIP_STREET_SP,
            color = street,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(textDirection = TextDirection.Content),
        )
        item.subtitle?.let {
            Text(
                it,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Medium,
                fontSize = STRIP_AREA_SP,
                color = AppTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Content),
            )
        }
    }
}

/**
 * Where the passengers are among the stops: the one shown is a blue bar; the next stop ([home])
 * is a larger dot when they are elsewhere.
 */
@Composable
private fun PageDots(current: Int, count: Int, home: Int, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 18.dp else if (i == home) 9.dp else 6.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "dot")
            val tall = if (i == home && i != current) 9.dp else 6.dp
            Box(Modifier.size(width, tall).clip(CircleShape).background(if (i == current || i == home) AppTheme.colors.highlight else AppTheme.colors.outline))
        }
    }
}

/** [dots] in the middle, and [left] just at their left, level with them. */
@Composable
private fun BesideDots(modifier: Modifier, dots: @Composable () -> Unit, left: @Composable () -> Unit) {
    Layout(
        content = {
            Box { dots() }
            Box { left() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val d = measurables[0].measure(loose)
        val l = measurables[1].measure(loose)
        val gap = HOME_GAP.roundToPx()
        val height = maxOf(d.height, l.height)
        // As wide as the dots on each side of their middle, so they stay in the middle.
        val half = maxOf(d.width / 2, d.width / 2 + gap + l.width)
        layout(half * 2, height) {
            d.place(half - d.width / 2, (height - d.height) / 2)
            l.place(half - d.width / 2 - gap - l.width, (height - l.height) / 2)
        }
    }
}

/**
 * Back to the next stop, while the display shows anything else (another trip, the strip, a trip
 * said): a small house in a blue circle, at the left of the dots, beating gently so it is found;
 * gone once it is pressed.
 */
@Composable
private fun HomeButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible,
        modifier,
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = scaleOut(tween(200)) + fadeOut(tween(200)),
    ) {
        val description = stringResource(R.string.display_home)
        val beat = rememberInfiniteTransition(label = "home")
        val scale by beat.animateFloat(1f, HOME_BEAT, infiniteRepeatable(tween(HOME_BEAT_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beat")
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .refCorner(200)
                .size(HOME_TOUCH)
                .clip(CircleShape)
                .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(AppTheme.effects.currentShadow, CircleShape)
                    .size(HOME_SIZE)
                    .clip(CircleShape)
                    .background(AppTheme.colors.highlight),
            ) {
                Icon(painterResource(R.drawable.ic_home), contentDescription = null, tint = AppTheme.colors.onInfo, modifier = Modifier.size(HOME_ICON))
            }
        }
    }
}

/**
 * The clock, without a frame: the hours medium and level with the middle of the minutes, the
 * colon a step apart from both, the minutes large, and the seconds small and thin under the
 * minutes, in the highlight colour, each digit rolling into place ([RollingDigits]); they take no
 * room in the line.
 * The colon tells how the next stop's time stands ([status]; see [Colon]).
 * When the minute changes ([grow] 0 → 1), the time (hours, colon, minutes and seconds, as one)
 * moves to the middle of the screen and grows to five times its size, or as far as the screen
 * allows, turning to this minute's colour ([hue]). A tap says the time ([onClick]; none while the
 * clock is out of sight).
 */
@Composable
private fun Clock(
    now: State<LocalTime>,
    minuteSize: TextUnit,
    grow: () -> Float,
    hue: Color,
    status: TimeStatus?,
    screen: () -> Rect,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val time = now.value
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val description = stringResource(R.string.display_say_time)
    val ink = AppTheme.colors.text
    val highlight = AppTheme.colors.highlight
    // The hours and minutes change seldom: their digits keep their own widths (a "1" is narrow).
    val style = TextStyle(
        fontFamily = DigitFont,
        fontWeight = FontWeight.Bold,
        color = ink,
        lineHeight = 1.0.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    // Each line of digits sits in a box as tall as the font's ascent and descent: the hours (and
    // colon) drop so their middle is level with the minutes' middle, the seconds so they stand on
    // the minutes' baseline.
    val (hourDrop, secondsTop) = with(LocalDensity.current) {
        (minuteSize * ((1f - HOUR_SHARE) * (DIGIT_ASCENT - DIGIT_HEIGHT / 2f))).toDp() to
            // The seconds' digits start a step under the minutes' baseline.
            (minuteSize * (DIGIT_ASCENT + SECONDS_DROP - SECOND_SHARE * (DIGIT_ASCENT - DIGIT_HEIGHT))).roundToPx()
    }
    var hours by remember { mutableStateOf(Rect.Zero) }
    var dots by remember { mutableStateOf(Rect.Zero) }
    var minutes by remember { mutableStateOf(Rect.Zero) }
    var seconds by remember { mutableStateOf(Rect.Zero) }
    val whole = { span(span(hours, minutes), seconds) }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(interactionSource = null, indication = null, onClickLabel = description, role = Role.Button) {
                        scope.launch {
                            bounce.animateTo(1.08f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
                            bounce.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
                        }
                        onClick()
                    }
                },
            ),
    ) {
        BasicText(
            twoDigits(time.hour),
            style = style.copy(fontSize = minuteSize * HOUR_SHARE),
            color = { lerp(ink, hue, grow()) },
            modifier = Modifier
                .offset(y = hourDrop)
                .onGloballyPositioned { hours = it.boundsInRoot() }
                .growTogether(grow, own = { hours }, group = whole, area = screen),
        )
        Colon(
            minuteSize * HOUR_SHARE,
            status,
            second = time.second,
            modifier = Modifier
                .offset(y = hourDrop)
                .onGloballyPositioned { dots = it.boundsInRoot() }
                .growTogether(grow, own = { dots }, group = whole, area = screen),
        )
        Box {
            Box(
                Modifier
                    .onGloballyPositioned { minutes = it.boundsInRoot() }
                    .growTogether(grow, own = { minutes }, group = whole, area = screen),
            ) {
                AnimatedContent(
                    targetState = twoDigits(time.minute),
                    transitionSpec = { slideInVertically(tween(450)) { -it } + fadeIn(tween(450)) togetherWith slideOutVertically(tween(450)) { it } + fadeOut(tween(300)) },
                    label = "minutes",
                ) { m ->
                    BasicText(m, style = style.copy(fontSize = minuteSize), color = { lerp(ink, hue, grow()) })
                }
            }
            // The seconds, small under the minutes, taking no room in the line.
            RollingDigits(
                twoDigits(time.second),
                style = style.copy(fontSize = minuteSize * SECOND_SHARE, fontWeight = FontWeight.Light, fontFeatureSettings = TABULAR, letterSpacing = 0.04.em),
                color = { lerp(highlight, hue, grow()) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(placeable.width, 0) { placeable.place(0, secondsTop) }
                    }
                    .onGloballyPositioned { seconds = it.boundsInRoot() }
                    .growTogether(grow, own = { seconds }, group = whole, area = screen),
            )
        }
    }
}

/**
 * [text] whose digits roll into place one by one as they change, like a counter's: the new one
 * comes up from below while the old one goes on up and out, each in its own slot.
 */
@Composable
private fun RollingDigits(text: String, style: TextStyle, color: ColorProducer, modifier: Modifier = Modifier) {
    Row(modifier) {
        text.forEachIndexed { i, c ->
            key(i) {
                AnimatedContent(
                    targetState = c,
                    transitionSpec = {
                        (slideInVertically(tween(ROLL_MS, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(ROLL_MS)))
                            .togetherWith(slideOutVertically(tween(ROLL_MS, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(ROLL_MS / 2)))
                            .using(SizeTransform(clip = true))
                    },
                    label = "digit",
                ) { d ->
                    BasicText(d.toString(), style = style, color = color)
                }
            }
        }
    }
}

/** The smallest rectangle around both. */
private fun span(a: Rect, b: Rect) = Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))

/**
 * The colon between hours and minutes, drawn: two round dots exactly one above the other, level
 * with the middle of the digits and set a step apart from them on both sides. It tells how the
 * next stop's time stands: green on time, orange within five minutes, red once it has passed,
 * beating fast when it is due or well past ([TimeStatus.beating]); yellow without a route.
 * Otherwise it blinks with the [second]. It is as tall as a line of digits of [digitSize].
 */
@Composable
private fun Colon(digitSize: TextUnit, status: TimeStatus?, second: Int?, modifier: Modifier = Modifier, tint: (() -> Color)? = null) {
    val color = status?.let { statusColor(it) } ?: AppTheme.colors.accent
    // The beat runs only while it beats: a still colon asks for no frames.
    val pulse = if (status?.beating == true) {
        rememberInfiniteTransition(label = "beat").animateFloat(1f, BEAT_LOW, infiniteRepeatable(tween(BEAT_MS), RepeatMode.Reverse), label = "beat")
    } else {
        null
    }
    val blink by animateFloatAsState(if (second == null || second % 2 == 0) 1f else COLON_LOW, tween(450), label = "colon")
    val (wide, tall) = with(LocalDensity.current) { (digitSize * (COLON_DOT + 2 * COLON_SIDE)).toDp() to (digitSize * (DIGIT_ASCENT + DIGIT_DESCENT)).toDp() }
    Canvas(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height, mapOf(ColonLine to placeable.width / 2)) { placeable.place(0, 0) }
            }
            .size(wide, tall),
    ) {
        val em = digitSize.toPx()
        val middle = em * (DIGIT_ASCENT - DIGIT_HEIGHT / 2f)
        val apart = em * COLON_SPREAD / 2f
        val dot = (tint?.invoke() ?: color).copy(alpha = pulse?.value ?: blink)
        drawCircle(dot, radius = em * COLON_DOT / 2f, center = Offset(size.width / 2f, middle - apart))
        drawCircle(dot, radius = em * COLON_DOT / 2f, center = Offset(size.width / 2f, middle + apart))
    }
}

/** Where a colon's middle is: a trip's time breathes around it. */
private val ColonLine = VerticalAlignmentLine(::min)

@Composable
internal fun statusColor(status: TimeStatus): Color = when (status) {
    TimeStatus.ON_TIME -> AppTheme.colors.success
    TimeStatus.SOON, TimeStatus.DUE -> AppTheme.colors.soon
    TimeStatus.LATE, TimeStatus.VERY_LATE -> AppTheme.colors.danger
}

/**
 * A trip's time ("08:00") in the clock's style: the hours medium and level with the middle of the
 * minutes, the colon in the accent yellow a step apart from both; the digits breathe slowly around
 * the colon unless it is still ([breathing] off). The minutes alone can swell ([minutes], drawn
 * larger from their left side, over nothing).
 */
@Composable
internal fun TimeFace(time: String, minuteSize: TextUnit, modifier: Modifier = Modifier, breathing: Boolean = true, minutes: (() -> Float)? = null, colon: (() -> Color)? = null) {
    val hour = time.substringBefore(':')
    val minute = time.substringAfter(':', "")
    // The breath runs only while it breathes: a still time asks for no frames.
    val scale = if (breathing) {
        rememberInfiniteTransition(label = "breath").animateFloat(1f / BREATH_SCALE, 1f, infiniteRepeatable(tween(BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath scale")
    } else {
        null
    }
    // Drawn at its largest and scaled down as one picture: the digits grow and shrink smoothly
    // (text scaled directly jumps between the sizes its glyphs are drawn at).
    val big = minuteSize * BREATH_SCALE
    val ink = AppTheme.colors.text
    val style = TextStyle(
        fontFamily = DigitFont,
        fontWeight = FontWeight.Bold,
        color = ink,
        lineHeight = 1.0.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    // Still, it is simply drawn at its size.
    val size = if (breathing) big else minuteSize
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (scale != null) modifier.breathe(rest = 1f / BREATH_SCALE) { scale.value } else modifier,
    ) {
        Text(hour, style = style.copy(fontSize = size * HOUR_SHARE))
        Colon(size * HOUR_SHARE, status = null, second = null, tint = colon)
        Text(
            minute,
            style = style.copy(fontSize = size),
            // Its digits' edge may reach past their box; never cut.
            overflow = TextOverflow.Visible,
            softWrap = false,
            modifier = if (minutes == null) Modifier else Modifier.graphicsLayer {
                val s = minutes()
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        )
    }
}

/**
 * Draws the content scaled by [scale] (at most 1) around its colon ([ColonLine], else its middle),
 * so the colon stays still, in a layer of its own so it is scaled as a picture. It takes the width
 * of its largest (nothing beside it is ever covered) and the height of it at [rest].
 */
private fun Modifier.breathe(rest: Float, scale: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity))
    // As wide as it is at its largest, so it never grows over what stands beside it; as tall as at rest.
    val w = placeable.width
    val h = (placeable.height * rest).roundToInt()
    val colon = placeable[ColonLine].takeIf { it != AlignmentLine.Unspecified } ?: (placeable.width / 2)
    val around = colon.toFloat() / placeable.width
    val x = 0
    layout(w, h) {
        placeable.placeWithLayer(x, (h - placeable.height) / 2) {
            val s = scale()
            scaleX = s
            scaleY = s
            transformOrigin = TransformOrigin(around, 0.5f)
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }
}

/**
 * A trip's whole YouDrive card (235), in the order of YouDrive's own details window
 * ([TripCardText]), small (about a quarter of the screen) and drawn to be read at a glance: a band
 * in the trip's colour (green pick-up, light drop-off) with its kind and time and its status; the
 * passenger's name beside a figure, the two times beside a clock; then each field with its own
 * sign (the address, the phone numbers large in the highlight colour, one under the other, seats
 * and mobility aids as chips, fare, compensation, eligibility), and the instructions in a box of
 * their own, a line for each ([CardNotes]). Over everything, for the driver, until a tap beside it
 * or its ×; a long card scrolls. It is a window ([place]): its band moves it (261), its border
 * makes it bigger or smaller from any edge or corner (262), two fingers do both, and it opens where
 * and as big as the driver last left it. Never said. Numbers in [DigitFont], words in [DisplayFont].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripCard(item: DisplayItem, landscape: Boolean, place: WindowState, onClose: () -> Unit) {
    val card = remember(item.card) { TripCardText.of(item.card.orEmpty()) }
    val colors = AppTheme.colors
    val size = if (landscape) CARD_SP else CARD_SP_NARROW
    val words = TextStyle(fontFamily = DisplayFont, color = colors.text, fontSize = size, lineHeight = 1.3.em, textDirection = TextDirection.Content)
    val shape = RoundedCornerShape(CARD_CORNER)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Beside the card: a tap closes it.
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background.copy(alpha = CARD_SCRIM))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
        val width = maxWidth * if (landscape) CARD_WIDTH else CARD_WIDTH_NARROW
        val height = maxHeight * CARD_HEIGHT
        FloatingWindow(place, edgeRef = 262) {
            Column(
                Modifier
                    .ref(235)
                    .width(width)
                    .heightIn(max = height)
                    .shadow(CARD_SHADOW, shape)
                    .clip(shape)
                    .background(colors.card)
                    .border(1.dp, colors.cardBorder, shape)
                    // A tap on the card is the driver reading it: it stays.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                // The band: the trip's kind and time on its own colour, and its status. A finger on it
                // moves the card.
                val move = stringResource(R.string.window_move)
                val band = colors.trip(item.kind ?: card.kind?.let { TripKinds.labelIn(it) })
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .refCorner(261)
                        .fillMaxWidth()
                        .background(band)
                        .movesWindow(place)
                        .semantics { contentDescription = move }
                        .padding(start = CARD_PAD, end = 6.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Text(
                        withDigitFont(card.title ?: item.time.orEmpty()),
                        style = words.copy(color = colors.onTrip, fontWeight = FontWeight.Bold, fontSize = size * CARD_TITLE),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    card.status?.let {
                        Text(
                            it,
                            style = words.copy(color = colors.onStatus, fontWeight = FontWeight.SemiBold, fontSize = size * CARD_SMALL),
                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(colors.success).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    val button = with(LocalDensity.current) { (size * CARD_BUTTON).toDp() }
                    Spacer(Modifier.width(4.dp))
                    val close = stringResource(R.string.display_card_close)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .refCorner(264)
                            .padding(start = 4.dp)
                            .size(button)
                            .clip(CircleShape)
                            .background(colors.onTrip.copy(alpha = CARD_BUTTON_GROUND))
                            .clickable(onClickLabel = close, role = Role.Button, onClick = onClose)
                            .semantics { contentDescription = close },
                    ) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = colors.onTrip, modifier = Modifier.size(button * 0.6f))
                    }
                }
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = CARD_PAD, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(CARD_GAP),
                ) {
                    card.name?.let { name ->
                        CardLine(R.drawable.ic_person, size) {
                            Text(name, style = words.copy(fontWeight = FontWeight.SemiBold, fontSize = size * CARD_NAME))
                        }
                    }
                    if (card.estimated != null || card.negotiated != null) {
                        CardLine(R.drawable.ic_schedule, size) {
                            Column {
                                card.estimated?.let { Text(withDigitFont("Estimated time $it"), style = words) }
                                card.negotiated?.let { Text(withDigitFont("${card.secondLabel}: $it"), style = words.copy(color = colors.textMuted)) }
                            }
                        }
                    }
                    // The phone numbers under one sign, each on a line of its own.
                    val phones = card.rows.filter { it.label == TripCardText.PHONE }
                    var phonesShown = false
                    for (row in card.rows) {
                        when (row.label) {
                            TripCardText.PHONE -> if (!phonesShown) {
                                phonesShown = true
                                CardLine(R.drawable.ic_phone, size, row.label) {
                                    phones.forEach { PhoneLine(it.value, null, words) }
                                }
                            }
                            TripCardText.INSTRUCTIONS -> CardNote(row.value, words)
                            TripCardText.SPACE, TripCardText.AIDS -> CardLine(cardIcon(row.label), size, row.label) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    row.value.split(", ").forEach { chip ->
                                        Text(
                                            withDigitFont(chip),
                                            style = words.copy(fontWeight = FontWeight.SemiBold, fontSize = size * CARD_SMALL),
                                            modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.tonalHigh).padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            else -> CardLine(cardIcon(row.label), size, row.label) {
                                Text(withDigitFont(row.value), style = words)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A phone number on a line of its own, from the line's start: large, in a light weight and the
 * highlight colour, its digits in groups of 3, 4 and 3 a little apart ([TripCardText.spacedPhone]),
 * to be read at a glance while driving; whose it is ([label], as the card writes it) after it.
 */
@Composable
private fun PhoneLine(number: String, label: String?, words: TextStyle) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = DigitFont, fontWeight = FontWeight.Light, fontSize = words.fontSize * CARD_PHONE, color = AppTheme.colors.highlight, letterSpacing = PHONE_SPACING)) {
                append(TripCardText.spacedPhone(number))
            }
            if (label != null) {
                append("  ")
                withStyle(SpanStyle(color = AppTheme.colors.textMuted)) { append(label) }
            }
        },
        style = words,
    )
}

/** One field of a trip's card: its sign, its name small above it ([label], YouDrive's), and [content]. */
@Composable
private fun CardLine(icon: Int, size: TextUnit, label: String? = null, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(with(LocalDensity.current) { (size * CARD_SIGN).toDp() }).clip(CircleShape).background(AppTheme.colors.tonal),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = AppTheme.colors.info, modifier = Modifier.size(with(LocalDensity.current) { (size * CARD_SIGN * 0.58f).toDp() }))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            if (label != null) {
                Text(label, fontFamily = DisplayFont, fontSize = size * CARD_LABEL, color = AppTheme.colors.textMuted, maxLines = 1)
            }
            content()
        }
    }
}

/**
 * The card's instructions, marked in yellow: what the driver must not miss at the door. Each thing
 * the dispatcher wrote is a tile of its own ([CardNotes]), so they never run together, however many
 * there are; a phone number starts its tile, large ([PhoneLine]).
 */
@Composable
private fun CardNote(text: String, words: TextStyle) {
    val lines = remember(text) { CardNotes.of(text) }
    val colors = AppTheme.colors
    val stripe = colors.accent
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NOTE_GAP)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val small = words.fontSize * CARD_LABEL
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.accent, modifier = Modifier.size(with(LocalDensity.current) { (small * 1.3f).toDp() }))
            Spacer(Modifier.width(4.dp))
            Text(TripCardText.INSTRUCTIONS, fontFamily = DisplayFont, fontSize = small, color = colors.textMuted, maxLines = 1)
        }
        for (line in lines) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NOTE_CORNER))
                    .background(colors.tonalHigh)
                    .drawWithContent {
                        drawContent()
                        drawRect(stripe, size = Size(NOTE_STRIPE.toPx(), size.height))
                    }
                    .padding(start = NOTE_STRIPE + 8.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
            ) {
                when (line) {
                    is CardNotes.Line.Phone -> PhoneLine(line.number, line.label, words)
                    is CardNotes.Line.Words -> Text(withDigitFont(line.text), style = words)
                }
            }
        }
    }
}

/** The sign of a card's field, by YouDrive's name for it. */
private fun cardIcon(label: String?): Int = when (label) {
    TripCardText.ADDRESS -> R.drawable.ic_pin
    TripCardText.PHONE -> R.drawable.ic_phone
    TripCardText.SPACE -> R.drawable.ic_seat
    TripCardText.AIDS -> R.drawable.ic_accessible
    TripCardText.FARE, TripCardText.COMPENSATION -> R.drawable.ic_wallet
    TripCardText.ELIGIBILITY -> R.drawable.ic_verified
    else -> R.drawable.ic_info
}

/** [text] with every run of digits in [DigitFont] (the display's figures). */
private fun withDigitFont(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val digit = text[i].isDigit()
        var j = i
        while (j < text.length && text[j].isDigit() == digit) j++
        if (digit) withStyle(SpanStyle(fontFamily = DigitFont)) { append(text, i, j) } else append(text, i, j)
        i = j
    }
}

/**
 * The way to the next stop on the map (as a long press on its address): a small pin, with no frame.
 * The map grows out of it ([onPlaced]: its middle on the screen).
 */
@Composable
private fun MapSign(onClick: () -> Unit, enabled: Boolean, onPlaced: (Offset) -> Unit) {
    val description = stringResource(R.string.display_show_way)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .onGloballyPositioned { onPlaced(it.boundsInRoot().center) }
            .refCorner(219)
            .size(TouchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick),
    ) {
        Icon(painterResource(R.drawable.ic_pin), contentDescription = description, tint = AppTheme.colors.highlight, modifier = Modifier.size(TOP_ICON + 2.dp))
    }
}

/**
 * One part ([own]) of something that grows as one piece ([group]): with [grow] 0 → 1 the piece
 * moves to the middle of [area] and grows to [TIME_GROWTH] times its size, or [TIME_FILL] of the
 * area if that is less, each part keeping its place in it. The bounds are taken before this layer.
 */
private fun Modifier.growTogether(grow: () -> Float, own: () -> Rect, group: () -> Rect, area: () -> Rect): Modifier = graphicsLayer {
    val p = grow()
    val g = group()
    val a = area()
    val e = own()
    if (p <= 0f || g.isEmpty || a.isEmpty || e.isEmpty) return@graphicsLayer
    val most = minOf(TIME_GROWTH, a.height * TIME_FILL / g.height, a.width * TIME_FILL / g.width)
    val s = 1f + (most - 1f) * p
    scaleX = s
    scaleY = s
    translationX = p * (a.center.x - g.center.x) + (s - 1f) * (e.center.x - g.center.x)
    translationY = p * (a.center.y - g.center.y) + (s - 1f) * (e.center.y - g.center.y)
}

/** Lays this out at its full height but takes none in its row: it hangs down over what follows. */
private fun Modifier.overhang(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    layout(placeable.width, 0) { placeable.place(0, 0) }
}

/**
 * Fades what is not the time while it grows ([back] 0 → 1: in view to gone). The alpha goes to each drawing
 * rather than through a layer of its own, so nothing that reaches past its box is cut off.
 */
private fun Modifier.stepBack(back: () -> Float): Modifier = graphicsLayer {
    alpha = 1f - back()
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/** "07": Western digits in every language, like the trip times. */
private fun twoDigits(n: Int) = String.format(Locale.ROOT, "%02d", n)

/** Full screen (system bars hidden, swipe to show) and screen always on while visible. */
@Composable
private fun KeepScreenOnFullscreen() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        var ctx = view.context
        while (ctx !is Activity && ctx is android.content.ContextWrapper) ctx = ctx.baseContext
        val window = (ctx as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** About how long a part of an announcement takes to say, by its length at the usual speaking speed. */
private fun msToSayPart(text: String): Long = text.length.coerceAtLeast(12) * MS_PER_CHAR

/** About how long the whole announcement takes, with its silences and the screen's last steps. */
private fun msToSay(text: String?): Long {
    if (text == null || !Announcements.isNextStops(text)) return SAY_START_MS + (text?.length ?: 0).coerceAtLeast(30) * MS_PER_CHAR
    val parts = Announcements.parts(text)
    return Announcements.LEAD_MS + parts.sumOf { msToSayPart(it) } + (parts.size - 1) * Announcements.GAP_MS + THEN_HOLD_MS + FADE_MS
}

private const val MIN_TITLE_SP = 32f
private const val MAX_TITLE_SP = 300f

/** The address is this share of the size that would fill the room under its time. */
private const val TITLE_SHARE = 0.6f

/** Room between the address's words, in ems. */
private const val WORD_GAP = 0.1f

/** The trip's time over the address: this share of the clock's minutes. */
private const val HERO_TIME_OF_CLOCK = 0.85f

/**
 * The stars of a large address ([Stars]): each lasts [STAR_MS], growing [STAR_GROW_MS] and going
 * back [STAR_BACK_MS] (held between, while a flash [FLASH_BAND] wide and [FLASH_ALPHA] bright
 * crosses it); the address grows up to [ADDRESS_STAR] times, to [ADDRESS_STAR_FILL] of the
 * room, the time [CLOCK_STAR] times (to [CLOCK_STAR_FILL] at most); the rest shrinks by
 * [STAR_OTHERS_SHRINK] and fades by [STAR_OTHERS_FADE], the town and area also drawing
 * [STAR_AWAY] aside. The trip's word then takes [LABEL_MS], swelling by [TIME_WORD_SWELL]; a rest of
 * [STAR_REST_MS], and again. The first star comes [STAR_FIRST_MS] after the address shows.
 */
private const val STAR_MS = 3_800L
private const val STAR_GROW_MS = 1_200L
private const val STAR_BACK_MS = 1_000L
private const val ADDRESS_STAR = 3f
private const val ADDRESS_STAR_FILL = 0.92f
private const val CLOCK_STAR = 5f
private const val CLOCK_STAR_FILL = 0.95f
private const val STAR_OTHERS_SHRINK = 0.4f
private const val STAR_OTHERS_FADE = 0.7f
private val STAR_AWAY = 60.dp
private const val LABEL_MS = 1_600L
private const val STAR_REST_MS = 3_000L
private const val STAR_FIRST_MS = 2_500L
private const val FLASH_BAND = 0.35f
private const val FLASH_ALPHA = 0.85f
private const val NO_STAR = -1
private const val ADDRESS = 0
private const val CLOCK = 1
private const val LABEL = -2

/** The trip's word over the time is this share of the line's size, and swells this much. */
private const val TIME_WORD_SHARE = 0.55f
private const val TIME_WORD_SWELL = 0.3f

/**
 * The coming trips on the bottom line: their size (tablet, phone), how much of their street shows
 * (in widths of their size), and how faint the one after "Därefter" is.
 */
/** The trips on the bottom line ([ComingTrip]): at most this many (the next stop and three after it), each at least this wide. */
private const val COMING_MOST = 4
private val COMING_MIN_WIDTH = 150.dp
private const val NEXT_CHIP_REF = 299
private val COMING_REFS = listOf(94, 229, 272)
private val COMING_SP = 18.sp
private val COMING_SP_NARROW = 14.sp
private const val COMING_TIME_SHARE = 1.45f
private val COMING_BAR_WIDTH = 28.dp
private val COMING_BAR_HEIGHT = 4.dp
private val THEN_LABEL_SP = 10.sp
private const val DONE_CARD_ALPHA = 0.6f
private val HOME_SIZE = 28.dp
private val HOME_ICON = 16.dp
private val HOME_TOUCH = 40.dp
private val HOME_GAP = 10.dp
private const val HOME_BEAT = 1.14f
private const val HOME_BEAT_MS = 700

/** The top line's signs show this long after a tap or a swipe down on it. */
private const val TOP_SHOWN_MS = 10_000L

/** The motion sign: its size, its waves, how far it sways at rest, how fast its waves run (at rest, and more with the shaking). */
private val MOTION_WIDTH = 16.dp
private val MOTION_HEIGHT = 34.dp
private const val MOTION_WAVES = 1.5f
private val MOTION_REST_AMP = 1.dp
private const val MOTION_SPEED_REST = 0.15f
private const val MOTION_SPEED_SHAKE = 2.5f

/** The battery sign: its size, and the percent at which it turns red. */
private val BATTERY_WIDTH = 24.dp
private val BATTERY_HEIGHT = 12.dp
private const val BATTERY_LOW = 15

/**
 * The strip of all the trips: each trip's width (a share of the screen's: tablet, phone), its
 * sizes (time, street, area), how much the one in the middle grows, how it comes and goes, and how
 * long it stays after the last swipe.
 */
private const val STRIP_TRIP_SHARE = 0.19f
private const val STRIP_TRIP_SHARE_NARROW = 0.5f
private val STRIP_TIME_SP = 24.sp
private val STRIP_STREET_SP = 22.sp
private val STRIP_AREA_SP = 15.sp
private const val STRIP_LIT_SCALE = 1.12f
private const val BROWSE_IN_MS = 350
private const val BROWSE_OUT_MS = 300
private const val BROWSE_SCALE = 0.94f
private const val BROWSE_LINGER_MS = 3_000L

/** A tapped trip on the bottom line swells this much while it shows at the top. */
private const val CARD_TAP_SWELL = 1.22f

/**
 * An announcement on the screen: the screen before goes in [OUT_MS]; a trip pops in over [POP_MS]
 * (from [POP_FROM] of its size, a touch past it and back) and fades away over [FADE_MS]; the next
 * stop stays [NEXT_HOLD_MS] after it has been said, "Därefter" [THEN_HOLD_MS]. They fit the
 * voice's silences ([Announcements.LEAD_MS], [Announcements.GAP_MS]). The voice heard on this
 * device is waited for at most [VOICE_SLACK_MS] past when it should have finished.
 */
private const val OUT_MS = 400
private const val POP_MS = 900
private const val FADE_MS = 1_400
private const val POP_FROM = 0.8f
private val POP_IN = tween<Float>(POP_MS, easing = CubicBezierEasing(0.3f, 1.3f, 0.5f, 1f))
private const val NEXT_HOLD_MS = 2_000L
private const val THEN_HOLD_MS = 2_000L
private const val VOICE_SLACK_MS = 4_000L

/** Focused, the coming trips on the bottom line fade out a moment after the tap (so it is seen swelling), and come back gently. */
private const val STRIP_FADE_DELAY_MS = 250
private const val STRIP_FADE_MS = 350
private const val STRIP_BACK_MS = 600

/**
 * The next stop's time beside the clock (its minutes; the hours are smaller), gently breathing:
 * small, like everything else on the bottom line, so the clock is its one large thing.
 */
private val HERO_TIME_SP = 28.sp
private val HERO_TIME_SP_NARROW = 20.sp

private const val BREATH_SCALE = 1.2f
private const val BREATH_MS = 2_200

/** Any other announcement starts about this long after the tap. */
private const val SAY_START_MS = 700L

/** Swedish at the app's speaking speed: about 13 letters a second. */
private const val MS_PER_CHAR = 75L

/** The spotlight: what is not being said steps back to this. */
private const val DIM = 0.4f
private const val DIM_MS = 600

/** The address lights up this fast while it is said. */
private const val FOCUS_MS = 1_400

private const val NONE = -2

/** The top line's icons. */
private val TOP_ICON = 22.dp

/** The connection line (the phone's name, or what the tablet is doing) at most this wide: on a tablet, on a phone. */
private val STATUS_MAX_WIDTH = 240.dp
private val STATUS_MAX_NARROW = 110.dp

/** Another stop paged to (or the strip scrolled) and left alone gives way to the next stop again. */
private const val BROWSE_RETURN_MS = 30_000L
private const val NEXT_STOP = -1

private val MINUTE_SP_WIDE = 180.sp
private const val MINUTE_SP_NARROW = 100f
private const val MINUTE_SP_MIN = 48f

/**
 * The clock is about this many dp wide per sp of its minutes; beside it a phone keeps this much
 * (for the next stop's time and the margins).
 */
private const val CLOCK_WIDTH_PER_SP = 2.65f
private val NARROW_RIGHT_ROOM = 210.dp

/**
 * Under the clock's digits: its seconds (their top this share of the minutes' size under the
 * minutes' baseline), and this much more to the screen's bottom, where the dots float
 * [DOTS_LOW] above it.
 */
private const val SECONDS_DROP = 0.07f
private val SECONDS_CLEAR = 16.dp
private val DOTS_LOW = 8.dp

/** A digit of the seconds rolls into place in this long. */
private const val ROLL_MS = 450

private const val HOUR_SHARE = 0.62f
private const val SECOND_SHARE = 0.17f

/**
 * The digit font's metrics as shares of its size: how tall its digits are, and how far its line
 * box reaches above the baseline and below it (a line of digits is laid out that tall).
 */
private const val DIGIT_HEIGHT = 0.67f
private const val DIGIT_ASCENT = 0.984f
private const val DIGIT_DESCENT = 0.316f

/** The time grows to five times its size, or to this share of the screen if that is less. */
private const val TIME_GROWTH = 5f
private const val TIME_FILL = 0.9f
private const val GROW_MS = 5_000
private const val SHRINK_MS = 1_200
private const val SETTLE_MS = 300

/**
 * The solid ground behind the time: it fades in over the last part of the growth, stays two
 * seconds with the time at its largest, and fades out as the time goes back.
 */
private const val SOLID_IN_MS = 1_800
private const val SOLID_HOLD_MS = 2_000L
private const val SOLID_OUT_MS = 1_800

/** A tap on the clock: the time springs out, the ground follows a moment later, and both stay four seconds. */
private const val TAP_SPRING_DAMPING = 0.7f
private const val TAP_SOLID_AFTER_MS = 300L
private const val TAP_HOLD_MS = 4_000L

/**
 * The moments take turns (the time, the weather, the map), each this many seconds after the one
 * before has gone; the weather and the travel time show seven seconds in all: in, held, out.
 */
private const val MOMENT_GAP_S = 50
private const val MOMENT_TURNS = 3
private const val INFO_IN_MS = 1_200
private const val INFO_HOLD_MS = 4_600L
private const val INFO_OUT_MS = 1_200

/** The map: forty seconds in all, filling the screen (it follows the car, then may fly to the next stop). */
private const val MAP_HOLD_MS = 37_600L

/** The driver's own map stays until he closes it, or this long after he last used it. */
private const val FOCUS_MAX_MS = 120_000L

/** An order tried on the driver's map is asked of Google this long after the last arrow tap. */
private const val ORDER_ASK_MS = 450L

/** The driver's map over the stepped-back screen, its text and buttons over it, a trip's card over all. */
private const val MAP_HELD_Z = 1f
private const val MAP_FLOOR_BELOW = 0.5f
private const val MAP_OVER_Z = 2f
private const val CARD_Z = 3f

/** The driver's map buttons: their size, their icons, the room between them and from the edge. */
/** A phone number's digits, a little apart from each other. */
private val PHONE_SPACING = 0.04.em

private val MAP_BUTTON = 48.dp
private val MAP_ICON = 24.dp
private val MAP_CONTROLS_GAP = 10.dp
private val MAP_CONTROLS_EDGE = 20.dp

/**
 * A trip's card: its text size (a tablet, a phone), its share of the width and at most of the
 * height (on the tablet about a quarter of the screen), its corners, edge room and gaps, its
 * shadow; its title, name, small words, field names and signs as shares of its text size; the
 * instructions' yellow stripe; the ground over the screen behind it, in and out.
 */
private val CARD_SP = 14.sp
private val CARD_SP_NARROW = 13.sp
private const val CARD_WIDTH = 0.36f
private const val CARD_WIDTH_NARROW = 0.8f
private const val CARD_HEIGHT = 0.5f
private val CARD_CORNER = 16.dp
private val CARD_PAD = 12.dp
private val CARD_GAP = 8.dp
private val CARD_SHADOW = 12.dp
private const val CARD_TITLE = 1.15f
private const val CARD_NAME = 1.1f
private const val CARD_SMALL = 0.85f
private const val CARD_LABEL = 0.75f
private const val CARD_SIGN = 1.9f
private val NOTE_STRIPE = 3.dp
private val NOTE_GAP = 4.dp
private val NOTE_CORNER = 8.dp

/** A phone number on the card: this much larger than its words. */
private const val CARD_PHONE = 1.3f

/** The card's −, + and ×: this much of the words' size, on a light round ground. */
private const val CARD_BUTTON = 1.9f
private const val CARD_BUTTON_GROUND = 0.18f

/** The windows the driver moves and sizes, by the name their place is kept under. */
private const val CARD_WINDOW = "trip_card"
private const val LIST_WINDOW = "way_list"

/** A figure's touch room beside a small trip (the bottom line, the strip). */
private val SMALL_TOUCH = 36.dp
private const val CARD_SCRIM = 0.6f
private const val CARD_IN_MS = 260
private const val CARD_OUT_MS = 180

/** The minutes and distance under the map: this size, and this share of the height above the bottom. */
private val MAP_SP = 150.sp
private const val MAP_TEXT_LOW = 0.04f

/** A weather app's widget, large in the middle. */
private val WIDGET_WIDE_W = 640.dp
private val WIDGET_NARROW_W = 340.dp
private const val WIDGET_ASPECT = 0.5f
private val INFO_SP_WIDE = 220.sp

/**
 * The next stop's passenger's last name: on its line under the address (tablet, phone), and as
 * large as fits (up to these sizes, within this share of the width) when tapped.
 */
/** The figure for the passenger's name: this share of the name's size; the name stays this long once shown, in and out. */
private const val PERSON_SIZE = 0.4f
private const val NAME_OPEN_MS = 15_000L
private const val NAME_IN_MS = 280
private const val NAME_OUT_MS = 200

/** The line over a stop's address ([HeroLine]): its words this size, its time this much larger. */
private val HERO_LINE_SP = 44.sp
private val HERO_LINE_SP_NARROW = 30.sp
/**
 * The trip's time over its address, in the clock's style: at most this share of the room left
 * over the address and of the width, and at least this size (of its minutes).
 */
private const val HERO_TIME_FILL = 0.92f
private const val HERO_TIME_WIDTH = 0.62f
private val HERO_TIME_MIN = 40.sp
private val NAME_SP_WIDE = 260.sp
private val NAME_SP_NARROW = 120.sp
private const val NAME_WIDTH = 0.9f
private val INFO_SP_NARROW = 110.sp

/** The colon: how faint it blinks with the seconds, and how it beats when the next stop is due or well past. */
private const val COLON_LOW = 0.35f

/** The colon's dots, their distance apart (centre to centre) and the room at each side, as shares of the hours' size. */
private const val COLON_DOT = 0.16f
private const val COLON_SPREAD = 0.36f
private const val COLON_SIDE = 0.12f

/**
 * The arrows of the bottom line: how tall (a share of the trip's time size), how long (a share of
 * their height), the room at each side (the same), their chevrons, how many arrows, how faint and
 * how bright, and how long the light takes along all of them.
 */
private const val ARROW_TALL = 0.42f
private const val ARROW_LONG = 1.6f
private const val ARROW_SPACE = 0.6f
private const val ARROW_COUNT = 2
private const val ARROW_LINES = 3
private const val ARROW_LOW = 0.35f
private const val ARROW_HIGH = 1f
private const val ARROW_FLOW_MS = 2_700

/** The largest a time is drawn; focused, the clock and the top line fade out fast and come back gently. */
private val FOCUS_MAX = 260.sp

/**
 * The entrance of what the display focuses on: how long it takes, how far it rises, how small and
 * how blurred it starts, how fast it becomes opaque, and the delays that stagger the time, the
 * address and the area; then the sweep of light: when, how long, how wide and how bright.
 */
private const val APPEAR_MS = 1_400
private val ENTER_RISE = 28.dp
private const val ENTER_SCALE = 0.93f
private val ENTER_BLUR = 16.dp
private const val ENTER_ALPHA_SPEED = 1.8f
private const val ENTER_TITLE_DELAY_MS = 700L
private const val ENTER_AREA_DELAY_MS = 0L
private const val SHINE_AFTER_MS = 300L
private const val SHINE_MS = 1_800
private const val SHINE_BAND = 0.3f
private const val SHINE_ALPHA = 0.55f
private const val CLOCK_FADE_MS = 250
private const val CLOCK_BACK_MS = 600
private const val BEAT_LOW = 0.2f
private const val BEAT_MS = 700

/** How far the rest of the screen steps back while the time grows (before the solid ground). */
private const val REST_FADE = 0.85f

/** The bottom line beside the clock fades this many times faster than the clock grows. */
private const val LINE_FADE_SPEED = 6f

/** Digits of equal width, for what changes each second (the seconds), so it does not shift. */
private const val TABULAR = "tnum"
