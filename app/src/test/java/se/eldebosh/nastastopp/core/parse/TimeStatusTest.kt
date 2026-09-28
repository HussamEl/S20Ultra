package se.eldebosh.nastastopp.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Next trip's scheduled time compared with now (on time / soon / late). */
class TimeStatusTest {

    private fun at(h: Int, m: Int) = h * 60 + m

    @Test
    fun minutesUntilScheduledTime() {
        assertEquals(18, TripTimes.minutesUntil("12:48", at(12, 30)))
        assertEquals(0, TripTimes.minutesUntil("12:48", at(12, 48)))
        assertEquals(-7, TripTimes.minutesUntil("12:48", at(12, 55)))
        assertEquals(95, TripTimes.minutesUntil("14:05", at(12, 30)))
        assertNull(TripTimes.minutesUntil(null, at(12, 30)))
    }

    @Test
    fun wrapsAroundMidnight() {
        assertEquals(20, TripTimes.minutesUntil("00:10", at(23, 50)))
        assertEquals(-15, TripTimes.minutesUntil("23:55", at(0, 10)))
        // Far ahead the same day is not "late".
        assertEquals(778, TripTimes.minutesUntil("23:59", at(11, 1)))
        assertEquals(-470, TripTimes.minutesUntil("08:00", at(15, 50)))
    }

    @Test
    fun levels() {
        assertEquals(TimeLevel.AHEAD, TripTimes.level(6))
        assertEquals(TimeLevel.SOON, TripTimes.level(5))
        assertEquals(TimeLevel.SOON, TripTimes.level(0))
        assertEquals(TimeLevel.LATE, TripTimes.level(-1))
    }
}
