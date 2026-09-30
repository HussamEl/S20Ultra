package se.eldebosh.nastastopp.core.display

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeStatusTest {
    @Test
    fun theStatusFollowsTheNextStopsTime() {
        assertEquals(
            listOf(TimeStatus.ON_TIME, TimeStatus.ON_TIME, TimeStatus.SOON, TimeStatus.SOON, TimeStatus.DUE, TimeStatus.LATE, TimeStatus.LATE, TimeStatus.VERY_LATE, TimeStatus.VERY_LATE),
            listOf(null, 6, 5, 1, 0, -1, -4, -5, -30).map { TimeStatus.of(it) },
        )
    }

    @Test
    fun theCountdownReadsMinutesAndSeconds() {
        assertEquals("12:05", TimeStatus.countdown(725))
        assertEquals("0:09", TimeStatus.countdown(9))
        assertEquals("1:04:30", TimeStatus.countdown(3870))
        assertEquals("+3:10", TimeStatus.countdown(-190))
        assertEquals("0:00", TimeStatus.countdown(0))
    }
}
