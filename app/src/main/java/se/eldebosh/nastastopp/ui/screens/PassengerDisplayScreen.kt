package se.eldebosh.nastastopp.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.display.DisplayItem
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.AppTheme
import se.eldebosh.nastastopp.ui.theme.DisplayFont
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Passenger display: made to be read by the passengers from their seats, while the driver works
 * it from the phone. The next stop fills the middle (street and number as large as the screen
 * allows, its area and time), the clock is large in the corner, the following trips sit below it
 * as cards, and a "Lyssna" button repeats the announcement. The words for the passengers are
 * Swedish, like the announcements; the connection line (for the driver) follows the app's language.
 *
 * Landscape (a tablet): the following trips side by side with the button beside them. Portrait
 * (the driver's phone): one below the other.
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
) {
    KeepScreenOnFullscreen()
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

            val current = snapshot?.current
            if (snapshot == null || !snapshot.active || current == null) {
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

            NextStop(current, landscape, Modifier.weight(1f).fillMaxWidth())

            if (landscape) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        if (snapshot.upcoming.isNotEmpty()) ThenLabel()
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            snapshot.upcoming.forEach { UpcomingCard(it, Modifier.ref(94).weight(1f)) }
                            // Fewer trips left: the cards keep their width.
                            repeat(MAX_UPCOMING - snapshot.upcoming.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    ListenButton(onSpeak)
                }
            } else {
                ListenButton(onSpeak, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp))
                if (snapshot.upcoming.isNotEmpty()) ThenLabel()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.upcoming.forEach { UpcomingCard(it, Modifier.ref(94).fillMaxWidth()) }
                }
            }
        }
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
        Clock(Modifier.ref(88).padding(start = 16.dp), fontSize = if (landscape) 60.sp else 44.sp)
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
 * The next stop: "Nästa stopp" and its time on one line, the street and number as large as fits
 * (at most two lines, never breaking a word), and the area under it.
 */
@Composable
private fun NextStop(current: DisplayItem, landscape: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.ref(90)) {
            Text(
                stringResource(R.string.passenger_next_stop).uppercase(),
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = if (landscape) 34.sp else 26.sp,
                letterSpacing = 1.sp,
                color = AppTheme.colors.onAccent,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(AppTheme.colors.accent).padding(horizontal = 22.dp, vertical = 2.dp),
            )
            if (current.time != null) {
                Spacer(Modifier.width(18.dp))
                Text(
                    current.time,
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (landscape) 46.sp else 34.sp,
                    color = AppTheme.colors.text,
                    style = TextStyle(fontFeatureSettings = TABULAR),
                )
            }
        }
        BoxWithConstraints(Modifier.ref(91).weight(1f, fill = false).fillMaxWidth()) {
            val style = TextStyle(
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.text,
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
                if (widest <= 0) MAX_TITLE_SP else (100f * width / widest * 0.97f).coerceIn(MIN_TITLE_SP, MAX_TITLE_SP)
            }
            BasicText(
                text = current.title,
                style = style,
                maxLines = 2,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_TITLE_SP.sp, maxFontSize = maxSp.sp, stepSize = 2.sp),
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

@Composable
private fun ThenLabel() {
    Text(
        stringResource(R.string.passenger_then).uppercase(),
        fontFamily = DisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 1.5.sp,
        color = AppTheme.colors.textMuted,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

/** A following trip: its time and street and number, the area under them. */
@Composable
private fun UpcomingCard(item: DisplayItem, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(AppTheme.colors.card)
            .border(1.dp, AppTheme.colors.cardBorder, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.time != null) {
                Text(
                    item.time,
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = UPCOMING_SP,
                    color = AppTheme.colors.time,
                    style = TextStyle(fontFeatureSettings = TABULAR),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                item.title,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = UPCOMING_SP,
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

/** Repeats the announcement on this device: a large, labelled button anyone can find. */
@Composable
private fun ListenButton(onSpeak: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.passenger_listen)
    val description = stringResource(R.string.display_repeat)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .refCorner(93)
            .heightIn(min = 76.dp)
            .widthIn(min = 180.dp)
            .clip(RoundedCornerShape(50))
            .background(AppTheme.colors.accent)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onSpeak)
            .semantics { contentDescription = description }
            .padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_speaker), contentDescription = null, tint = AppTheme.colors.onAccent, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = AppTheme.colors.onAccent)
    }
}

@Composable
fun Clock(modifier: Modifier = Modifier, fontSize: TextUnit = 56.sp) {
    val format = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var now by remember { mutableStateOf(LocalTime.now().format(format)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now().format(format)
            delay(1_000)
        }
    }
    Text(
        now,
        modifier = modifier,
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        color = AppTheme.colors.text,
        style = TextStyle(fontFeatureSettings = TABULAR),
    )
}

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

private const val MIN_TITLE_SP = 32f
private const val MAX_TITLE_SP = 300f
private const val MAX_UPCOMING = 3
private val UPCOMING_SP = 28.sp

/** Digits of equal width, so times and the clock do not shift as they change. */
private const val TABULAR = "tnum"
