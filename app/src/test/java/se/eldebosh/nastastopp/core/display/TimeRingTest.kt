package se.eldebosh.nastastopp.core.display

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeRingTest {
    @Test
    fun theRingFollowsTheNextStopsTime() {
        assertEquals(listOf(null, null, TimeRing.SOON, TimeRing.SOON, TimeRing.DUE, TimeRing.LATE, TimeRing.LATE, TimeRing.VERY_LATE, TimeRing.VERY_LATE),
            listOf(null, 6, 5, 1, 0, -1, -4, -5, -30).map { TimeRing.of(it) })
    }
}
