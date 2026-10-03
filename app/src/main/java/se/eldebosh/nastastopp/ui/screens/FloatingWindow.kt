package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.settings.WindowPlace
import se.eldebosh.nastastopp.settings.WindowPlaces
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.AppTheme
import kotlin.math.abs
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

    // The window's and the screen's sizes at their last layout (the window stays inside the screen),
    // and how much of the window does not grow with it (its border).
    private var size = IntSize.Zero
    private var screen = IntSize.Zero
    private var fixed = 0

    /** Where the window's top left goes: its middle at [place], moved in as far as the screen needs. */
    fun corner(size: IntSize, screen: IntSize, fixed: Int = 0): IntOffset {
        this.size = size
        this.screen = screen
        this.fixed = fixed
        return IntOffset(edge(place.x, size.width, screen.width).roundToInt(), edge(place.y, size.height, screen.height).roundToInt())
    }

    /** Moved by ([dx], [dy]) pixels, never out of the screen. */
    fun moveBy(dx: Float, dy: Float) {
        if (screen.width == 0 || screen.height == 0) return
        place = place.copy(x = share(place.x, dx, size.width, screen.width), y = share(place.y, dy, size.height, screen.height))
    }

    /**
     * The window's [side] (an edge, or a corner) dragged [by] so many pixels: the window grows or
     * shrinks with it, between [WindowPlace.SMALLEST] and [WindowPlace.LARGEST], and the opposite
     * side stays where it is.
     */
    fun resizeBy(side: Side, by: Offset) {
        if (size.width == 0 || size.height == 0 || screen.width == 0 || screen.height == 0) return
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        // What it holds grows; its border does not. The edge follows the finger.
        val gx = side.dx * by.x / (w - fixed).coerceAtLeast(1f)
        val gy = side.dy * by.y / (h - fixed).coerceAtLeast(1f)
        val scale = (place.scale * (1 + if (abs(gx) >= abs(gy)) gx else gy)).coerceIn(WindowPlace.SMALLEST, WindowPlace.LARGEST)
        val f = scale / place.scale
        if (f == 1f) return
        val newW = (w - fixed) * f + fixed
        val newH = (h - fixed) * f + fixed
        val left = edge(place.x, size.width, screen.width)
        val top = edge(place.y, size.height, screen.height)
        val anchorX = left + w * (1 - side.dx) / 2f
        val anchorY = top + h * (1 - side.dy) / 2f
        val newLeft = anchorX - (anchorX - left) * newW / w
        val newTop = anchorY - (anchorY - top) * newH / h
        // Until the next layout, the size it will have: a fast drag stays with the finger.
        size = IntSize(newW.roundToInt(), newH.roundToInt())
        place = WindowPlace((newLeft + newW / 2f) / screen.width, (newTop + newH / 2f) / screen.height, scale)
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

/** An edge or corner of a window: which way each of its sides moves when dragged (-1, 0 or 1). */
internal class Side(val dx: Int, val dy: Int) {
    companion object {
        /** The side under [at] in a window [size] big, within [zone] of its border; null inside it. */
        fun at(at: Offset, size: IntSize, zone: Float): Side? {
            val dx = if (at.x < zone) -1 else if (at.x > size.width - zone) 1 else 0
            val dy = if (at.y < zone) -1 else if (at.y > size.height - zone) 1 else 0
            return if (dx == 0 && dy == 0) null else Side(dx, dy)
        }
    }
}

/**
 * [content] as a window over the whole screen, placed and sized by [state]: a finger on its border
 * (any edge or corner, marked at the corners) makes it bigger or smaller ([resizes]), two fingers
 * anywhere on it move it and pinch it, and its bar moves it ([movesWindow]); one finger still
 * scrolls and taps inside it. It is drawn at its size, never stretched: its text stays sharp. Each
 * touch on it is told ([onTouch]); touches beside it go through to what is under it. Its border is
 * numbered [edgeRef].
 */
@Composable
internal fun FloatingWindow(state: WindowState, edgeRef: Int, modifier: Modifier = Modifier, onTouch: () -> Unit = {}, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scaled = Density(density.density * state.place.scale, density.fontScale)
    val grip = AppTheme.colors.textMuted.copy(alpha = GRIP_ALPHA)
    val resize = stringResource(R.string.window_resize)
    Layout(
        content = {
            Box(
                Modifier
                    .refCorner(edgeRef)
                    .semantics { contentDescription = resize }
                    .pinches(state, onTouch)
                    .resizes(state)
                    .drawBehind { corners(grip) }
                    .padding(EDGE),
            ) {
                CompositionLocalProvider(LocalDensity provides scaled, content = content)
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val screen = IntSize(constraints.maxWidth, constraints.maxHeight)
        val window = measurables.first().measure(Constraints(maxWidth = screen.width, maxHeight = screen.height))
        val corner = state.corner(IntSize(window.width, window.height), screen, fixed = (EDGE * 2).roundToPx())
        layout(screen.width, screen.height) { window.place(corner) }
    }
}

/** A finger on the window's border (outside what it holds) makes it bigger or smaller from that side. */
private fun Modifier.resizes(state: WindowState): Modifier = pointerInput(state) {
    val zone = EDGE.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val side = Side.at(down.position, size, zone) ?: return@awaitEachGesture
        down.consume()
        drag(down.id) { change ->
            state.resizeBy(side, change.positionChange())
            change.consume()
        }
        state.keep()
    }
}

/** The four corners marked, where the border is easiest to take. */
private fun DrawScope.corners(color: Color) {
    val stroke = GRIP_STROKE.toPx()
    val long = GRIP_LENGTH.toPx()
    val inset = (EDGE / 2).toPx()
    for ((x, sx) in listOf(inset to 1f, size.width - inset to -1f)) {
        for ((y, sy) in listOf(inset to 1f, size.height - inset to -1f)) {
            drawLine(color, Offset(x, y), Offset(x + sx * long, y), stroke, StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + sy * long), stroke, StrokeCap.Round)
        }
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

/** The window's border, where a finger resizes it, and the marks at its corners. */
private val EDGE = 18.dp
private val GRIP_STROKE = 3.dp
private val GRIP_LENGTH = 14.dp
private const val GRIP_ALPHA = 0.7f
