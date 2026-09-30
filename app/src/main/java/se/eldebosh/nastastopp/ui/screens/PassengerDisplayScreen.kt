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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.display.DisplayItem
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.AppTheme
import se.eldebosh.nastastopp.ui.theme.DisplayFont
import java.time.LocalTime
import java.util.Locale

/**
 * Passenger display: made to be read by the passengers from their seats, while the driver works
 * it from the phone. The next stop fills the middle, the clock is large in the corner and the
 * following trips sit below it as cards, the first one ("Därefter") marked. The words for the
 * passengers are Swedish, like the announcements; the connection line (for the driver) follows
 * the app's language.
 *
 * It moves with the route:
 * - when the driver taps Next, the "Därefter" card rises from its place and grows into the new
 *   next stop (a shared-bounds transition; Back runs it the other way);
 * - while an announcement is spoken ([spoken] goes up by one each time, and a tap on the speaker
 *   counts too), "NÄSTA STOPP" and its time grow and settle, the street lights up, and then the
 *   "Därefter" card does the same when its name comes;
 * - the clock's colon beats with the seconds, its minutes roll over, and a thin line fills with
 *   the minute.
 *
 * Landscape (a tablet): the following trips side by side. Portrait (the phone): one below the other.
 *
 * @param status connection line for a remote display (null on the driver's own device).
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
) {
    KeepScreenOnFullscreen()
    var tapped by remember { mutableIntStateOf(0) }
    val cue = spoken + tapped
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize().padding(horizontal = if (landscape) 32.dp else 20.dp, vertical = 12.dp)) {
            TopLine(status, connected, landscape, onExit, extraActions)
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

            if (snapshot == null || !snapshot.active || snapshot.current == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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

            // The trip just done: one quiet line, so the passengers see the list moving on.
            snapshot.previous?.let { PreviousLine(it, Modifier.ref(89).padding(top = 4.dp)) }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Stage(snapshot, landscape, cue, Modifier.fillMaxSize().padding(end = if (landscape) SPEAKER_ROOM else 0.dp, bottom = if (landscape) 0.dp else SPEAKER_ROOM))
                SpeakerButton(
                    speakingText = snapshot.announcementSv,
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

/**
 * The next stop and the following trips, changing together: the new next stop grows out of its
 * "Därefter" card.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Stage(snapshot: DisplaySnapshot, landscape: Boolean, cue: Int, modifier: Modifier = Modifier) {
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
            Column(Modifier.fillMaxSize()) {
                NextStop(current, landscape, cue, shown.announcementSv, shared, Modifier.weight(1f).fillMaxWidth())
                if (shown.upcoming.isNotEmpty()) {
                    ThenLabel()
                    if (landscape) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            shown.upcoming.forEachIndexed { i, item ->
                                UpcomingCard(item, first = i == 0, landscape, cue, shown.announcementSv, shared, Modifier.ref(94).weight(if (i == 0) FIRST_CARD_WEIGHT else 1f))
                            }
                            // Fewer trips left: the cards keep their width.
                            repeat(MAX_UPCOMING - shown.upcoming.size) { Spacer(Modifier.weight(1f)) }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            shown.upcoming.forEachIndexed { i, item ->
                                UpcomingCard(item, first = i == 0, landscape, cue, shown.announcementSv, shared, Modifier.ref(94).fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
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

/** Exit (small and quiet: it is for the driver), the connection line, and the clock (large). */
@Composable
private fun TopLine(status: String?, connected: Boolean, landscape: Boolean, onExit: () -> Unit, extraActions: @Composable () -> Unit) {
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
        Clock(Modifier.ref(88).padding(start = 16.dp), hourSize = if (landscape) 120.sp else 64.sp)
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
 * The next stop: "NÄSTA STOPP" and its time in the highlight colour, the street and number as
 * large as fits (at most two lines, never breaking a word), and the area under it. While it is
 * announced, the label grows and settles and the street lights up.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NextStop(current: DisplayItem, landscape: Boolean, cue: Int, spokenText: String?, shared: SharedStop, modifier: Modifier = Modifier) {
    val labelScale = remember { Animatable(1f) }
    val titleScale = remember { Animatable(1f) }
    var lit by remember { mutableStateOf(false) }
    LaunchedEffect(cue) {
        if (cue == 0) return@LaunchedEffect
        delay(SAY_DELAY_MS) // after a change of stop, once the new one has grown into place
        lit = true
        launch {
            labelScale.animateTo(1.35f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
            labelScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
        }
        titleScale.animateTo(1.06f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
        titleScale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow))
        delay(msUntilThen(spokenText) - SAY_DELAY_MS)
        lit = false
    }
    val titleColor by animateColorAsState(if (lit) AppTheme.colors.highlight else AppTheme.colors.text, tween(500), label = "street")
    with(shared) {
        Column(
            modifier.stop(current),
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
                    color = AppTheme.colors.highlight,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(AppTheme.colors.accentSoft).padding(horizontal = 22.dp, vertical = 2.dp),
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
                    modifier = Modifier.fillMaxWidth().scale(titleScale.value),
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
 * "Därefter" of the announcement: larger, edged in the highlight colour, and it lights up when
 * its name is said.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun UpcomingCard(
    item: DisplayItem,
    first: Boolean,
    landscape: Boolean,
    cue: Int,
    spokenText: String?,
    shared: SharedStop,
    modifier: Modifier = Modifier,
) {
    val pulse = remember { Animatable(1f) }
    var lit by remember { mutableStateOf(false) }
    if (first) {
        LaunchedEffect(cue) {
            if (cue == 0) return@LaunchedEffect
            delay(msUntilThen(spokenText))
            lit = true
            pulse.animateTo(1.1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
            pulse.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
            delay(THEN_LIT_MS)
            lit = false
        }
    }
    val shape = RoundedCornerShape(18.dp)
    val background by animateColorAsState(if (lit) AppTheme.colors.accentSoft else AppTheme.colors.card, tween(400), label = "then card")
    val titleSp = if (first) FIRST_CARD_SP else CARD_SP
    with(shared) {
        Column(
            modifier
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                }
                .stop(item)
                .clip(shape)
                .background(background)
                .border(if (first) 2.dp else 1.dp, if (first) AppTheme.colors.highlight else AppTheme.colors.cardBorder, shape)
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
 * The clock: the hours large, the colon beating with the seconds, the minutes smaller and raised,
 * rolling over when they change, and a thin line under it that fills with the minute.
 */
@Composable
fun Clock(modifier: Modifier = Modifier, hourSize: TextUnit = 120.sp) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }
    val colon by animateFloatAsState(if (now.second % 2 == 0) 1f else 0.35f, tween(450), label = "colon")
    val second = now.second / 60f
    val progress by animateFloatAsState(second, if (second == 0f) snap() else tween(900), label = "minute")
    val style = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        color = AppTheme.colors.text,
        fontFeatureSettings = TABULAR,
        lineHeight = 1.0.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    val track = AppTheme.colors.cardBorder
    val fill = AppTheme.colors.highlight
    val minuteSize = hourSize * 0.62f
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(AppTheme.colors.card)
            .border(1.dp, AppTheme.colors.cardBorder, RoundedCornerShape(26.dp))
            .drawBehind {
                val h = 5.dp.toPx()
                val inset = 18.dp.toPx()
                val y = size.height - h - 8.dp.toPx()
                val w = size.width - 2 * inset
                drawRoundRect(track, Offset(inset, y), Size(w, h), CornerRadius(h / 2))
                drawRoundRect(fill, Offset(inset, y), Size(w * progress, h), CornerRadius(h / 2))
            }
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 20.dp),
    ) {
        Text(twoDigits(now.hour), style = style.copy(fontSize = hourSize))
        Text(":", style = style.copy(fontSize = hourSize, color = AppTheme.colors.highlight), modifier = Modifier.alpha(colon))
        AnimatedContent(
            targetState = twoDigits(now.minute),
            transitionSpec = { slideInVertically(tween(450)) { -it } + fadeIn(tween(450)) togetherWith slideOutVertically(tween(450)) { it } + fadeOut(tween(300)) },
            label = "minutes",
        ) { m ->
            Text(m, style = style.copy(fontSize = minuteSize), modifier = Modifier.padding(top = 4.dp))
        }
    }
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
private const val THEN_LIT_MS = 2_200L

/** Digits of equal width, so times and the clock do not shift as they change. */
private const val TABULAR = "tnum"
