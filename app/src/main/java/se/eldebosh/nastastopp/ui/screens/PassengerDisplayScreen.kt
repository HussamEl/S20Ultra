package se.eldebosh.nastastopp.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.core.display.DisplayItem
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.Brand
import se.eldebosh.nastastopp.ui.theme.TimeColor
import se.eldebosh.nastastopp.ui.theme.Located
import se.eldebosh.nastastopp.ui.theme.NotLocated
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Passenger display: the next destination fills the screen, one previous trip (small, faded) above
 * it, three upcoming trips (small) below, and a speaker button that repeats the announcement.
 * Used on the driver's phone itself and on a Bluetooth-connected tablet.
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
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0F17), Color(0xFF05070B))))
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        // Top: exit, connection status, clock.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onExit, modifier = Modifier.refCorner(86).size(TouchTarget)) {
                Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.display_exit), tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
            }
            if (status != null) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(if (connected) Located else NotLocated, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.ref(87),
                )
            }
            Spacer(Modifier.weight(1f))
            extraActions()
            Clock(Modifier.ref(88).padding(start = 12.dp))
        }
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.ref(95).padding(start = 12.dp),
            )
        }

        val current = snapshot?.current
        if (snapshot == null || !snapshot.active || current == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.display_waiting_route),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        // One previous trip: small and faded.
        snapshot.previous?.let { TripLine(it, fontSize = 22, alpha = 0.4f, modifier = Modifier.ref(89).padding(top = 4.dp)) }

        // Next destination: as large as the screen allows.
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.ref(90)) {
                Text(stringResource(R.string.display_next_label), color = Brand, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                if (current.time != null) {
                    Spacer(Modifier.width(14.dp))
                    Text(current.time, color = TimeColor, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
            BoxWithConstraints(Modifier.ref(91).weight(1f, fill = false).fillMaxWidth()) {
                val style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    textDirection = TextDirection.Content,
                )
                // Never larger than the size at which the longest word still fits on one line,
                // so a street name is not split in the middle ("Brattgård-sgatan").
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
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.ref(92),
                )
            }
            Spacer(Modifier.height(12.dp))
            FilledIconButton(
                onClick = onSpeak,
                modifier = Modifier.refCorner(93).size(80.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Brand, contentColor = Color(0xFF06142B)),
            ) {
                Icon(painterResource(R.drawable.ic_speaker), contentDescription = stringResource(R.string.display_repeat), modifier = Modifier.size(40.dp))
            }
        }

        // Three upcoming trips: small.
        snapshot.upcoming.forEach { TripLine(it, fontSize = 24, alpha = 0.85f, modifier = Modifier.ref(94).padding(vertical = 2.dp)) }
        Spacer(Modifier.height(8.dp))
    }
}

private const val MIN_TITLE_SP = 32f
private const val MAX_TITLE_SP = 280f

@Composable
private fun TripLine(item: DisplayItem, fontSize: Int, alpha: Float, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().alpha(alpha)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.time != null) {
                Text(item.time, color = TimeColor, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                item.title,
                color = Color.White,
                fontSize = fontSize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Content),
            )
        }
        item.subtitle?.let {
            Text(it, color = Color.White, fontSize = (fontSize * 0.7f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Content))
        }
    }
}

@Composable
fun Clock(modifier: Modifier = Modifier, fontSize: Int = 26) {
    val format = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var now by remember { mutableStateOf(LocalTime.now().format(format)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now().format(format)
            delay(1_000)
        }
    }
    Text(now, modifier = modifier, color = Color.White.copy(alpha = 0.7f), fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold)
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
