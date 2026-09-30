package se.eldebosh.nastastopp.ui.screens

import android.app.Activity
import kotlin.math.sin
import kotlin.math.cos
import se.eldebosh.nastastopp.core.weather.WeatherKind
import se.eldebosh.nastastopp.core.weather.DisplayWeather
import se.eldebosh.nastastopp.core.parse.TripTimes
import se.eldebosh.nastastopp.core.nav.DisplayEta
import se.eldebosh.nastastopp.core.display.TimeStatus
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import kotlin.math.roundToInt
import se.eldebosh.nastastopp.ui.DoneMarks
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.display.DisplayItem
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.core.route.Announcement
import se.eldebosh.nastastopp.core.route.Announcements
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.AppTheme
import se.eldebosh.nastastopp.ui.theme.DisplayFont
import java.time.LocalTime
import java.util.Locale

/**
 * Passenger display: made to be read by the passengers from their seats, while the driver works
 * it from the phone. The next stop fills the middle, a large clock stands in the corner and the
 * following trips sit below as cards, the first one ("Därefter") marked. The words for the
 * passengers are Swedish, like the announcements; the connection line (for the driver) follows
 * the app's language.
 *
 * It moves with the route:
 * - when the driver taps Next, the "Därefter" card rises from its place and grows into the new
 *   next stop (a shared-bounds transition; Back runs it the other way);
 * - while an announcement is spoken ([spoken] goes up by one each time, and a tap on the speaker
 *   counts too), a spotlight follows it: "NÄSTA STOPP" and its time spring up, the next stop
 *   slowly grows and lights up in the highlight colour while the rest steps back, then the
 *   "Därefter" card grows well past its size when its name comes, and settles;
 * - a tap on a card says it ("Därefter: …") and grows it the same way, over four seconds;
 * - a tap on the clock says the time;
 * - when the minute changes, the time grows into the middle of the screen over five seconds,
 *   stays five more and goes back faster (never while something is being said).
 *
 * Landscape (a tablet): the following trips side by side. Portrait (the phone): one below the other.
 *
 * @param status connection line for a remote display (null on the driver's own device).
 * @param onSay says what a tap on the clock or a card asks for, on this device.
 * @param time the time of day (tests set it).
 */
@Composable
fun PassengerDisplayScreen(
    snapshot: DisplaySnapshot?,
    status: String?,
    connected: Boolean,
    onSpeak: () -> Unit,
    onExit: () -> Unit,
    extraActions: @Composable () -> Unit = {},
    detail: String? = null,
    spoken: Int = 0,
    onSay: (Announcement) -> Unit = {},
    time: () -> LocalTime = { LocalTime.now() },
) {
    KeepScreenOnFullscreen()
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
    val moments = rememberMoments(now, cue + said, snapshot?.announcementSv, weather = live?.weather, eta = live?.eta)
    // Everything but the time (or the weather, or the travel time) steps back while it shows, and
    // is hidden behind a solid ground at its largest.
    val rest = Modifier.stepBack { moments.back }
    // How the next stop's time stands, for the outline on the minutes (none without a route).
    val nextTime = live?.current?.time
    val hasNext = live != null
    val timeStatus by remember(nextTime, hasNext) {
        derivedStateOf { if (hasNext) TimeStatus.of(TripTimes.minutesUntil(nextTime, now.value.hour * 60 + now.value.minute)) else null }
    }
    // Swedish for the passengers, read left to right whatever the app's language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val landscape = maxWidth > maxHeight
        val width = maxWidth
        var screen by remember { mutableStateOf(Rect.Zero) }
        // On a tablet the clock hangs down beside the next stop instead of pushing it down; the
        // address itself starts below it.
        var clockBottom by remember { mutableFloatStateOf(0f) }
        var stageTop by remember { mutableFloatStateOf(0f) }
        val clear = with(LocalDensity.current) { (clockBottom - stageTop).coerceAtLeast(0f).toDp() }
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { screen = it.boundsInRoot() }
                .padding(horizontal = if (landscape) 32.dp else 20.dp, vertical = 12.dp),
        ) {
            // Drawn over what follows, so the clock hangs over it and the growing time passes in front.
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().zIndex(1f)) {
                Column(rest.weight(1f)) {
                    TopLine(
                        status,
                        connected,
                        wide = landscape,
                        onExit = onExit,
                        extraActions = extraActions,
                        // On a tablet the trip just done opens the top line (one quiet line, so the
                        // passengers see the list moving on).
                        lead = {
                            val previous = live?.previous
                            if (landscape && previous != null) PreviousLine(previous, Modifier.ref(89).weight(1f).padding(end = 16.dp))
                            else if (landscape) Spacer(Modifier.weight(1f))
                        },
                    )
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
                Clock(
                    now = now,
                    // On a narrow phone the clock leaves room for the exit and status.
                    minuteSize = if (landscape) MINUTE_SP_WIDE else ((width - NARROW_LEFT_ROOM).value / CLOCK_WIDTH_PER_SP).coerceIn(MINUTE_SP_MIN, MINUTE_SP_NARROW).sp,
                    grow = { moments.grow.value },
                    hue = AppTheme.colors.showHues[moments.hue % AppTheme.colors.showHues.size],
                    status = timeStatus,
                    screen = { screen },
                    // Said here, and the time springs out to fill the screen meanwhile.
                    onClick = {
                        onSay(Announcements.clock(now.value.hour, now.value.minute))
                        moments.playTime(tapped = true)
                    },
                    modifier = (if (landscape) Modifier.overhang() else Modifier)
                        .onGloballyPositioned { clockBottom = it.boundsInRoot().bottom }
                        .ref(88)
                        .padding(start = 16.dp),
                )
            }

            if (live == null) {
                Box(rest.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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

            if (!landscape) live.previous?.let { PreviousLine(it, rest.ref(89).padding(top = 4.dp)) }

            Stage(
                live,
                landscape,
                cue,
                status = timeStatus,
                clear = clear,
                // A tap on the next stop's address says the announcement here.
                onSpeakNext = {
                    tapped++
                    onSpeak()
                },
                onSay = say,
                modifier = rest.weight(1f).fillMaxWidth().onGloballyPositioned { stageTop = it.boundsInRoot().top },
            )
        }
        // The weather or the travel time, in the middle while it shows.
        InfoMoment(moments, live?.weather, live?.eta, landscape, Modifier.align(Alignment.Center))
        // While the time, the weather or the travel time fills the screen, a tap anywhere brings the
        // screen back at once.
        if (moments.showing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { moments.settle() } },
            )
        }
    }
    }
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
 * - the weather in the middle of each minute, and Google Maps' travel time a little later
 *   ([info] 0 → 1 → 0 over seven seconds, [shown] saying which).
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
    private var job: Job? = null

    /** How far everything else steps back: 0 in view, 1 gone. */
    val back: Float get() = maxOf(REST_FADE * grow.value, solid.value, info.value)

    /** Something fills the middle now (a tap brings the screen back). */
    val showing: Boolean by derivedStateOf { grow.value > 0f || info.value > 0f }

    val idle: Boolean get() = job?.isActive != true && grow.value == 0f && info.value == 0f

    /** The time fills the screen: slowly as the minute changes, springing out when [tapped]. */
    fun playTime(tapped: Boolean = false) {
        job?.cancel()
        hue++
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
        job?.cancel()
        shown = which
        job = scope.launch {
            info.animateTo(1f, tween(INFO_IN_MS, easing = FastOutSlowInEasing))
            delay(INFO_HOLD_MS)
            info.animateTo(0f, tween(INFO_OUT_MS, easing = FastOutSlowInEasing))
        }
    }

    /** Straight back: a tap, or something being said. */
    fun settle() {
        job?.cancel()
        job = scope.launch {
            launch { solid.animateTo(0f, tween(SETTLE_MS)) }
            launch { info.animateTo(0f, tween(SETTLE_MS)) }
            grow.animateTo(0f, tween(SETTLE_MS))
        }
    }

    enum class Info { WEATHER, ETA }
}

/**
 * The moments of each minute: the time as the minute changes, the weather in its middle, Google
 * Maps' travel time a little later (each only when there is something to show). Nothing comes
 * while something is being said ([cue] goes up with each announcement and tap), and a new cue
 * sends it straight back.
 */
@Composable
private fun rememberMoments(now: State<LocalTime>, cue: Int, spokenText: String?, weather: DisplayWeather?, eta: DisplayEta?): Moments {
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
    val minute by remember { derivedStateOf { now.value.hour * 60 + now.value.minute } }
    val opened = remember { minute }
    LaunchedEffect(minute) {
        if (minute != opened && !quiet) moments.playTime()
    }
    val second by remember { derivedStateOf { now.value.second } }
    val hasWeather by rememberUpdatedState(weather != null)
    val hasEta by rememberUpdatedState(eta != null)
    LaunchedEffect(second) {
        if (quiet || !moments.idle) return@LaunchedEffect
        when (second) {
            WEATHER_AT_S -> if (hasWeather) moments.playInfo(Moments.Info.WEATHER)
            ETA_AT_S -> if (hasEta) moments.playInfo(Moments.Info.ETA)
        }
    }
    return moments
}

/** The weather or Google Maps' travel time, large in the middle while [moments] shows it. */
@Composable
private fun InfoMoment(moments: Moments, weather: DisplayWeather?, eta: DisplayEta?, landscape: Boolean, modifier: Modifier = Modifier) {
    if (moments.info.value <= 0f) return
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
            Moments.Info.WEATHER -> if (weather != null) {
                WeatherGlyph(weather.kind, hue, Modifier.size(if (landscape) 220.dp else 120.dp))
                Spacer(Modifier.width(if (landscape) 40.dp else 16.dp))
                Column(Modifier.ref(204)) {
                    Text(
                        "${weather.tempC}°",
                        fontFamily = DisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = big,
                        color = hue,
                        style = TextStyle(fontFeatureSettings = TABULAR, lineHeight = 1.0.em),
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
                            fontFamily = DisplayFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = big,
                            color = hue,
                            style = TextStyle(fontFeatureSettings = TABULAR, lineHeight = 1.0.em),
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
        }
    }
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
 * The next stop and the trips around it, changing together: the new next stop grows out of its
 * "Därefter" card. A spotlight follows what is said.
 *
 * The passengers can look around without moving the route: in the middle they page from the next
 * stop on to the following trips and back to the ones done (sideways on a tablet, up and down on
 * the phone), and the strip of cards below scrolls sideways through the same trips. A tap on a
 * card says its time and place, swells it a little and shows that trip in the middle for a few
 * seconds. A small Home button brings everything back, and so do an announcement and half a
 * minute left alone. A tap on the address in the middle says it: the next stop's announcement
 * ([onSpeakNext]), or another trip's time and place.
 *
 * The stops in the middle keep [clear] free at their top, for the clock hanging down beside them.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Stage(
    snapshot: DisplaySnapshot,
    landscape: Boolean,
    cue: Int,
    status: TimeStatus?,
    clear: Dp,
    onSpeakNext: () -> Unit,
    onSay: (Announcement) -> Unit,
    modifier: Modifier = Modifier,
) {
    SharedTransitionLayout(modifier) {
        AnimatedContent(
            targetState = snapshot,
            contentKey = { it.current?.trip },
            transitionSpec = {
                (fadeIn(tween(ENTER_MS, delayMillis = 150)) togetherWith fadeOut(tween(250)) + scaleOut(tween(300), targetScale = 0.92f))
                    .using(SizeTransform(clip = false))
            },
            label = "next stop",
        ) { shown ->
            val current = shown.current ?: return@AnimatedContent
            val shared = SharedStop(this@SharedTransitionLayout, this@AnimatedContent)
            val spotlight = rememberSpotlight()
            val earlier = shown.earlier
            val upcoming = shown.upcoming
            // The strip's cards: the trips done, then the coming ones; card [home] is the first
            // "Därefter", and page [home] in the middle is the next stop.
            val home = earlier.size
            val cardCount = earlier.size + upcoming.size
            val cardItem = { k: Int -> if (k < home) earlier[k] else upcoming[k - home] }
            val pageOfCard = { k: Int -> if (k < home) k else k + 1 }
            val pager = rememberPagerState(initialPage = home) { cardCount + 1 }
            val strip = rememberLazyListState(initialFirstVisibleItemIndex = home)
            val stripDragged by strip.interactionSource.collectIsDraggedAsState()
            var stripMoved by remember { mutableStateOf(false) }
            LaunchedEffect(stripDragged) { if (stripDragged) stripMoved = true }
            val scope = rememberCoroutineScope()
            val goHome: suspend () -> Unit = {
                coroutineScope {
                    launch { pager.animateScrollToPage(home) }
                    launch { strip.animateScrollToItem(home) }
                }
                stripMoved = false
            }
            val away = pager.currentPage != home || stripMoved
            val sayTrip = { item: DisplayItem -> onSay(Announcements.at(item.time, listOfNotNull(item.title, item.subtitle).joinToString(", "))) }
            // The announcement: first the next stop, then the "Därefter" card when its name comes.
            LaunchedEffect(cue) {
                if (cue == 0) return@LaunchedEffect
                spotlight.play {
                    delay(SAY_DELAY_MS) // after a change of stop, once the new one has grown into place
                    on = NEXT_STOP
                    delay(msUntilThen(shown.announcementSv) - SAY_DELAY_MS)
                    if (upcoming.isNotEmpty()) {
                        lifting = true
                        on = home
                        delay(CARD_LIT_MS)
                    }
                }
                goHome()
            }
            // A tapped card: said, and its trip shown in the middle for a moment, then home again.
            val showCard = { k: Int ->
                sayTrip(cardItem(k))
                spotlight.play {
                    lifting = false
                    on = k
                    coroutineScope {
                        launch { pager.animateScrollToPage(pageOfCard(k)) }
                        delay(SHOW_TRIP_MS)
                    }
                    goHome()
                }
            }
            // Looked around and left there: back after a while.
            LaunchedEffect(away, pager.isScrollInProgress, strip.isScrollInProgress) {
                if (!away || pager.isScrollInProgress || strip.isScrollInProgress) return@LaunchedEffect
                delay(BROWSE_RETURN_MS)
                goHome()
            }
            // The strip follows the stop paged to in the middle.
            LaunchedEffect(pager.settledPage) {
                val page = pager.settledPage
                strip.animateScrollToItem(if (page == home) home else (cardOf(page, home) - 1).coerceAtLeast(0))
            }
            val spot = spotlight.on
            // The "Därefter" card the announcement says is lifted out of the strip (which cuts off
            // what passes its edge) and drawn over everything while it grows.
            val cards = remember { mutableStateMapOf<Int, Rect>() }
            var stage by remember { mutableStateOf(Rect.Zero) }
            val lift = remember { Animatable(0f) }
            var lifted by remember { mutableIntStateOf(NONE) }
            LaunchedEffect(spot, spotlight.lifting) {
                if (spot >= 0 && spotlight.lifting) {
                    lifted = spot
                    lift.animateTo(1f, tween(CARD_GROW_MS, easing = FastOutSlowInEasing))
                } else if (lifted != NONE) {
                    lift.animateTo(0f, tween(CARD_GROW_MS, easing = FastOutSlowInEasing))
                    lifted = NONE
                }
            }
            Box(Modifier.fillMaxSize().onGloballyPositioned { stage = Rect(it.positionInRoot(), it.size.toSize()) }) {
                Column(Modifier.fillMaxSize()) {
                    val page = @Composable { index: Int ->
                        val hero = Modifier.fillMaxSize().padding(top = clear)
                        if (index == home) {
                            StopHero(current, HeroRole.NEXT, landscape, focused = spot == NEXT_STOP, dimmed = spot >= 0 && spotlight.lifting, shared = shared, onClick = onSpeakNext, status = status, modifier = hero)
                        } else {
                            // A card that is said lights up here too while its trip is shown.
                            val k = cardOf(index, home)
                            val item = cardItem(k)
                            StopHero(
                                item,
                                if (index < home) HeroRole.EARLIER else HeroRole.LATER,
                                landscape,
                                focused = spot == k,
                                dimmed = spot != NONE && spot != k,
                                shared = null,
                                onClick = { sayTrip(item) },
                                modifier = hero,
                            )
                        }
                    }
                    val pages = Modifier.weight(1f).fillMaxWidth()
                    if (landscape) HorizontalPager(pager, pages) { page(it) } else VerticalPager(pager, pages) { page(it) }
                    if (pager.pageCount > 1) {
                        PageDots(pager.currentPage, pager.pageCount, home, Modifier.align(Alignment.CenterHorizontally).ref(201, centered = true).padding(top = 8.dp))
                    }
                    if (cardCount > 0) {
                        Box(Modifier.fillMaxWidth()) {
                            Column {
                                if (upcoming.isNotEmpty()) ThenLabel() else Spacer(Modifier.height(14.dp))
                                LazyRow(state = strip, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                                    items(cardCount) { k ->
                                        val item = cardItem(k)
                                        val first = k == home
                                        TripCard(
                                            item,
                                            first = first,
                                            done = k < home,
                                            landscape = landscape,
                                            dimmed = spot != NONE && spot != k,
                                            hidden = lifted == k,
                                            shownAbove = pager.currentPage == pageOfCard(k),
                                            tapped = spot == k && !spotlight.lifting,
                                            onPlaced = { cards[k] = it },
                                            onClick = { showCard(k) },
                                            shared = shared,
                                            modifier = Modifier.ref(94).fillParentMaxWidth(
                                                when {
                                                    first && landscape -> FIRST_CARD_SHARE
                                                    first -> FIRST_CARD_SHARE_NARROW
                                                    landscape -> CARD_SHARE
                                                    else -> CARD_SHARE_NARROW
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
                            // Small and floating over the strip's end: back to the next stop.
                            HomeButton(visible = away, onClick = { scope.launch { goHome() } }, modifier = Modifier.align(Alignment.TopEnd))
                        }
                    }
                }
                val from = cards[lifted]
                if (lifted in 0 until cardCount && from != null && !stage.isEmpty) {
                    LiftedCard(cardItem(lifted), first = lifted == home, landscape, from = from, stage = stage, reach = CARD_FOCUS, lift = { lift.value })
                }
            }
        }
    }
}

/** The strip's card of the stop shown on [page] of the middle, whose next stop is page [home]. */
private fun cardOf(page: Int, home: Int) = if (page < home) page else page - 1

/**
 * What the screen points at while something is said: the next stop ([NEXT_STOP]), a trip's card
 * (its place in the strip; [lifting] it out when the announcement says it) or nothing ([NONE]).
 * A new [play] replaces the one running.
 */
@Stable
private class Spotlight(private val scope: CoroutineScope) {
    var on by mutableIntStateOf(NONE)
    var lifting by mutableStateOf(false)
    private var job: Job? = null

    fun play(steps: suspend Spotlight.() -> Unit) {
        job?.cancel()
        on = NONE
        lifting = false
        job = scope.launch {
            steps()
            on = NONE
        }
    }
}

@Composable
private fun rememberSpotlight(): Spotlight {
    val scope = rememberCoroutineScope()
    return remember(scope) { Spotlight(scope) }
}

/** The scopes a stop needs to move between its card and the middle of the screen. */
@OptIn(ExperimentalSharedTransitionApi::class)
private class SharedStop(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope) {
    /** The same trip, as a card or as the next stop (done or not), keeps its place in the transition. */
    @Composable
    fun Modifier.stop(item: DisplayItem): Modifier = with(transition) {
        this@stop.sharedBounds(
            rememberSharedContentState(key = item.trip),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow) },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
        )
    }
}

/**
 * The top line: [lead] (on a tablet the trip just done, taking the room left), then the
 * connection line and exit (small and quiet: they are for the driver). On a [wide] screen the
 * connection line keeps its own width; on the phone it takes what is left.
 */
@Composable
private fun TopLine(
    status: String?,
    connected: Boolean,
    wide: Boolean,
    onExit: () -> Unit,
    extraActions: @Composable () -> Unit,
    lead: @Composable RowScope.() -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        lead()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = if (wide) Modifier.widthIn(max = STATUS_MAX_WIDTH) else Modifier.weight(1f)) {
            if (status != null) {
                Box(Modifier.size(10.dp).background(if (connected) AppTheme.colors.success else AppTheme.colors.danger, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.ref(87),
                )
            }
        }
        extraActions()
        IconButton(onClick = onExit, modifier = Modifier.refCorner(86).size(TouchTarget)) {
            Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.display_exit), tint = AppTheme.colors.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PreviousLine(item: DisplayItem, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        DoneMarks(youDrive = item.doneInYouDrive, here = item.doneHere, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            listOfNotNull(item.time, item.title, item.subtitle).joinToString("  ·  "),
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            color = AppTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(textDirection = TextDirection.Content, fontFeatureSettings = TABULAR),
            modifier = Modifier.alpha(0.55f),
        )
    }
}

/** Which stop the middle shows: one done, the next stop, or one coming after it. */
private enum class HeroRole { EARLIER, NEXT, LATER }

/**
 * A stop filling the middle: the street and number as large as fits (at most two lines, never
 * breaking a word) and the area under it. Above the address at the left, floating (it takes no
 * line of its own), its time in the clock's style, the digits gently breathing: the
 * [HeroRole.NEXT] stop's with a yellow "NÄSTA", the others' alone, growing as their page comes in.
 * Where the trip was marked done shows beside its time. The next stop keeps its place in the card
 * → next stop transition ([shared]). A tap says it ([onClick]). While it is [focused], its time
 * springs up and settles, and the address slowly grows and lights up; while something else is
 * said, it is [dimmed].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StopHero(
    current: DisplayItem,
    role: HeroRole,
    landscape: Boolean,
    focused: Boolean,
    dimmed: Boolean,
    shared: SharedStop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: TimeStatus? = null,
) {
    val next = role == HeroRole.NEXT
    val labelScale = remember { Animatable(if (next) 1f else PAGE_POP) }
    LaunchedEffect(Unit) {
        // A trip paged to: its time comes in large and settles.
        if (!next) labelScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        labelScale.animateTo(1.35f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        labelScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
    }
    // How far the address may grow and stay within its page (a page cuts off what passes its
    // edge): less for a long one.
    var room by remember { mutableFloatStateOf(TITLE_FOCUS) }
    val scale by animateFloatAsState(if (focused) room else 1f, tween(FOCUS_MS, easing = FastOutSlowInEasing), label = "focus")
    val shade by animateFloatAsState(if (dimmed) DIM else 1f, tween(DIM_MS), label = "dim")
    val titleColor by animateColorAsState(if (focused) AppTheme.colors.highlight else AppTheme.colors.text, tween(FOCUS_MS / 2), label = "street")
    val placed = if (shared == null) modifier else with(shared) { modifier.stop(current) }
    val description = stringResource(if (next) R.string.display_repeat else R.string.display_say_trip)
    // Somewhat above the middle, so the address stays high on the screen.
    Box(
        placed
            .graphicsLayer { alpha = shade }
            .clickable(interactionSource = null, indication = null, onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = BiasAlignment(0f, HERO_BIAS),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Above the address at the left, floating: it takes no line, so the address keeps its place.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.Start)
                    .floatAbove(8.dp)
                    .ref(90)
                    .graphicsLayer {
                        scaleX = labelScale.value
                        scaleY = labelScale.value
                        transformOrigin = TransformOrigin(0f, 1f)
                    },
            ) {
                if (next) {
                    NextChip(landscape)
                    Spacer(Modifier.width(14.dp))
                }
                if (current.time != null) TimeFace(current.time, if (landscape) HERO_TIME_SP else HERO_TIME_SP_NARROW, status = if (next) status else null)
                if (current.doneInYouDrive || current.doneHere) {
                    Spacer(Modifier.width(12.dp))
                    DoneMarks(youDrive = current.doneInYouDrive, here = current.doneHere, size = if (landscape) 26.dp else 18.dp)
                }
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoxWithConstraints(Modifier.ref(91).weight(1f, fill = false).fillMaxWidth()) {
                    val style = TextStyle(
                        fontFamily = DisplayFont,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        textAlign = TextAlign.Center,
                        textDirection = TextDirection.Content,
                        lineHeight = 1.0.em,
                    )
                    // Never larger than the size at which the longest word still fits on one line,
                    // so a street name is not split in the middle ("Järnvägsg-atan").
                    val measurer = rememberTextMeasurer()
                    val width = constraints.maxWidth
                    val maxSp = remember(current.title, width) {
                        val widest = current.title.split(' ').filter { it.isNotBlank() }
                            .maxOfOrNull { measurer.measure(it, style.copy(fontSize = 100.sp)).size.width } ?: 0
                        if (widest <= 0) MAX_TITLE_SP else (100f * width / widest * 0.95f).coerceIn(MIN_TITLE_SP, MAX_TITLE_SP)
                    }
                    BasicText(
                        text = current.title,
                        style = style,
                        maxLines = 2,
                        autoSize = TextAutoSize.StepBased(minFontSize = MIN_TITLE_SP.sp, maxFontSize = maxSp.sp, stepSize = 2.sp),
                        onTextLayout = { layout ->
                            val line = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) - layout.getLineLeft(it) } ?: 0f
                            if (line > 0f) room = (layout.size.width / line).coerceIn(1f, TITLE_FOCUS)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                current.subtitle?.let {
                    Text(
                        it,
                        fontFamily = DisplayFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (landscape) 44.sp else 30.sp,
                        color = AppTheme.colors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Content),
                        modifier = Modifier.ref(92),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThenLabel() {
    Text(
        stringResource(R.string.passenger_then).uppercase(),
        fontFamily = DisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 1.5.sp,
        color = AppTheme.colors.textMuted,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
    )
}

/**
 * What a card shows: the time, where the trip was marked done, the street and number, and the area
 * under them. The [first] card (the announcement's "Därefter") is large and bold with its time in
 * the highlight colour; the others are small and thin, so it stands out.
 */
@Composable
private fun CardFace(item: DisplayItem, first: Boolean, landscape: Boolean) {
    val titleSp = if (first) (if (landscape) FIRST_CARD_SP else FIRST_CARD_SP_NARROW) else CARD_SP
    Column(Modifier.padding(horizontal = 16.dp, vertical = if (first) 12.dp else 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.time != null) {
                Text(
                    item.time,
                    fontFamily = DisplayFont,
                    fontWeight = if (first) FontWeight.Bold else FontWeight.Medium,
                    fontSize = titleSp,
                    color = if (first) AppTheme.colors.highlight else AppTheme.colors.time,
                    style = TextStyle(fontFeatureSettings = TABULAR),
                )
                Spacer(Modifier.width(10.dp))
            }
            if (item.doneInYouDrive || item.doneHere) {
                DoneMarks(youDrive = item.doneInYouDrive, here = item.doneHere, size = if (first) 22.dp else 16.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                item.title,
                fontFamily = DisplayFont,
                fontWeight = if (first) FontWeight.SemiBold else FontWeight.Light,
                fontSize = titleSp,
                color = AppTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Content),
            )
        }
        item.subtitle?.let {
            Text(
                it,
                fontFamily = DisplayFont,
                fontWeight = if (first) FontWeight.Medium else FontWeight.Light,
                fontSize = if (first) 22.sp else 18.sp,
                color = AppTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Content),
            )
        }
    }
}

private val CardShape = RoundedCornerShape(18.dp)

/**
 * A trip in the strip. The [first] coming one is the "Därefter" of the announcement: large and
 * edged in the highlight colour; the others are small and thin, the ones [done] quieter still.
 * A tap ([onClick]) swells it a little while its trip shows in the middle ([tapped]). While the
 * announcement says it, it is lifted out and grows over the screen ([hidden] here meanwhile);
 * while something else is said it is [dimmed]; while its trip is shown in the middle
 * ([shownAbove]) it keeps a highlight edge and tint.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TripCard(
    item: DisplayItem,
    first: Boolean,
    done: Boolean,
    landscape: Boolean,
    dimmed: Boolean,
    hidden: Boolean,
    shownAbove: Boolean,
    tapped: Boolean,
    onPlaced: (Rect) -> Unit,
    onClick: () -> Unit,
    shared: SharedStop,
    modifier: Modifier = Modifier,
) {
    val shade by animateFloatAsState(if (dimmed) DIM else 1f, tween(DIM_MS), label = "dim")
    val swell by animateFloatAsState(if (tapped) CARD_TAP_SWELL else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "swell")
    val background by animateColorAsState(if (shownAbove) AppTheme.colors.infoSoft else AppTheme.colors.card, tween(600), label = "card tint")
    val description = stringResource(R.string.display_say_trip)
    with(shared) {
        Box(
            modifier
                .onGloballyPositioned { onPlaced(Rect(it.positionInRoot(), it.size.toSize())) }
                .graphicsLayer {
                    alpha = if (hidden) 0f else shade
                    scaleX = swell
                    scaleY = swell
                }
                .stop(item)
                .clip(CardShape)
                .background(background)
                .border(
                    if (shownAbove) 3.dp else if (first) 2.dp else 1.dp,
                    if (first || shownAbove) AppTheme.colors.highlight else AppTheme.colors.cardBorder,
                    CardShape,
                )
                .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        ) {
            Box(Modifier.alpha(if (done && !shownAbove) DONE_CARD_ALPHA else 1f)) { CardFace(item, first, landscape) }
        }
    }
}

/**
 * The card being said, lifted out of the strip at its place ([from], in the screen) and drawn over
 * the [stage]: with [lift] 0 → 1 it rises to the middle and grows to [reach] times its size, or as
 * far as the stage allows, tinted and edged in the highlight colour.
 */
@Composable
private fun LiftedCard(item: DisplayItem, first: Boolean, landscape: Boolean, from: Rect, stage: Rect, reach: Float, lift: () -> Float) {
    val at = from.translate(-stage.left, -stage.top)
    val shadow = AppTheme.effects.panelShadow
    val (width, height) = with(LocalDensity.current) { at.width.toDp() to at.height.toDp() }
    Box(
        Modifier
            .offset { IntOffset(at.left.roundToInt(), at.top.roundToInt()) }
            .size(width, height)
            .graphicsLayer {
                val p = lift()
                val most = minOf(reach, stage.width * LIFT_FILL / at.width, stage.height * LIFT_FILL / at.height).coerceAtLeast(1f)
                val s = 1f + (most - 1f) * p
                scaleX = s
                scaleY = s
                translationX = p * (stage.width / 2f - at.center.x)
                translationY = p * (stage.height / 2f - at.center.y)
                shadowElevation = shadow.toPx() * p
                shape = CardShape
            }
            .clip(CardShape)
            .background(AppTheme.colors.infoSoft)
            .border(2.dp, AppTheme.colors.highlight, CardShape),
    ) {
        CardFace(item, first, landscape)
    }
}

/**
 * Where the passengers are among the stops: the one shown is a blue bar; the next stop ([home])
 * is a larger dot when they are elsewhere.
 */
@Composable
private fun PageDots(current: Int, count: Int, home: Int, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 28.dp else if (i == home) 12.dp else 8.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "dot")
            val tall = if (i == home && i != current) 12.dp else 8.dp
            Box(Modifier.size(width, tall).clip(CircleShape).background(if (i == current || i == home) AppTheme.colors.highlight else AppTheme.colors.outline))
        }
    }
}

/** Back to the next stop, while the passengers are looking elsewhere: a small house in a blue circle. */
@Composable
private fun HomeButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible,
        modifier,
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = scaleOut(tween(200)) + fadeOut(tween(200)),
    ) {
        val description = stringResource(R.string.display_home)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .refCorner(200)
                .shadow(AppTheme.effects.currentShadow, CircleShape)
                .size(HOME_SIZE)
                .clip(CircleShape)
                .background(AppTheme.colors.highlight)
                .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        ) {
            Icon(painterResource(R.drawable.ic_home), contentDescription = null, tint = AppTheme.colors.onInfo, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * The clock, without a frame: the minutes large, the hours smaller beside them and level with
 * their middle, the seconds thin under the minutes in the highlight colour, and the colon, in the
 * accent yellow, beating with the seconds.
 * A thin ring around the minutes tells how the next stop's time stands ([ring]: orange when it is
 * near, red when it has passed, beating when it is due or well past).
 * When the minute changes ([grow] 0 → 1), the time (hours, colon and minutes, as one) moves to the
 * middle of the screen and grows to five times its size, or as far as the screen allows, turning
 * to this minute's colour ([hue]); the seconds step back with the screen. A tap says the time.
 */
@Composable
private fun Clock(
    now: State<LocalTime>,
    minuteSize: TextUnit,
    grow: () -> Float,
    hue: Color,
    status: TimeStatus?,
    screen: () -> Rect,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = now.value
    val colon by animateFloatAsState(if (time.second % 2 == 0) 1f else 0.35f, tween(450), label = "colon")
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val description = stringResource(R.string.display_say_time)
    val ink = AppTheme.colors.text
    val highlight = AppTheme.colors.highlight
    val style = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        color = ink,
        fontFeatureSettings = TABULAR,
        lineHeight = 1.0.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    // The smaller hours with the seconds right under them sit level with the middle of the
    // minutes, so the clock is no taller than its minutes; the colon is level with the hours.
    val (lift, tuck) = with(LocalDensity.current) {
        (minuteSize * ((1f - HOUR_SHARE - SECOND_SHARE + SECONDS_TUCK) / 2f)).toDp() to (minuteSize * SECONDS_TUCK).toDp()
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
            .clickable(interactionSource = null, indication = null, onClickLabel = description, role = Role.Button) {
                scope.launch {
                    bounce.animateTo(1.08f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
                    bounce.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
                }
                onClick()
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = lift)) {
            BasicText(
                twoDigits(time.hour),
                style = style.copy(fontSize = minuteSize * HOUR_SHARE),
                color = { lerp(ink, hue, grow()) },
                modifier = Modifier
                    .onGloballyPositioned { hours = it.boundsInRoot() }
                    .growTogether(grow, own = { hours }, group = whole, area = screen),
            )
            AnimatedContent(
                targetState = twoDigits(time.second),
                transitionSpec = { (slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)) togetherWith fadeOut(tween(150))).using(SizeTransform(clip = false)) },
                label = "seconds",
                modifier = Modifier
                    .offset(y = -tuck)
                    .onGloballyPositioned { seconds = it.boundsInRoot() }
                    .growTogether(grow, own = { seconds }, group = whole, area = screen),
            ) { sec ->
                BasicText(
                    sec,
                    style = style.copy(fontSize = minuteSize * SECOND_SHARE, fontWeight = FontWeight.Light, letterSpacing = 0.06.em),
                    color = { lerp(highlight, hue, grow()) },
                )
            }
        }
        Text(
            ":",
            style = style.copy(fontSize = minuteSize * HOUR_SHARE, color = AppTheme.colors.accent),
            modifier = Modifier
                .offset(y = lift)
                .onGloballyPositioned { dots = it.boundsInRoot() }
                .growTogether(grow, own = { dots }, group = whole, area = screen)
                .alpha(colon),
        )
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
                OutlinedDigits(m, style.copy(fontSize = minuteSize), status, fill = { lerp(ink, hue, grow()) })
            }
        }
    }
}

/** The smallest rectangle around both. */
private fun span(a: Rect, b: Rect) = Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))

/**
 * Digits with an outline in [status]'s colour (green on time, orange soon, red late), beating when
 * it says so; plain digits for no status.
 */
@Composable
private fun OutlinedDigits(text: String, style: TextStyle, status: TimeStatus?, fill: ColorProducer, modifier: Modifier = Modifier) {
    val beat = rememberInfiniteTransition(label = "beat")
    val pulse by beat.animateFloat(1f, BEAT_LOW, infiniteRepeatable(tween(BEAT_MS), RepeatMode.Reverse), label = "beat")
    val line = status?.let { statusColor(it) }
    Box(modifier) {
        if (status != null && line != null) {
            // The stroke is centred on the glyphs' edge; the digits drawn over it leave its outer half.
            val width = with(LocalDensity.current) { (style.fontSize * OUTLINE_SHARE).toPx() }
            BasicText(
                text,
                style = style.copy(drawStyle = Stroke(width = width, join = StrokeJoin.Round)),
                color = { line.copy(alpha = if (status.beating) pulse else 1f) },
            )
        }
        BasicText(text, style = style, color = fill)
    }
}

@Composable
private fun statusColor(status: TimeStatus): Color = when (status) {
    TimeStatus.ON_TIME -> AppTheme.colors.success
    TimeStatus.SOON, TimeStatus.DUE -> AppTheme.colors.soon
    TimeStatus.LATE, TimeStatus.VERY_LATE -> AppTheme.colors.danger
}

/**
 * A time ("08:00") in the clock's style: the hours smaller and level with the middle of the
 * minutes, the colon in the accent yellow, the minutes outlined in [status]'s colour; the digits
 * breathe slowly.
 */
@Composable
private fun TimeFace(time: String, minuteSize: TextUnit, modifier: Modifier = Modifier, status: TimeStatus? = null) {
    val hour = time.substringBefore(':')
    val minute = time.substringAfter(':', "")
    val breath = rememberInfiniteTransition(label = "breath")
    val scale by breath.animateFloat(1f / BREATH_SCALE, 1f, infiniteRepeatable(tween(BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath scale")
    // Drawn at its largest and scaled down as one picture: the digits grow and shrink smoothly
    // (text scaled directly jumps between the sizes its glyphs are drawn at).
    val big = minuteSize * BREATH_SCALE
    val ink = AppTheme.colors.text
    val style = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        color = ink,
        fontFeatureSettings = TABULAR,
        lineHeight = 1.0.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.breathe(rest = 1f / BREATH_SCALE) { scale },
    ) {
        Text(hour, style = style.copy(fontSize = big * HOUR_SHARE))
        Text(":", style = style.copy(fontSize = big * HOUR_SHARE, color = AppTheme.colors.accent))
        OutlinedDigits(minute, style.copy(fontSize = big), status, fill = { ink })
    }
}

/**
 * Draws the content scaled by [scale] (at most 1) from the middle of its left edge, in a layer of
 * its own so it is scaled as a picture, and takes the room of it at [rest].
 */
private fun Modifier.breathe(rest: Float, scale: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity))
    val w = (placeable.width * rest).roundToInt()
    val h = (placeable.height * rest).roundToInt()
    layout(w, h) {
        placeable.placeWithLayer(0, (h - placeable.height) / 2) {
            val s = scale()
            scaleX = s
            scaleY = s
            transformOrigin = TransformOrigin(0f, 0.5f)
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }
}

/** "NÄSTA" (said "Nästa stopp") on a translucent yellow chip with a hairline edge. */
@Composable
private fun NextChip(landscape: Boolean) {
    val shape = RoundedCornerShape(50)
    Text(
        stringResource(R.string.passenger_next).uppercase(),
        fontFamily = DisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = if (landscape) 26.sp else 20.sp,
        letterSpacing = 3.sp,
        color = AppTheme.colors.onNextChip,
        modifier = Modifier
            .clip(shape)
            .background(AppTheme.colors.nextChip)
            .border(1.5.dp, AppTheme.colors.nextChipEdge, shape)
            .padding(horizontal = 18.dp, vertical = 3.dp),
    )
}

/** Lays this out above where it would go, taking no room in its column: it floats over what is above. */
private fun Modifier.floatAbove(gap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    layout(placeable.width, 0) { placeable.place(0, -placeable.height - gap.roundToPx()) }
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

/**
 * About when the announcement reaches "Därefter", counted from its start. The display does not
 * hear the voice, so this goes by the text's length at the usual speaking speed.
 */
private fun msUntilThen(text: String?): Long {
    val at = text?.indexOf("Därefter")?.takeIf { it > 0 } ?: return SAY_DELAY_MS + THEN_FALLBACK_MS
    return SAY_DELAY_MS + at * MS_PER_CHAR
}

/** About how long the whole announcement takes. */
private fun msToSay(text: String?): Long = SAY_DELAY_MS + (text?.length ?: 0).coerceAtLeast(30) * MS_PER_CHAR

private const val MIN_TITLE_SP = 32f
private const val MAX_TITLE_SP = 300f

/** The strip's cards: the first "Därefter" large, the others small (a share of the strip's width). */
private val FIRST_CARD_SP = 38.sp
private val FIRST_CARD_SP_NARROW = 30.sp
private val CARD_SP = 22.sp
private const val FIRST_CARD_SHARE = 0.34f
private const val FIRST_CARD_SHARE_NARROW = 0.72f
private const val CARD_SHARE = 0.2f
private const val CARD_SHARE_NARROW = 0.5f
private const val DONE_CARD_ALPHA = 0.6f
private val HOME_SIZE = 44.dp

/** A tapped card swells this much while its trip shows in the middle, for this long. */
private const val CARD_TAP_SWELL = 1.08f
private const val SHOW_TRIP_MS = 6_000L

/** The time above the address (its minutes; the hours are smaller), gently breathing. */
private val HERO_TIME_SP = 52.sp
private val HERO_TIME_SP_NARROW = 36.sp
private const val BREATH_SCALE = 1.2f
private const val BREATH_MS = 2_200

/** A trip paged to: its time comes in this much larger and settles. */
private const val PAGE_POP = 1.8f
private const val ENTER_MS = 450

/** The speech starts a moment after the tap, and a new stop first grows into place. */
private const val SAY_DELAY_MS = 700L

/** Swedish at the app's speaking speed: about 13 letters a second. */
private const val MS_PER_CHAR = 75L
private const val THEN_FALLBACK_MS = 3_000L

/** The spotlight: what is not being said steps back to this. */
private const val DIM = 0.4f
private const val DIM_MS = 600

/** The next stop grows slowly to at most this while it is said. */
private const val TITLE_FOCUS = 1.25f
private const val FOCUS_MS = 1_400

/** The "Därefter" card grows to this when the announcement says it, or to [LIFT_FILL] of the stage if that is less. */
private const val CARD_FOCUS = 1.6f
private const val LIFT_FILL = 0.96f
private const val CARD_GROW_MS = 1_600

/** Growing and holding; with the way back, a card is lit for four seconds. */
private const val CARD_LIT_MS = 2_400L
private const val NONE = -2

/** Where a stop sits in the middle: -1 top, 0 centre. */
private const val HERO_BIAS = -0.4f

/** The connection line on a tablet, beside the speaker. */
private val STATUS_MAX_WIDTH = 320.dp

/** Another stop paged to (or the strip scrolled) and left alone gives way to the next stop again. */
private const val BROWSE_RETURN_MS = 30_000L
private const val NEXT_STOP = -1

private val MINUTE_SP_WIDE = 180.sp
private const val MINUTE_SP_NARROW = 100f
private const val MINUTE_SP_MIN = 48f

/** The clock is about this many dp wide per sp of its minutes; beside it a phone keeps this much. */
private const val CLOCK_WIDTH_PER_SP = 1.85f
private val NARROW_LEFT_ROOM = 220.dp
private const val HOUR_SHARE = 0.62f
private const val SECOND_SHARE = 0.3f

/** The seconds tuck up this share of the minutes' size into the empty room under the hours' digits. */
private const val SECONDS_TUCK = 0.08f

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
 * The weather at this second of each minute, Google Maps' travel time at this one, each seven
 * seconds in all: in, held, out.
 */
private const val WEATHER_AT_S = 27
private const val ETA_AT_S = 45
private const val INFO_IN_MS = 1_200
private const val INFO_HOLD_MS = 4_600L
private const val INFO_OUT_MS = 1_200
private val INFO_SP_WIDE = 220.sp
private val INFO_SP_NARROW = 110.sp

/** The ring around the clock's minutes: its line, its glow, and how it beats. */
private const val OUTLINE_SHARE = 0.045f
private const val BEAT_LOW = 0.2f
private const val BEAT_MS = 700

/** How far the rest of the screen steps back while the time grows (before the solid ground). */
private const val REST_FADE = 0.85f

/** Digits of equal width, so times and the clock do not shift as they change. */
private const val TABULAR = "tnum"
