package se.eldebosh.nastastopp.ui.screens

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.display.FlowScene
import se.eldebosh.nastastopp.core.display.FlowState
import se.eldebosh.nastastopp.core.display.MotionStyle
import se.eldebosh.nastastopp.ui.theme.AppTheme
import se.eldebosh.nastastopp.ui.theme.DisplayColors
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The words for each look, said nowhere: shown for a moment on the top line when the driver switches. */
val MotionStyle.label: Int
    get() = when (this) {
        MotionStyle.AUTO -> R.string.motion_style_auto
        MotionStyle.ROAD -> R.string.motion_style_road
        MotionStyle.WINGS -> R.string.motion_style_wings
        MotionStyle.AURORA -> R.string.motion_style_aurora
        MotionStyle.LIGHT -> R.string.motion_style_light
        MotionStyle.WAKE -> R.string.motion_style_wake
        MotionStyle.OFF -> R.string.motion_style_off
    }

/**
 * The passenger display's flow (297): one layer over the whole screen, behind everything it shows
 * and above its ground colour. It rises from both sides and the bottom and gathers toward a
 * vanishing point above the top's middle, the way the car goes, in the driver's look ([style]):
 * - it flows forward with the tablet's GPS speed ([speed], m/s; without it the shaking [level],
 *   0–1, stands for it), is transparent at a stand and fully shown from about 50 km/h;
 * - speeding up stretches and brightens its marks and brings the vanishing point a little lower;
 *   braking compresses them; the shaking jitters the vanishing point and the strokes;
 * - it fades out once the car has stood two minutes ([awake] false);
 * - what the display shows ([scene]) sets how much of it shows and how far it keeps to the edges:
 *   the middle, where the text stands, is always cleared (cut out of the layer), the more so when
 *   the text is large; it is hidden on the driver's map, and a little fainter while the top line's
 *   [signs] show;
 * - it is drawn in the look's highlight, going over to the black look's as the ground under a
 *   trip shown large turns black ([ground], 0–1), so it reads on that ground in the light look too.
 *
 * The readings are smoothed in [FlowState]; each frame redraws the layer from it in the draw phase
 * only, with no recomposition, reused paint and path and a bounded number of marks. Hidden, it
 * draws nothing and follows the car at a slower pace.
 */
@Composable
fun FlowLayer(
    style: MotionStyle,
    level: () -> Float,
    speed: () -> Float?,
    awake: Boolean,
    scene: FlowScene,
    signs: Boolean,
    modifier: Modifier = Modifier,
    ground: () -> Float = { 0f },
) {
    val styleNow by rememberUpdatedState(style)
    val levelNow by rememberUpdatedState(level)
    val speedNow by rememberUpdatedState(speed)
    val awakeNow by rememberUpdatedState(awake)
    val sceneNow by rememberUpdatedState(scene)
    val signsNow by rememberUpdatedState(signs)
    val s = remember { FlowState() }
    val pen = remember { FlowPen() }
    // The one state the frames write: the draw phase reads it, so each frame only redraws.
    val frame = remember { mutableIntStateOf(0) }
    val drawn = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var first = -1L
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { t ->
                if (first < 0L) first = t
                val dt = if (last == 0L) 0f else (t - last).toFloat()
                last = t
                // The jitter's clock starts again every hour, so its seconds stay exact.
                s.step(((t - first) % JITTER_WRAP_MS) / 1000f, dt, levelNow(), speedNow(), awakeNow, sceneNow, signsNow)
                val on = s.visible(styleNow)
                if (on != drawn.value) drawn.value = on
                if (on) frame.intValue++
            }
            if (!drawn.value) delay(HIDDEN_STEP_MS)
        }
    }
    val own = AppTheme.colors.highlight
    val black = DisplayColors.highlight
    val description = stringResource(if (awake) R.string.display_motion_on else R.string.display_motion_off)
    Canvas(
        modifier
            .semantics { contentDescription = description }
            .graphicsLayer {
                // Drawn apart, so the middle's clearing cuts the flow only, never the screen under it.
                alpha = if (drawn.value) 1f else 0f
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        frame.intValue
        if (!drawn.value) return@Canvas
        val look = styleNow
        val color = lerp(own, black, ground().coerceIn(0f, 1f))
        val vx = s.vanishX * size.width
        val vy = s.vanishY * size.height
        for (drawnLook in LOOKS) {
            val base = s.alpha(look, drawnLook)
            if (base < MIN_ALPHA) continue
            when (drawnLook) {
                MotionStyle.ROAD -> road(s, color, base, vx, vy)
                MotionStyle.WINGS -> wings(s, color, base, vx, vy)
                MotionStyle.AURORA -> aurora(pen, s, color, base, vx, vy)
                MotionStyle.LIGHT -> light(pen, s, color, base, vx, vy)
                MotionStyle.WAKE -> wake(s, color, base, vx)
                MotionStyle.AUTO, MotionStyle.OFF -> Unit
            }
        }
        clearMiddle(pen, s, color)
    }
}

/** What the flow draws with, made once: its paint, its path, the light streaks' steady randoms and the middle's clearing. */
private class FlowPen {
    val paint = Paint().apply {
        style = PaintingStyle.Stroke
        strokeCap = StrokeCap.Round
        isAntiAlias = true
    }
    val path = Path()
    val streakY = FloatArray(STREAKS_MOST) { rnd(it, 4) }
    val streakStart = FloatArray(STREAKS_MOST) { rnd(it, 2) }
    val streakSpeed = FloatArray(STREAKS_MOST) { rnd(it, 1) }
    val streakAlpha = FloatArray(STREAKS_MOST) { rnd(it, 5) }
    val streakWidth = FloatArray(STREAKS_MOST) { rnd(it, 6) }

    /** The middle's clearing, made again only when its size or strength changes. */
    var clearing: Brush? = null
    var clearingKey = Long.MIN_VALUE
}

/** One stroke of the flow, unless it would not be seen. */
private fun DrawScope.line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, alpha: Float, color: Color) {
    val a = alpha.coerceIn(0f, 1f)
    if (a < MIN_ALPHA) return
    drawLine(color, Offset(x0, y0), Offset(x1, y1), strokeWidth = width, cap = StrokeCap.Round, alpha = a)
}

/** Depth along a line to the vanishing point: near marks (u near 0) move fast and are large, far ones small and slow. */
private fun depth(u: Float) = 1f - 1f / (1f + DEPTH_CURVE * u)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** The part of [d] after its whole number, 0–1. */
private fun frac(d: Double): Float = (d - floor(d)).toFloat()

/** A steady pseudo random number (0–1) for the [i]th mark, so the marks never flicker. */
private fun rnd(i: Int, k: Int = 1): Float {
    val x = sin(i * 127.1f + k * 311.7f) * 43758.547f
    return x - floor(x)
}

/** Speeding up brightens the marks a little. */
private fun bright(s: FlowState, most: Float) = 1f + most * max(0f, s.push)

/** Speeding up stretches the marks, braking compresses them to [least] of their length. */
private fun stretch(s: FlowState, most: Float, least: Float) = (1f + most * max(0f, s.push)) * (1f - (1f - least) * max(0f, -s.push))

/** Two edge rails from the bottom corners and two dashed lane lines, all to the vanishing point; the dashes run toward it. */
private fun DrawScope.road(s: FlowState, color: Color, base: Float, vx: Float, vy: Float) {
    val w = size.width
    val h = size.height
    for (fx in ROAD_RAILS) line(fx * w, h, vx, vy, max(1.5f, w * 0.003f), 0.55f * base, color)
    val len = (0.05f + 0.06f * s.pace) * stretch(s, 0.8f, 0.55f)
    val run = frac(s.travel * 0.12)
    val a = base * bright(s, 0.3f)
    for (fx in ROAD_LANES) {
        for (i in 0 until ROAD_DASHES) {
            val u = (i.toFloat() / ROAD_DASHES + run) % 1f
            val t0 = depth(u)
            val t1 = depth(min(1f, u + len))
            line(
                lerp(fx * w, vx, t0), lerp(h, vy, t0), lerp(fx * w, vx, t1), lerp(h, vy, t1),
                max(1.5f, w * 0.012f * (1f - t0)), a * (1f - t0 * 0.9f), color,
            )
        }
    }
}

/** Chevrons whose arms reach the side edges, climbing toward the top and narrowing with depth. */
private fun DrawScope.wings(s: FlowState, color: Color, base: Float, vx: Float, vy: Float) {
    val w = size.width
    val h = size.height
    val run = frac(s.travel * 0.1)
    val fold = (0.18f + 0.12f * max(0f, s.push)) * stretch(s, 0f, 0.65f)
    val a = base * bright(s, 0.3f) * 0.9f
    for (i in 0 until WINGS) {
        val u = (i.toFloat() / WINGS + run) % 1f
        val t = depth(u)
        val y = h * (1.05f - 1.1f * t) + vy * t
        val spread = (1f - t) * 0.55f + 0.04f
        val drop = h * fold * (1f - t)
        val alpha = a * sin(PI.toFloat() * min(1f, u * 1.15f))
        val width = max(1.5f, w * 0.008f * (1f - t))
        line(vx - spread * w, y + drop, vx, y, width, alpha, color)
        line(vx + spread * w, y + drop, vx, y, width, alpha, color)
    }
}

/** Two soft ribbons of fine strands rising from the bottom corners and meeting at the top, waves running up them. */
private fun DrawScope.aurora(pen: FlowPen, s: FlowState, color: Color, base: Float, vx: Float, vy: Float) {
    val w = size.width
    val h = size.height
    val sway = s.travel * 0.6
    val waves = s.ripple * 1.5
    val a = base * 0.11f * bright(s, 0.6f)
    val top = max(vy, h * 0.02f)
    drawIntoCanvas { canvas ->
        for (side in SIDES) {
            for (k in 0 until AURORA_STRANDS) {
                val f = k / (AURORA_STRANDS - 1f)
                val wave = 0.5f + 0.5f * sin(f * 9.0 - waves).toFloat()
                val alpha = (a * (0.4f + 0.6f * wave)).coerceIn(0f, 1f)
                if (alpha < MIN_ALPHA) continue
                pen.path.rewind()
                pen.path.moveTo(vx + side * w * (0.5f - 0.03f * f), h)
                pen.path.quadraticTo(
                    vx + side * w * (0.42f - 0.12f * f), h * (0.35f + 0.1f * sin(sway + f * 3.0).toFloat()),
                    vx + side * w * 0.02f * f, top,
                )
                pen.paint.color = color.copy(alpha = alpha)
                pen.paint.strokeWidth = max(2f, w * (0.01f + 0.012f * wave))
                canvas.drawPath(pen.path, pen.paint)
            }
        }
    }
}

/** Streaks born at both side edges streaming toward the vanishing point, longer with the speed; more of them the faster. */
private fun DrawScope.light(pen: FlowPen, s: FlowState, color: Color, base: Float, vx: Float, vy: Float) {
    val w = size.width
    val h = size.height
    val many = STREAKS_FEWEST + (STREAKS_MOST - STREAKS_FEWEST) * s.pace
    val count = min(STREAKS_MOST, ceil(many).roundToInt())
    val len = (0.02f + 0.22f * s.pace) * stretch(s, 0.7f, 0.5f)
    val a = base * bright(s, 0.3f)
    for (i in 0 until count) {
        val sx = if (i % 2 == 0) 0f else w
        val sy = h * (0.15f + 0.9f * pen.streakY[i])
        val u = frac(pen.streakStart[i] + s.travel * (0.05 + 0.08 * pen.streakSpeed[i]))
        val t0 = depth(u) * 0.95f
        val t1 = max(0f, t0 - len * (1f - t0))
        // The newest streaks come in gently as the speed rises.
        val coming = min(1f, many - i)
        line(
            lerp(sx, vx, t1), lerp(sy, vy, t1), lerp(sx, vx, t0), lerp(sy, vy, t0),
            max(1f, w * 0.004f * (1f - t0) * (0.6f + pen.streakWidth[i])),
            a * (0.35f + 0.6f * pen.streakAlpha[i]) * (1f - t0) * coming, color,
        )
    }
}

/** A V of lines with its apex at the top's middle, ripples running along them toward the top. */
private fun DrawScope.wake(s: FlowState, color: Color, base: Float, vx: Float) {
    val w = size.width
    val h = size.height
    val apexY = h * 0.04f
    val waves = s.ripple * 1.2
    val a = base * bright(s, 0.5f)
    for (side in SIDES) {
        for (k in 0 until WAKE_LINES) {
            val ex = vx + side * w * (0.52f + 0.06f * k)
            val ey = h * (0.75f + 0.15f * k)
            for (j in 0 until WAKE_SEGMENTS) {
                val f0 = j.toFloat() / WAKE_SEGMENTS
                val f1 = (j + 1f) / WAKE_SEGMENTS
                val ripple = 0.5f + 0.5f * sin(f0 * 18.0 + waves + k).toFloat()
                val jy = s.shake * h * 0.004f * sin(s.seconds * 27f + j)
                line(
                    lerp(vx, ex, f0), lerp(apexY, ey, f0) + jy, lerp(vx, ex, f1), lerp(apexY, ey, f1) + jy,
                    max(1.2f, w * (0.002f + 0.006f * ripple) * (0.4f + f0)),
                    a * (0.25f + 0.55f * ripple) * (1f - 0.25f * k), color,
                )
            }
        }
    }
}

/**
 * The middle kept clear: the flow is cut away over where the text stands, most at the middle and
 * thinning out to the edges, and further out the larger the text ([FlowState.clearing],
 * [FlowState.clearingReach]). Only its alpha counts.
 */
private fun DrawScope.clearMiddle(pen: FlowPen, s: FlowState, color: Color) {
    val clear = s.clearing
    val reach = s.clearingReach
    val key = (size.width.roundToInt().toLong() shl 40) or (size.height.roundToInt().toLong() shl 16) or (clear * 1000f).roundToInt().toLong()
    val brush = pen.clearing?.takeIf { pen.clearingKey == key } ?: Brush.radialGradient(
        0f to color.copy(alpha = clear),
        reach to color.copy(alpha = clear * FlowState.CLEAR_THINNED),
        1f to color.copy(alpha = 0f),
        center = Offset(size.width / 2f, size.height / 2f),
        radius = max(size.width, size.height) / 2f,
    ).also {
        pen.clearing = it
        pen.clearingKey = key
    }
    drawRect(brush, blendMode = BlendMode.DstOut)
}

/** The looks drawn, AUTO's three among them. */
private val LOOKS = arrayOf(MotionStyle.WAKE, MotionStyle.ROAD, MotionStyle.LIGHT, MotionStyle.WINGS, MotionStyle.AURORA)
private val SIDES = floatArrayOf(-1f, 1f)

/** Fainter than this is not drawn. */
private const val MIN_ALPHA = 0.004f

/** The road's rails and lane lines (shares of the width at the bottom), and its dashes on each lane line. */
private val ROAD_RAILS = floatArrayOf(0.03f, 0.97f)
private val ROAD_LANES = floatArrayOf(0.24f, 0.76f)
private const val ROAD_DASHES = 9

/** The marks of each look. */
private const val WINGS = 8
private const val AURORA_STRANDS = 26
private const val STREAKS_FEWEST = 50
private const val STREAKS_MOST = 140
private const val WAKE_LINES = 3
private const val WAKE_SEGMENTS = 40

/** How steeply the marks shrink toward the vanishing point. */
private const val DEPTH_CURVE = 6f

/** Hidden, the flow follows the car ten times a second instead of every frame. */
private const val HIDDEN_STEP_MS = 100L

private const val JITTER_WRAP_MS = 3_600_000L
