package se.eldebosh.nastastopp.core.display

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class CarStillnessTest {
    /** A car on the road shakes the tablet: awake; still for two minutes: asleep; moving again: awake at once. */
    @Test
    fun theDisplayRestsTwoMinutesAfterTheCarStops() {
        val s = CarStillness()
        var t = 0L
        // Driving: gravity with a shake of about ±0.6 m/s², 50 readings a second.
        repeat(500) {
            s.onAcceleration(0f, 0f, 9.81f + 0.6f * sin(it * 1.3f), t)
            t += 20
        }
        assertTrue(s.awake(t))
        assertTrue("it shakes", s.level > 0.1f)
        // Parked: only the sensor's own noise.
        repeat(10_000) {
            s.onAcceleration(0f, 0f, 9.81f + 0.005f * sin(it * 2.1f), t)
            t += 20
        }
        assertFalse("still for over two minutes", s.awake(t))
        assertTrue("barely shakes", s.level < 0.05f)
        // A GPS speed wakes it at once, even on a smooth road.
        s.onSpeed(8f, t)
        assertTrue(s.awake(t))
        assertFalse(s.awake(t + CarStillness.STILL_AFTER_MS))
        // Walking pace does not count below it.
        s.onSpeed(0.5f, t + CarStillness.STILL_AFTER_MS)
        assertFalse(s.awake(t + CarStillness.STILL_AFTER_MS))
    }
}
