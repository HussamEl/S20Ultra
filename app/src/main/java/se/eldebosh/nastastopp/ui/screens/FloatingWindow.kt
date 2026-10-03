package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.settings.WindowPlace
import se.eldebosh.nastastopp.settings.WindowPlaces
import se.eldebosh.nastastopp.ui.refCorner
import kotlin.math.roundToInt

/**
 * A window over the display that the driver moves and sizes ([FloatingWindow]): where it is and
 * how big, kept by [name] in [places] each time he lets go, so it opens as he last left it; else
 * at [default].
 */
@Stable
internal class WindowState(private val name: String, private val places: WindowPlaces, default: WindowPlace) {
    var place by mutableStateOf(places.place(name) ?: default)
        private set

    // The window's and the screen's sizes at their last layout: the window stays inside the screen.
    private var size = IntSize.Zero
    private var screen = IntSize.Zero

    /** Where the window's top left goes: its middle at [place], moved in as far as the screen needs. */
    fun corner(size: IntSize, screen: IntSize): IntOffset {
        this.size = size
        this.screen = screen
        return IntOffset(edge(place.x, size.width, screen.width).roundToInt(), edge(place.y, size.height, screen.height).roundToInt())
    }

    /** Moved by ([dx], [dy]) pixels, never out of the screen. */
    fun moveBy(dx: Float, dy: Float) {
        if (screen.width == 0 || screen.height == 0) return
        place = place.copy(x = share(place.x, dx, size.width, screen.width), y = share(place.y, dy, size.height, screen.height))
    }

    /** [factor] times as big, between [WindowPlace.SMALLEST] and [WindowPlace.LARGEST]; its middle stays. */
    fun zoomBy(factor: Float) {
        place = place.copy(scale = (place.scale * factor).coerceIn(WindowPlace.SMALLEST, WindowPlace.LARGEST))
    }

    /** Kept as it is now, to open so next time. */
    fun keep() = places.keep(name, place)

    private fun edge(share: Float, size: Int, screen: Int): Float =
        (share * screen - size / 2f).coerceIn(0f, (screen - size).coerceAtLeast(0).toFloat())

    private fun share(share: Float, by: Float, size: Int, screen: Int): Float {
        val edge = (edge(share, size, screen) + by).coerceIn(0f, (screen - size).coerceAtLeast(0).toFloat())
        return (edge + size / 2f) / screen
    }
}

/**
 * [content] as a window over the whole screen, placed and sized by [state]: two fingers anywhere on
 * it move it and pinch it bigger or smaller (one finger still scrolls and taps inside it), its bar
 * moves it ([movesWindow]), and its − and + size it ([WindowZoom]). It is drawn at its size, never
 * stretched: its text stays sharp. Each touch on it is told ([onTouch]); touches beside it go
 * through to what is under it.
 */
@Composable
internal fun FloatingWindow(state: WindowState, modifier: Modifier = Modifier, onTouch: () -> Unit = {}, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scaled = Density(density.density * state.place.scale, density.fontScale)
    Layout(
        content = {
            Box(Modifier.pinches(state, onTouch)) {
                CompositionLocalProvider(LocalDensity provides scaled, content = content)
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val screen = IntSize(constraints.maxWidth, constraints.maxHeight)
        val window = measurables.first().measure(Constraints(maxWidth = screen.width, maxHeight = screen.height))
        val corner = state.corner(IntSize(window.width, window.height), screen)
        layout(screen.width, screen.height) { window.place(corner) }
    }
}

/** Two fingers on the window: it follows them and grows or shrinks with them; one finger is left to what is inside. */
private fun Modifier.pinches(state: WindowState, onTouch: () -> Unit): Modifier = pointerInput(state) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onTouch()
        var pinched = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                state.zoomBy(event.calculateZoom())
                val pan = event.calculatePan()
                state.moveBy(pan.x, pan.y)
                event.changes.forEach { it.consume() }
                pinched = true
            }
        } while (event.changes.any { it.pressed })
        if (pinched) state.keep()
    }
}

/** The window's bar: a finger on it moves the window. */
internal fun Modifier.movesWindow(state: WindowState): Modifier = pointerInput(state) {
    detectDragGestures(onDragEnd = { state.keep() }, onDragCancel = { state.keep() }) { change, amount ->
        change.consume()
        state.moveBy(amount.x, amount.y)
    }
}

/** The window's − ([smaller]) and + ([larger]): a step smaller or bigger, kept at once. */
@Composable
internal fun WindowZoom(state: WindowState, smaller: Int, larger: Int, tint: Color, ground: Color, size: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ZoomButton(R.drawable.ic_remove, R.string.window_smaller, smaller, tint, ground, size, enabled = state.place.scale > WindowPlace.SMALLEST) {
            state.zoomBy(1f / ZOOM_STEP)
            state.keep()
        }
        ZoomButton(R.drawable.ic_add, R.string.window_larger, larger, tint, ground, size, enabled = state.place.scale < WindowPlace.LARGEST) {
            state.zoomBy(ZOOM_STEP)
            state.keep()
        }
    }
}

@Composable
private fun ZoomButton(icon: Int, label: Int, ref: Int, tint: Color, ground: Color, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(label)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .refCorner(ref)
            .padding(start = 4.dp)
            .size(size)
            .clip(CircleShape)
            .background(ground)
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = DISABLED), modifier = Modifier.size(size * ICON_SHARE))
    }
}

/** Each − or + makes the window this much smaller or bigger. */
private const val ZOOM_STEP = 1.2f
private const val ICON_SHARE = 0.6f
private const val DISABLED = 0.35f
