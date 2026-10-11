package se.eldebosh.nastastopp.ui.screens

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.display.MotionStyle
import se.eldebosh.nastastopp.ui.theme.AppTheme
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The words for each look, said nowhere: shown for a moment on the top line when the driver switches. */
val MotionStyle.label: Int
    get() = when (this) {
        MotionStyle.TRAILS -> R.string.motion_style_trails
        MotionStyle.WAVE -> R.string.motion_style_wave
        MotionStyle.STARS -> R.string.motion_style_stars
        MotionStyle.LANE -> R.string.motion_style_lane
        MotionStyle.PULSE -> R.string.motion_style_pulse
    }

/**
 * The motion band (297) across the whole top line, behind its signs and above the address, so it
 * never moves or covers what the passengers read. It goes with the car: it flows forward (to the
 * left) with the tablet's GPS speed ([speed], m/s; without it, with the shaking) and shakes with the
 * tablet's accelerometer ([level], 0–1). It stands when the car stops, and fades to a still grey
 * once the car has stood two minutes ([awake] false: the moments rest). [dim] while the top line's
 * signs show over it. Drawn each frame from the latest readings, with no recomposition.
 */
@Composable
fun MotionBand(
    style: MotionStyle,
    level: () -> Float,
    speed: () -> Float?,
    awake: Boolean,
    dim: Boolean,
    modifier: Modifier = Modifier,
) {
    val awakeNow by rememberUpdatedState(awake)
    val dimNow by rememberUpdatedState(dim)
    val s = remember { BandState() }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { t ->
                val dt = if (last == 0L) 0f else min(100L, t - last).toFloat()
                last = t
                s.step(t / 1000f, dt, level(), speed(), awakeNow, dimNow)
            }
        }
    }
    val on = AppTheme.colors.highlight
    val off = AppTheme.colors.textMuted
    val description = stringResource(if (awake) R.string.display_motion_on else R.string.display_motion_off)
    Canvas(modifier.semantics { contentDescription = description }) {
        s.frame.floatValue // read in the draw phase: each frame redraws, nothing recomposes
        val color = lerp(off, on, s.wake)
        val alpha = (0.45f + 0.55f * s.wake) * s.show
        when (style) {
            MotionStyle.TRAILS -> trails(s, color, alpha)
            MotionStyle.WAVE -> wave(s, color, alpha)
            MotionStyle.STARS -> stars(s, color, alpha)
            MotionStyle.LANE -> lane(s, color, alpha)
            MotionStyle.PULSE -> pulse(s, color, alpha)
        }
    }
}

/** The band's readings, smoothed, and how far it has flowed. Plain fields: only [frame] is state. */
private class BandState {
    val frame = mutableFloatStateOf(0f)
    var t = 0f
    var kmh = 0f
    var shake = 0f
    var bump = 0f
    var wake = 1f
    var show = 1f
    var travel = 0f

    /** 0 at a stand, 1 at a highway's speed. */
    val pace get() = min(1f, kmh / HIGHWAY_KMH)

    fun step(now: Float, dt: Float, level: Float, speedMps: Float?, awake: Boolean, dim: Boolean) {
        t = now
        // Without the GPS's speed the shaking stands for it: a car that shakes is moving.
        val target = speedMps?.let { it * 3.6f } ?: (min(1f, level * 2.5f) * TOWN_KMH)
        kmh += (target - kmh) * min(1f, dt / SPEED_FOLLOW_MS)
        val before = shake
        shake += (level - shake) * SHAKE_FOLLOW
        if (shake - before > BUMP_RISE) bump = 1f
        bump = max(0f, bump - dt / BUMP_MS)
        wake += ((if (awake) 1f else 0f) - wake) * min(1f, dt / WAKE_MS)
        show += ((if (dim) DIMMED else 1f) - show) * min(1f, dt / WAKE_MS)
        travel += dt / 1000f * (kmh / FLOW_KMH) * wake
        frame.floatValue = now
    }
}

/** A steady pseudo random number (0–1) for the [i]th mark, so the marks never flicker. */
private fun rnd(i: Int, k: Int = 1): Float {
    val x = sin(i * 127.1f + k * 311.7f) * 43758.547f
    return x - floor(x)
}

/** Where a mark at [base] (0–1 along the band) stands after the band flowed, wrapped; it flows to the left. */
private fun DrawScope.flowX(base: Float, moved: Float, span: Float): Float {
    val x = ((base * span + moved) % span) - (span - size.width) / 2f
    return size.width - x
}

private fun DrawScope.trails(s: BandState, color: Color, alpha: Float) {
    val h = size.height
    val n = 6 + (18 * s.pace).roundToInt()
    for (i in 0 until n) {
        val lane = ((i % 5) + 0.5f) / 5f
        val speed = 0.6f + rnd(i) * 0.8f
        val x = flowX(rnd(i, 2), s.travel * h * speed * 2f, size.width * 1.3f)
        val len = h * (0.4f + 3.5f * s.pace) * (0.6f + rnd(i, 3))
        val y = h * (0.2f + 0.6f * lane) + s.shake * h * 0.18f * sin(s.t * 23f + i * 1.9f) * s.wake
        drawLine(
            Brush.horizontalGradient(listOf(color.copy(alpha = 0.95f * alpha), color.copy(alpha = 0f)), startX = x, endX = x + len),
            Offset(x, y), Offset(x + len, y),
            strokeWidth = max(1.5f, h * 0.07f), cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.wave(s: BandState, color: Color, alpha: Float) {
    val h = size.height
    val w = size.width
    val amp = h * (0.06f + 0.32f * s.shake * s.wake)
    for (k in 0..1) {
        val path = Path()
        var x = 0f
        while (x <= w) {
            val u = x / h + s.travel * 2.2f
            val crest = s.bump * exp(-((x / w - 0.5f) * 6f).let { it * it })
            val y = h / 2f + amp * sin(u * 0.9f + k * 1.3f) * (if (k == 1) 0.6f else 1f) +
                amp * 0.4f * sin(u * 2.3f + s.t * 3f) * s.shake - crest * h * 0.3f * s.wake
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 3f
        }
        drawPath(path, color.copy(alpha = (if (k == 1) 0.35f else 0.95f) * alpha), style = Stroke(width = max(1.5f, h * 0.07f), cap = StrokeCap.Round))
    }
}

private fun DrawScope.stars(s: BandState, color: Color, alpha: Float) {
    val h = size.height
    for (i in 0 until 46) {
        val speed = 0.4f + rnd(i) * 1.2f
        val x = flowX(rnd(i, 2), s.travel * h * speed * 1.6f, size.width * 1.1f)
        val y = h * (0.1f + 0.8f * rnd(i, 4)) + s.shake * h * 0.12f * sin(s.t * 19f + i) * s.wake
        val len = h * 0.04f + h * 2.4f * s.pace * speed
        drawLine(
            color.copy(alpha = (0.35f + 0.6f * rnd(i, 5)) * alpha),
            Offset(x, y), Offset(x + len, y),
            strokeWidth = max(1.2f, h * 0.05f * (0.6f + rnd(i, 6))), cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.lane(s: BandState, color: Color, alpha: Float) {
    val h = size.height
    val w = size.width
    val jy = s.wake * (s.shake * h * 0.1f * sin(s.t * 29f) + s.bump * h * 0.14f * sin(s.t * 41f))
    for (f in floatArrayOf(0.18f, 0.82f)) {
        drawLine(color.copy(alpha = 0.45f * alpha), Offset(0f, h * f + jy), Offset(w, h * f + jy), strokeWidth = max(1f, h * 0.04f))
    }
    val dash = h * 1.6f
    val period = dash + h * 1.2f
    val off = ((-s.travel * h * 2.4f) % period + period) % period
    var x = -period + off
    while (x < w + period) {
        drawLine(color.copy(alpha = 0.95f * alpha), Offset(x, h / 2f + jy), Offset(x + dash, h / 2f + jy), strokeWidth = max(2f, h * 0.11f), cap = StrokeCap.Round)
        x += period
    }
}

private fun DrawScope.pulse(s: BandState, color: Color, alpha: Float) {
    val h = size.height
    val step = max(6f, h * 0.32f)
    val n = (size.width / step).toInt()
    val bw = max(2f, step * 0.42f)
    for (i in 0 until n) {
        val flow = 0.5f + 0.5f * sin(i * 0.32f + s.travel * 2.6f)
        val jitter = s.shake * s.wake * rnd(i, floor(s.t * 12f).toInt())
        val v = min(1f, 0.08f + (0.25f + 0.55f * s.pace) * flow * s.wake + 0.6f * jitter + 0.35f * s.bump * s.wake)
        val bh = h * 0.12f + h * 0.72f * v
        drawRoundRect(
            color.copy(alpha = (0.3f + 0.65f * v) * alpha),
            topLeft = Offset(i * step + (step - bw) / 2f, (h - bh) / 2f),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(bw / 2f),
        )
    }
}

/** The band's speeds: a highway's, a town's (for the shaking without GPS), and the speed at which it flows one band height a second. */
private const val HIGHWAY_KMH = 110f
private const val TOWN_KMH = 40f
private const val FLOW_KMH = 30f

/** How the readings are smoothed: the speed over about 1.5 s, the shaking a tenth a frame; a bump is a sudden rise, and fades in 0.6 s. */
private const val SPEED_FOLLOW_MS = 1500f
private const val SHAKE_FOLLOW = 0.15f
private const val BUMP_RISE = 0.04f
private const val BUMP_MS = 600f

/** The band fades to grey (or back) over about 0.8 s, and steps back to this while the top line's signs show. */
private const val WAKE_MS = 800f
private const val DIMMED = 0.3f
