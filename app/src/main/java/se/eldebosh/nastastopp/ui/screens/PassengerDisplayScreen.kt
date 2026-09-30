package se.eldebosh.nastastopp.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedContent
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
 * - when the minute changes, the minutes grow into the middle of the screen over five seconds
 *   and go back faster (never while something is being said).
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
    // Taps on the clock or a card: said on this device, and the minutes keep still meanwhile.
    var said by remember { mutableIntStateOf(0) }
    val say: (Announcement) -> Unit = {
        said++
        onSay(it)
    }
    val now = rememberNow(time)
    val grow = rememberMinuteGrowth(now, cue + said, snapshot?.announcementSv)
    // Everything but the minutes steps back while they grow.
    val rest = Modifier.stepBack { grow.value }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val landscape = maxWidth > maxHeight
        var screen by remember { mutableStateOf(Rect.Zero) }
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { screen = it.boundsInRoot() }
                .padding(horizontal = if (landscape) 32.dp else 20.dp, vertical = 12.dp),
        ) {
            val live = snapshot?.takeIf { it.active && it.current != null }
            // Drawn over what follows, so the growing minutes pass in front of it.
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().zIndex(1f)) {
                Column(rest.weight(1f)) {
                    TopLine(status, connected, onExit, extraActions)
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
                    // The trip just done, beside the clock: one quiet line, so the passengers see
                    // the list moving on.
                    if (landscape) live?.previous?.let { PreviousLine(it, Modifier.ref(89).padding(top = 16.dp)) }
                }
                Clock(
                    now = now,
                    landscape = landscape,
                    grow = { grow.value },
                    screen = { screen },
                    onClick = { say(Announcements.clock(now.value.hour, now.value.minute)) },
                    modifier = Modifier.ref(88).padding(start = 16.dp),
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

            Box(rest.weight(1f).fillMaxWidth()) {
                Stage(
                    live,
                    landscape,
                    cue,
                    onSay = say,
                    modifier = Modifier.fillMaxSize().padding(end = if (landscape) SPEAKER_ROOM else 0.dp, bottom = if (landscape) 0.dp else SPEAKER_ROOM),
                )
                SpeakerButton(
                    speakingText = live.announcementSv,
                    cue = cue,
                    onSpeak = {
                        tapped++
                        onSpeak()
                    },
                    modifier = Modifier.align(Alignment.BottomEnd),
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
 * 0 → 1 → 0 each time the minute changes: the minutes grow for five seconds, then go back faster.
 * Nothing grows while something is being said ([cue] goes up with each announcement and tap), and
 * a new cue sends the minutes straight back.
 */
@Composable
private fun rememberMinuteGrowth(now: State<LocalTime>, cue: Int, spokenText: String?): Animatable<Float, AnimationVector1D> {
    val grow = remember { Animatable(0f) }
    var quiet by remember { mutableStateOf(false) }
    LaunchedEffect(cue) {
        if (cue == 0) return@LaunchedEffect
        quiet = true
        grow.animateTo(0f, tween(SETTLE_MS))
        delay(msToSay(spokenText))
        quiet = false
    }
    val minute by remember { derivedStateOf { now.value.hour * 60 + now.value.minute } }
    val opened = remember { minute }
    LaunchedEffect(minute) {
        if (minute == opened || quiet) return@LaunchedEffect
        grow.animateTo(1f, tween(GROW_MS, easing = FastOutSlowInEasing))
        grow.animateTo(0f, tween(SHRINK_MS, easing = FastOutLinearInEasing))
    }
    return grow
}

/**
 * The next stop and the following trips, changing together: the new next stop grows out of its
 * "Därefter" card. A spotlight follows what is said.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Stage(snapshot: DisplaySnapshot, landscape: Boolean, cue: Int, onSay: (Announcement) -> Unit, modifier: Modifier = Modifier) {
    SharedTransitionLayout(modifier) {
        AnimatedContent(
            targetState = snapshot,
            contentKey = { it.current },
            transitionSpec = {
                (fadeIn(tween(ENTER_MS, delayMillis = 150)) togetherWith fadeOut(tween(250)) + scaleOut(tween(300), targetScale = 0.92f))
                    .using(SizeTransform(clip = false))
            },
            label = "next stop",
        ) { shown ->
            val current = shown.current ?: return@AnimatedContent
            val shared = SharedStop(this@SharedTransitionLayout, this@AnimatedContent)
            val spotlight = rememberSpotlight()
            // The announcement: first the next stop, then the "Därefter" card when its name comes.
            LaunchedEffect(cue) {
                if (cue == 0) return@LaunchedEffect
                spotlight.play {
                    delay(SAY_DELAY_MS) // after a change of stop, once the new one has grown into place
                    on = NEXT_STOP
                    delay(msUntilThen(shown.announcementSv) - SAY_DELAY_MS)
                    if (shown.upcoming.isNotEmpty()) {
                        on = 0
                        delay(CARD_LIT_MS)
                    }
                }
            }
            val spot = spotlight.on
            Column(Modifier.fillMaxSize()) {
                NextStop(current, landscape, focused = spot == NEXT_STOP, dimmed = spot >= 0, shared, Modifier.weight(1f).fillMaxWidth())
                if (shown.upcoming.isNotEmpty()) {
                    ThenLabel()
                    val card = @Composable { i: Int, item: DisplayItem, modifier: Modifier ->
                        UpcomingCard(
                            item,
                            first = i == 0,
                            landscape = landscape,
                            lit = spot == i,
                            dimmed = spot != NONE && spot != i,
                            // Side by side, each card grows into the screen, never over its edge.
                            origin = if (landscape) TransformOrigin(i / (MAX_UPCOMING - 1f), 1f) else TransformOrigin.Center,
                            onClick = {
                                spotlight.play {
                                    on = i
                                    delay(CARD_LIT_MS)
                                }
                                onSay(Announcements.following(listOfNotNull(item.title, item.subtitle).joinToString(", ")))
                            },
                            shared = shared,
                            modifier = modifier,
                        )
                    }
                    if (landscape) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            shown.upcoming.forEachIndexed { i, item -> card(i, item, Modifier.ref(94).weight(if (i == 0) FIRST_CARD_WEIGHT else 1f)) }
                            // Fewer trips left: the cards keep their width.
                            repeat(MAX_UPCOMING - shown.upcoming.size) { Spacer(Modifier.weight(1f)) }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            shown.upcoming.forEachIndexed { i, item -> card(i, item, Modifier.ref(94).fillMaxWidth()) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * What the screen points at while something is said: the next stop ([NEXT_STOP]), a following
 * trip's card (its index) or nothing ([NONE]). A new [play] replaces the one running.
 */
@Stable
private class Spotlight(private val scope: CoroutineScope) {
    var on by mutableIntStateOf(NONE)
    private var job: Job? = null

    fun play(steps: suspend Spotlight.() -> Unit) {
        job?.cancel()
        on = NONE
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
    /** The same trip, as a card or as the next stop, keeps its place in the transition. */
    @Composable
    fun Modifier.stop(item: DisplayItem): Modifier = with(transition) {
        this@stop.sharedBounds(
            rememberSharedContentState(key = item),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow) },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
        )
    }
}

/** Exit (small and quiet: it is for the driver) and the connection line. */
@Composable
private fun TopLine(status: String?, connected: Boolean, onExit: () -> Unit, extraActions: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onExit, modifier = Modifier.refCorner(86).size(TouchTarget)) {
            Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.display_exit), tint = AppTheme.colors.textMuted, modifier = Modifier.size(20.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
    }
}

@Composable
private fun PreviousLine(item: DisplayItem, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().alpha(0.55f)) {
        Icon(painterResource(R.drawable.ic_located), contentDescription = null, tint = AppTheme.colors.success, modifier = Modifier.size(20.dp))
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
        )
    }
}

/**
 * The next stop: "NÄSTA STOPP" on a highlight chip and its time in the highlight colour, the
 * street and number as large as fits (at most two lines, never breaking a word), and the area
 * under it. While it is [focused], the label springs up and settles, and the address slowly grows
 * and lights up; while something else is said, it is [dimmed].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NextStop(current: DisplayItem, landscape: Boolean, focused: Boolean, dimmed: Boolean, shared: SharedStop, modifier: Modifier = Modifier) {
    val labelScale = remember { Animatable(1f) }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        labelScale.animateTo(1.35f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        labelScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
    }
    // How far the address may grow and stay on the screen: less for a long one.
    var room by remember { mutableFloatStateOf(TITLE_FOCUS) }
    val scale by animateFloatAsState(if (focused) room else 1f, tween(FOCUS_MS, easing = FastOutSlowInEasing), label = "focus")
    val shade by animateFloatAsState(if (dimmed) DIM else 1f, tween(DIM_MS), label = "dim")
    val titleColor by animateColorAsState(if (focused) AppTheme.colors.highlight else AppTheme.colors.text, tween(FOCUS_MS / 2), label = "street")
    with(shared) {
        Column(
            modifier.stop(current).graphicsLayer { alpha = shade },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.ref(90).graphicsLayer {
                    scaleX = labelScale.value
                    scaleY = labelScale.value
                },
            ) {
                Text(
                    stringResource(R.string.passenger_next_stop).uppercase(),
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (landscape) 34.sp else 26.sp,
                    letterSpacing = 1.5.sp,
                    color = AppTheme.colors.onInfo,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(AppTheme.colors.highlight).padding(horizontal = 22.dp, vertical = 2.dp),
                )
                if (current.time != null) {
                    Spacer(Modifier.width(18.dp))
                    Text(
                        current.time,
                        fontFamily = DisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (landscape) 50.sp else 36.sp,
                        color = AppTheme.colors.highlight,
                        style = TextStyle(fontFeatureSettings = TABULAR),
                    )
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
                            if (line > 0f) room = (layout.size.width * TITLE_ROOM / line).coerceIn(1f, TITLE_FOCUS)
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
 * A following trip: its time and street and number, the area under them. The [first] one is the
 * "Därefter" of the announcement: larger and edged in the highlight colour. While it is [lit]
 * (its name is said), it grows well past its size from [origin] and takes a highlight tint, then
 * settles back; while something else is said, it is [dimmed]. A tap says it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun UpcomingCard(
    item: DisplayItem,
    first: Boolean,
    landscape: Boolean,
    lit: Boolean,
    dimmed: Boolean,
    origin: TransformOrigin,
    onClick: () -> Unit,
    shared: SharedStop,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        if (!lit) 1f else if (landscape) CARD_FOCUS else CARD_FOCUS_NARROW,
        tween(CARD_GROW_MS, easing = FastOutSlowInEasing),
        label = "card",
    )
    val shade by animateFloatAsState(if (dimmed) DIM else 1f, tween(DIM_MS), label = "dim")
    val shape = RoundedCornerShape(18.dp)
    val background by animateColorAsState(if (lit) AppTheme.colors.infoSoft else AppTheme.colors.card, tween(600), label = "card tint")
    val titleSp = if (first) FIRST_CARD_SP else CARD_SP
    val description = stringResource(R.string.display_say_trip)
    with(shared) {
        Column(
            modifier
                // A growing card passes in front of its neighbours.
                .zIndex(if (lit || scale > 1f) 1f else 0f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = origin
                    alpha = shade
                }
                .stop(item)
                .clip(shape)
                .background(background)
                .border(if (first) 2.dp else 1.dp, if (first) AppTheme.colors.highlight else AppTheme.colors.cardBorder, shape)
                .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = if (first && landscape) 12.dp else 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.time != null) {
                    Text(
                        item.time,
                        fontFamily = DisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSp,
                        color = if (first) AppTheme.colors.highlight else AppTheme.colors.time,
                        style = TextStyle(fontFeatureSettings = TABULAR),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    item.title,
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.SemiBold,
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
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = AppTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content),
                )
            }
        }
    }
}

/**
 * Repeats the announcement on this device: a speaker in a yellow circle. While an announcement
 * is spoken, a ring widens around it again and again.
 */
@Composable
private fun SpeakerButton(speakingText: String?, cue: Int, onSpeak: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.display_repeat)
    var speaking by remember { mutableStateOf(false) }
    LaunchedEffect(cue) {
        if (cue == 0) return@LaunchedEffect
        speaking = true
        delay(msToSay(speakingText))
        speaking = false
    }
    val ring = rememberInfiniteTransition(label = "speaking")
    val wave by ring.animateFloat(0f, 1f, infiniteRepeatable(tween(1_100), RepeatMode.Restart), label = "wave")
    val accent = AppTheme.colors.accent
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .refCorner(93)
            .size(SPEAKER_SIZE)
            .drawBehind {
                if (speaking) {
                    val r = size.minDimension / 2 * (1f + 0.45f * wave)
                    drawCircle(accent.copy(alpha = 0.45f * (1f - wave)), radius = r)
                }
            }
            .clip(CircleShape)
            .background(accent)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onSpeak)
            .semantics { contentDescription = description },
    ) {
        Icon(painterResource(R.drawable.ic_speaker), contentDescription = null, tint = AppTheme.colors.onAccent, modifier = Modifier.size(30.dp))
    }
}

/**
 * The clock, without a frame: the minutes large, the hours smaller and raised beside them, the
 * seconds thin under the minutes in the highlight colour, and the colon beating with the seconds.
 * When the minute changes ([grow] 0 → 1), the minutes move to the middle of the screen and grow
 * to five times their size, or as far as the screen allows, turning to the highlight colour; the
 * rest of the clock steps back with the screen. A tap says the time.
 */
@Composable
private fun Clock(now: State<LocalTime>, landscape: Boolean, grow: () -> Float, screen: () -> Rect, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val time = now.value
    val minuteSize = if (landscape) MINUTE_SP_WIDE else MINUTE_SP_NARROW
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
    // The seconds tuck up under the digits, into the empty room below them, and the smaller hours
    // come down so that their tops line up with the minutes'.
    val (tuck, drop) = with(LocalDensity.current) { (minuteSize * SECONDS_TUCK).toDp() to (minuteSize * (1f - HOUR_SHARE) * TOP_ROOM).toDp() }
    var minutes by remember { mutableStateOf(Rect.Zero) }
    val rest = Modifier.stepBack(grow)
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
        Text(twoDigits(time.hour), style = style.copy(fontSize = minuteSize * HOUR_SHARE), modifier = Modifier.offset(y = drop).then(rest))
        Text(":", style = style.copy(fontSize = minuteSize * HOUR_SHARE, color = highlight), modifier = Modifier.offset(y = drop).then(rest).alpha(colon))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .onGloballyPositioned { minutes = it.boundsInRoot() }
                    .graphicsLayer {
                        val p = grow()
                        val area = screen()
                        if (p > 0f && !minutes.isEmpty && !area.isEmpty) {
                            val most = minOf(MINUTE_GROWTH, area.height * MINUTE_FILL / minutes.height, area.width * MINUTE_FILL / minutes.width)
                            val s = 1f + (most - 1f) * p
                            scaleX = s
                            scaleY = s
                            translationX = (area.center.x - minutes.center.x) * p
                            translationY = (area.center.y - minutes.center.y) * p
                        }
                    },
            ) {
                AnimatedContent(
                    targetState = twoDigits(time.minute),
                    transitionSpec = { slideInVertically(tween(450)) { -it } + fadeIn(tween(450)) togetherWith slideOutVertically(tween(450)) { it } + fadeOut(tween(300)) },
                    label = "minutes",
                ) { m ->
                    BasicText(m, style = style.copy(fontSize = minuteSize), color = { lerp(ink, highlight, grow()) })
                }
            }
            AnimatedContent(
                targetState = twoDigits(time.second),
                transitionSpec = { (slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)) togetherWith fadeOut(tween(150))).using(SizeTransform(clip = false)) },
                label = "seconds",
                modifier = Modifier.offset(y = -tuck).then(rest),
            ) { sec ->
                Text(sec, style = style.copy(fontSize = minuteSize * SECOND_SHARE, fontWeight = FontWeight.Light, color = highlight, letterSpacing = 0.06.em))
            }
        }
    }
}

/**
 * Fades what is not the minutes while they grow ([grow] 0 → 1). The alpha goes to each drawing
 * rather than through a layer of its own, so nothing that reaches past its box is cut off.
 */
private fun Modifier.stepBack(grow: () -> Float): Modifier = graphicsLayer {
    alpha = 1f - REST_FADE * grow()
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
private const val MAX_UPCOMING = 3
private const val FIRST_CARD_WEIGHT = 1.35f
private val FIRST_CARD_SP = 32.sp
private val CARD_SP = 26.sp
private val SPEAKER_SIZE = 64.dp
private val SPEAKER_ROOM = 80.dp
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

/** The address may grow a little past its box, into the screen's margins. */
private const val TITLE_ROOM = 1.06f
private const val CARD_FOCUS = 1.6f

/** One card under another fills the width already: it only swells. */
private const val CARD_FOCUS_NARROW = 1.08f
private const val CARD_GROW_MS = 1_600

/** Growing and holding; with the way back, a card is lit for four seconds. */
private const val CARD_LIT_MS = 2_400L
private const val NONE = -2
private const val NEXT_STOP = -1

/** Twice the size the clock had with its hours large. */
private val MINUTE_SP_WIDE = 240.sp
private val MINUTE_SP_NARROW = 128.sp
private const val HOUR_SHARE = 0.62f
private const val SECOND_SHARE = 0.3f
private const val SECONDS_TUCK = 0.2f

/** The room above a digit in its line, as a share of the font size. */
private const val TOP_ROOM = 0.24f

/** The minutes grow to five times their size, or to this share of the screen if that is less. */
private const val MINUTE_GROWTH = 5f
private const val MINUTE_FILL = 0.9f
private const val GROW_MS = 5_000
private const val SHRINK_MS = 1_200
private const val SETTLE_MS = 300

/** How far the rest of the screen steps back while the minutes grow. */
private const val REST_FADE = 0.85f

/** Digits of equal width, so times and the clock do not shift as they change. */
private const val TABULAR = "tnum"
