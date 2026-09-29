package se.eldebosh.nastastopp.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.eldebosh.nastastopp.core.route.Fix

/** When the current street is looked up, and which street is shown. */
class StreetLookupTest {

    private val lat0 = 59.38

    /** A fix [m] metres north of the start at [s] seconds. */
    private fun fix(s: Long, m: Double, acc: Float = 5f) = Fix(s * 1000, lat0 + m / 111_195.0, 13.5, null, acc)

    @Test
    fun firstGoodFixIsLookedUp() {
        assertTrue(StreetLookup.shouldLookup(null, fix(0, 0.0), lastFailed = false))
        assertFalse("inaccurate fix", StreetLookup.shouldLookup(null, fix(0, 0.0, acc = 120f), lastFailed = false))
    }

    @Test
    fun throttledByTimeAndDistance() {
        val last = fix(0, 0.0)
        assertFalse("too soon", StreetLookup.shouldLookup(last, fix(4, 100.0), lastFailed = false))
        assertFalse("not moved", StreetLookup.shouldLookup(last, fix(20, 10.0), lastFailed = false))
        assertTrue(StreetLookup.shouldLookup(last, fix(9, 60.0), lastFailed = false))
    }

    @Test
    fun failedLookupIsRetriedWhileStandingStill() {
        val last = fix(0, 0.0)
        assertFalse(StreetLookup.shouldLookup(last, fix(20, 0.0), lastFailed = true))
        assertTrue(StreetLookup.shouldLookup(last, fix(31, 0.0), lastFailed = true))
    }

    @Test
    fun picksTheFirstResultWithAStreet() {
        val results = listOf(
            GeoResult(0.0, 0.0, "Kvarnholmen", null, "Karlstad", null, null),
            GeoResult(0.0, 0.0, "Drottninggatan 3", "65225", "Karlstad", "Centrum", "Drottninggatan"),
        )
        assertEquals(StreetInfo("Drottninggatan", "Centrum"), StreetLookup.pick(results))
        assertEquals("Drottninggatan, Centrum", StreetLookup.pick(results)!!.spoken)
    }

    @Test
    fun areaOnlyWhenNoStreet() {
        val info = StreetLookup.pick(listOf(GeoResult(0.0, 0.0, null, null, "Kil", null, null)))
        assertEquals(StreetInfo(null, "Kil"), info)
        assertEquals("Kil", info!!.spoken)
        assertNull(StreetLookup.pick(emptyList()))
        assertNull(StreetLookup.pick(listOf(GeoResult(0.0, 0.0, null, null, null, null, " "))))
    }
}
