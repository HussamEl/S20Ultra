package se.eldebosh.nastastopp.core.display

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * What the passenger display shows, as its flow (297) sees it: how much of the flow it leaves
 * ([strength]: 0 none, 1 all) and how far the flow keeps to the screen's edges ([edges]: 0 only a
 * light clearing in the middle, 1 the middle cleared most). Text shown large keeps the flow to the
 * edges, away from the large time and address.
 */
enum class FlowScene(val strength: Float, val edges: Float) {
    /** The next stop at home, or no route yet. */
    HOME(1f, 0.15f),

    /** A trip shown large: the one motion (M1–M8), at home now and then, tapped or announced. */
    FOCUS(0.55f, 0.85f),

    /** A minute's moment: the time, the weather or the travel time large in the middle. */
    MOMENT(0.5f, 0.85f),

    /** A window over the display: the trip card (235). */
    WINDOW(0.3f, 0.6f),

    /** The driver's map (219), revealed or held, with its order list on it: no flow at all. */
    MAP(0f, 1f),
}

/**
 * The flow's readings, smoothed frame by frame ([step]), and the numbers it is drawn from. Plain
 * fields, so the layer redraws each frame without recomposing, and the JVM tests run it.
 *
 * - [kmh] follows the tablet's GPS speed over about 1.5 s. Without a GPS speed the shaking stands
 *   for it (a car that shakes is moving), up to a town's speed.
 * - [accel] (m/s²) is the smoothed speed's change, smoothed again over 0.4 s: positive while the
 *   car speeds up (the marks stretch and brighten, the vanishing point comes a little lower),
 *   negative while it brakes (the marks compress). It is read from the GPS speed alone, smoothed
 *   apart ([gpsKmh]) and kept through a short loss of the position: the shaking's stand-in, or a
 *   position that goes and comes back, is no speeding up or braking.
 * - [shake] follows the accelerometer's shaking (0–1); it jitters the vanishing point and strokes.
 * - [travel] is how far the flow has run toward the top, faster with the speed; [ripple] runs
 *   faster still as the pace rises, for the waves along the marks. Both are summed frame by frame
 *   (never a speed times the time), so a change of speed never makes the marks jump, and kept as
 *   doubles, so hours of driving never blur them.
 * - [shown]: transparent at a stand and fully shown from about 50 km/h, times what the display
 *   leaves of it ([FlowScene], eased over 0.8 s; a little less while the top line's signs show),
 *   times [wake] (it fades out once the car has stood two minutes). Both easings arrive (they
 *   end on their target once within [ARRIVED]), and the driver's map hides it over 0.25 s, so
 *   it stops being drawn within about a second of the map opening.
 * - [autoLook] is AUTO's look by the speed, with hysteresis so it never flaps at a boundary; the
 *   looks crossfade over about 1.5 s ([weight]).
 */
class FlowState {
    /** The frame's time (s), for the jitter. */
    var seconds = 0f
        private set
    var kmh = 0f
        private set
    var accel = 0f
        private set
    var shake = 0f
        private set
    var travel = 0.0
        private set
    var ripple = 0.0
        private set
    var wake = 1f
        private set
    var strength = FlowScene.HOME.strength
        private set
    var edges = FlowScene.HOME.edges
        private set
    var autoLook = MotionStyle.WAKE
        private set
    /** The GPS speed alone, smoothed as [kmh] is; null without a position for longer than [GPS_GAP_MS]. */
    private var gpsKmh: Float? = null
    private var noGpsMs = 0f
    private val mix = FloatArray(AUTO_LOOKS.size).also { it[AUTO_LOOKS.indexOf(MotionStyle.WAKE)] = 1f }

    /** 0 at a stand, 1 at a highway's speed. */
    val pace: Float get() = min(1f, kmh / HIGHWAY_KMH)

    /** The acceleration as -1 (hard braking) to 1 (speeding up hard). */
    val push: Float get() = (accel / FULL_PUSH).coerceIn(-1f, 1f)

    /** How much of the flow shows now, 0–1, before its look's own share ([alpha]). */
    val shown: Float get() = smoothstep(SHOWN_FROM_KMH, SHOWN_FULL_KMH, kmh) * strength * wake

    /**
     * One frame: [dtMs] since the last, the shaking [level] (0–1), the GPS speed (m/s, null
     * without a position), whether the car moved in the last two minutes, what the display shows,
     * and whether the top line's signs show over it.
     */
    fun step(nowS: Float, dtMs: Float, level: Float, speedMps: Float?, awake: Boolean, scene: FlowScene, signs: Boolean) {
        seconds = nowS
        val dt = min(dtMs, MAX_STEP_MS)
        if (dt <= 0f) return
        val gps = speedMps?.let { max(0f, it) * MPS_TO_KMH }
        val target = gps ?: (min(1f, level * SHAKE_TO_SPEED) * TOWN_KMH)
        kmh += (target - kmh) * follow(dt, SPEED_FOLLOW_MS)
        val change = if (gps == null) {
            noGpsMs += dt
            if (noGpsMs > GPS_GAP_MS) gpsKmh = null
            0f
        } else {
            noGpsMs = 0f
            val last = gpsKmh
            val now = if (last == null) gps else last + (gps - last) * follow(dt, SPEED_FOLLOW_MS)
            gpsKmh = now
            if (last == null) 0f else (now - last) / MPS_TO_KMH / (dt / 1000f)
        }
        accel += (change - accel) * follow(dt, ACCEL_FOLLOW_MS)
        shake += (level.coerceIn(0f, 1f) - shake) * follow(dt, SHAKE_FOLLOW_MS)
        val run = dt / 1000.0 * kmh / FLOW_KMH
        travel += run
        ripple += run * (1f + RIPPLE_PACE * pace)
        wake = ease(wake, if (awake) 1f else 0f, follow(dt, WAKE_MS))
        val leaves = scene.strength * (if (signs) SIGNS_SHOWN else 1f)
        // Hidden (the driver's map) faster: the map covers it, and it is not drawn on under it.
        strength = ease(strength, leaves, follow(dt, if (leaves == 0f) HIDE_MS else SCENE_MS))
        edges += (scene.edges - edges) * follow(dt, SCENE_MS)
        autoLook = nextAutoLook(autoLook, kmh)
        for (i in AUTO_LOOKS.indices) {
            mix[i] += ((if (AUTO_LOOKS[i] == autoLook) 1f else 0f) - mix[i]) * follow(dt, LOOK_MS)
        }
    }

    /** How much of [look] is drawn while the driver's look is [style]: AUTO crossfades its three. */
    fun weight(style: MotionStyle, look: MotionStyle): Float = when (style) {
        MotionStyle.OFF -> 0f
        MotionStyle.AUTO -> AUTO_LOOKS.indexOf(look).let { if (it < 0) 0f else mix[it] }
        else -> if (look == style) 1f else 0f
    }

    /** How strongly [look] is drawn now under [style], 0–1. */
    fun alpha(style: MotionStyle, look: MotionStyle): Float = shown * weight(style, look)

    /** Anything of the flow shows under [style]. */
    fun visible(style: MotionStyle): Boolean = style != MotionStyle.OFF && shown > HIDDEN

    /** The vanishing point across the screen (a share of its width): the middle, jittered by the shaking. */
    val vanishX: Float get() = 0.5f + shake * VANISH_SHAKE_X * sin(seconds * VANISH_JITTER_X)

    /**
     * The vanishing point's height (a share of the screen's height; negative is above its top): a
     * little lower while the car speeds up, jittered by the shaking.
     */
    val vanishY: Float get() = -(HORIZON - HORIZON_PUSH * push) + shake * VANISH_SHAKE_Y * sin(seconds * VANISH_JITTER_Y)

    /** How much of the flow the cut takes away over where the text stands, 0–1: all of it while text is shown large. */
    val clearing: Float get() = min(1f, CLEAR_MIDDLE + CLEAR_EDGES * edges)

    /**
     * The box the cut takes in full, around the screen's middle, as shares of its half-width and
     * half-height: at home the next stop's time and address, and the whole box the large time or
     * address can fill (90 % of the width, 74 % of the height) once a trip or moment is shown large.
     * Beyond it the cut thins out over [CLEAR_FEATHER] of the shorter half-side.
     */
    val clearHalfWidth: Float get() = CLEAR_W + CLEAR_W_EDGES * edges
    val clearHalfHeight: Float get() = CLEAR_H + CLEAR_H_EDGES * edges

    companion object {
        /** AUTO's looks, slow to fast. */
        val AUTO_LOOKS = listOf(MotionStyle.WAKE, MotionStyle.ROAD, MotionStyle.LIGHT)

        /**
         * AUTO's look at [kmh] after [now]: [MotionStyle.WAKE] in town, [MotionStyle.ROAD] between,
         * [MotionStyle.LIGHT] on a highway. It goes up past 40 and 80 km/h and back down under 30
         * and 70, so a speed around a boundary never makes it flap.
         */
        fun nextAutoLook(now: MotionStyle, kmh: Float): MotionStyle = when {
            now == MotionStyle.WAKE && kmh > ROAD_UP_KMH -> MotionStyle.ROAD
            now == MotionStyle.ROAD && kmh < ROAD_DOWN_KMH -> MotionStyle.WAKE
            now == MotionStyle.ROAD && kmh > LIGHT_UP_KMH -> MotionStyle.LIGHT
            now == MotionStyle.LIGHT && kmh < LIGHT_DOWN_KMH -> MotionStyle.ROAD
            else -> now
        }

        const val ROAD_UP_KMH = 40f
        const val ROAD_DOWN_KMH = 30f
        const val LIGHT_UP_KMH = 80f
        const val LIGHT_DOWN_KMH = 70f

        /** Transparent up to a walking pace, fully shown from a town road's speed. */
        const val SHOWN_FROM_KMH = 3f
        const val SHOWN_FULL_KMH = 50f

        /** What the flow keeps while the top line's signs show over it. */
        const val SIGNS_SHOWN = 0.7f

        /** Under this the flow is not drawn at all. */
        const val HIDDEN = 0.004f

        /** A highway's speed (the fullest pace), a town's (the shaking's most, without GPS), and the speed that runs the flow one unit a second. */
        private const val HIGHWAY_KMH = 110f
        private const val TOWN_KMH = 40f
        private const val FLOW_KMH = 30f
        private const val SHAKE_TO_SPEED = 2.5f

        /** How much faster the ripples run at a highway's pace than at a stand's. */
        private const val RIPPLE_PACE = 1.3f
        private const val MPS_TO_KMH = 3.6f

        /** Speeding up or braking this hard (m/s²) stretches or compresses the marks the most. */
        private const val FULL_PUSH = 2.5f

        /** How the readings follow: the speed over 1.5 s, its change over 0.4 s, the shaking over 0.1 s, the look over 1.5 s, the display's scene over 0.8 s (hidden over 0.25 s), the car's rest over 1.5 s. */
        private const val SPEED_FOLLOW_MS = 1_500f
        private const val ACCEL_FOLLOW_MS = 400f
        private const val SHAKE_FOLLOW_MS = 100f
        private const val LOOK_MS = 1_500f
        private const val SCENE_MS = 800f
        private const val HIDE_MS = 250f
        private const val WAKE_MS = 1_500f

        /** A position lost for longer than this starts its speed afresh when it comes back. */
        private const val GPS_GAP_MS = 3_000f

        /** A longer gap between frames (a pause, a hidden flow's slower steps) counts as this. */
        private const val MAX_STEP_MS = 250f

        /** The vanishing point: above the top by this share of the height, lower by up to [HORIZON_PUSH] while speeding up; the shaking's jitter. */
        private const val HORIZON = 0.16f
        private const val HORIZON_PUSH = 0.05f
        private const val VANISH_SHAKE_X = 0.006f
        private const val VANISH_SHAKE_Y = 0.01f
        private const val VANISH_JITTER_X = 23f
        private const val VANISH_JITTER_Y = 31f

        /** The middle's cut: its strength and box at no edges, and how much more each takes at full edges. */
        private const val CLEAR_MIDDLE = 0.8f
        private const val CLEAR_EDGES = 0.4f
        private const val CLEAR_W = 0.5f
        private const val CLEAR_W_EDGES = 0.5f
        private const val CLEAR_H = 0.4f
        private const val CLEAR_H_EDGES = 0.45f

        /** How far beyond its box the cut thins out to nothing: a share of the screen's shorter half-side. */
        const val CLEAR_FEATHER = 0.3f

        private fun follow(dt: Float, ms: Float) = min(1f, dt / ms)

        /** An easing this near its target ends on it. */
        const val ARRIVED = 0.02f

        /** [from] eased toward [to] by [share], ending on [to] once within [ARRIVED]. */
        private fun ease(from: Float, to: Float, share: Float): Float {
            val next = from + (to - from) * share
            return if (abs(to - next) < ARRIVED) to else next
        }

        private fun smoothstep(from: Float, to: Float, x: Float): Float {
            val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
