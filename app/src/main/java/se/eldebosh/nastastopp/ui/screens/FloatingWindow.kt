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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
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

    // The window's and the screen's sizes at their last layout (the window stays inside the screen).
    private var size = IntSize.Zero
    private var screen = IntSize.Zero

    /** Where the window's top left goes: its middle at [place], moved in as far as the screen needs. */
    fun corner(size: IntSize, screen: IntSize): IntOffset {
        this.size = size
        this.screen = screen
        return IntOffset(edge(place.x, size.width, screen.width).roundToInt(), edge(place.y, size.height, screen.height).roundToInt())
    }

    /** The size the driver gave it on a [screen] (0 for a side he left as drawn), never more than the screen. */
    fun sizeOn(screen: IntSize): IntSize = IntSize(
        (place.width * screen.width).roundToInt().coerceAtMost(screen.width),
        (place.height * screen.height).roundToInt().coerceAtMost(screen.height),
    )

    /** Moved by ([dx], [dy]) pixels, never out of the screen. */
    fun moveBy(dx: Float, dy: Float) {
        if (screen.width == 0 || screen.height == 0) return
        place = place.copy(x = share(place.x, dx, size.width, screen.width), y = share(place.y, dy, size.height, screen.height))
    }

    /**
     * The window's [side] (an edge, or a corner) dragged [by] so many pixels: that edge follows the
     * finger, wider or narrower from a side edge, taller or lower from the top or bottom, both from
     * a corner; between [WindowPlace.SMALLEST] of the screen and the whole screen. The opposite
     * side stays where it is. What it holds is laid out again at the new size, never stretched.
     */
    fun resizeBy(side: Side, by: Offset) {
        if (size.width == 0 || size.height == 0 || screen.width == 0 || screen.height == 0) return
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val newW = if (side.dx != 0) (w + side.dx * by.x).coerceIn(WindowPlace.SMALLEST * screen.width, screen.width.toFloat()) else w
        val newH = if (side.dy != 0) (h + side.dy * by.y).coerceIn(WindowPlace.SMALLEST * screen.height, screen.height.toFloat()) else h
        if (newW == w && newH == h) return
        val left = edge(place.x, size.width, screen.width)
        val top = edge(place.y, size.height, screen.height)
        // The side opposite the finger keeps its place.
        val newLeft = if (side.dx < 0) left + w - newW else left
        val newTop = if (side.dy < 0) top + h - newH else top
        // Until the next layout, the size it will have: a fast drag stays with the finger.
        size = IntSize(newW.roundToInt(), newH.roundToInt())
        place = WindowPlace(
            (newLeft + newW / 2f) / screen.width,
            (newTop + newH / 2f) / screen.height,
            if (side.dx != 0) newW / screen.width else place.width,
            if (side.dy != 0) newH / screen.height else place.height,
        )
    }

    /** [factor] times as big both ways (two fingers), within the screen; its middle stays. */
    fun zoomBy(factor: Float) {
        if (size.width == 0 || size.height == 0 || screen.width == 0 || screen.height == 0 || factor == 1f) return
        val w = (size.width * factor).coerceIn(WindowPlace.SMALLEST * screen.width, screen.width.toFloat())
        val h = (size.height * factor).coerceIn(WindowPlace.SMALLEST * screen.height, screen.height.toFloat())
        size = IntSize(w.roundToInt(), h.roundToInt())
        place = place.copy(width = w / screen.width, height = h / screen.height)
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
 * [content] as a window over the whole screen, placed and sized by [state], like any window: a
 * finger on its border makes it wider or narrower from a side edge, taller or lower from the top
 * or bottom edge, and both from a corner ([resizes]); two fingers anywhere on it move it and
 * pinch it; its bar moves it ([movesWindow]); one finger still scrolls and taps inside it.
 * [content] fills the size it is given (until the driver sizes it, its own), and is laid out again
 * at each size: its text keeps its size. Each touch on it is told ([onTouch]); touches beside it
 * go through to what is under it. Its border is numbered [edgeRef].
 */
@Composable
internal fun FloatingWindow(state: WindowState, edgeRef: Int, modifier: Modifier = Modifier, onTouch: () -> Unit = {}, content: @Composable () -> Unit) {
    val resize = stringResource(R.string.window_resize)
    Layout(
        content = {
            Box(
                Modifier
                    .refCorner(edgeRef)
                    .semantics { contentDescription = resize }
                    .pinches(state, onTouch)
                    .resizes(state)
                    .padding(EDGE),
                propagateMinConstraints = true,
            ) {
                content()
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val screen = IntSize(constraints.maxWidth, constraints.maxHeight)
        val given = state.sizeOn(screen)
        val window = measurables.first().measure(
            Constraints(
                minWidth = given.width,
                maxWidth = if (given.width > 0) given.width else screen.width,
                minHeight = given.height,
                maxHeight = if (given.height > 0) given.height else screen.height,
            ),
        )
        val corner = state.corner(IntSize(window.width, window.height), screen)
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

/** The window's border, unmarked, where a finger resizes it. */
private val EDGE = 18.dp
