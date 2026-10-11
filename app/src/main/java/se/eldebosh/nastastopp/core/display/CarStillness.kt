package se.eldebosh.nastastopp.core.display

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Whether the car moves, from the tablet's own accelerometer (the shaking of a car on the road)
 * and its GPS speed: the passenger display's minute moments, its map and its flights stop
 * [STILL_AFTER_MS] after the car last moved, and come back as soon as it moves again. Nothing is
 * stored or sent; only how much it shakes now ([level]) and when it last moved.
 */
class CarStillness(private val stillAfterMs: Long = STILL_AFTER_MS) {
    private var gravity = Float.NaN
    private var energy = 0f
    private var lastMovedMs = Long.MIN_VALUE / 2

    /** How much the tablet shakes now, 0 (still) to 1 (a rough road), smoothed. */
    var level = 0f
        private set

    /** An accelerometer reading at [nowMs]: the size of the acceleration, gravity included (m/s²). */
    fun onAcceleration(x: Float, y: Float, z: Float, nowMs: Long) {
        val g = sqrt(x * x + y * y + z * z)
        // Gravity is what stays; the shaking is what moves around it.
        gravity = if (gravity.isNaN()) g else gravity + (g - gravity) * GRAVITY_FOLLOW
        val shake = abs(g - gravity)
        energy += (shake - energy) * SHAKE_FOLLOW
        level = (energy / FULL_SHAKE).coerceIn(0f, 1f)
        if (energy >= MOVING_SHAKE) lastMovedMs = nowMs
    }

    /** A GPS speed at [nowMs] (m/s): driving counts as moving even on a smooth road. */
    fun onSpeed(speedMps: Float?, nowMs: Long) {
        if (speedMps != null && speedMps >= MOVING_SPEED) lastMovedMs = nowMs
    }

    /** The car moved within the last [stillAfterMs]. */
    fun awake(nowMs: Long): Boolean = nowMs - lastMovedMs < stillAfterMs

    companion object {
        const val STILL_AFTER_MS = 2 * 60_000L
        private const val GRAVITY_FOLLOW = 0.02f
        private const val SHAKE_FOLLOW = 0.1f

        /** Shaking (m/s² around gravity, smoothed) that counts as a car moving, and as the most. */
        const val MOVING_SHAKE = 0.12f
        private const val FULL_SHAKE = 1.2f

        /** Walking pace and more. */
        const val MOVING_SPEED = 1.5f
    }
}
