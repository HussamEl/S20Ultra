package se.eldebosh.nastastopp.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    /** Without the street map, a street counts only when its address is very close. */
    @Test
    fun aFarAddressGivesTheAreaButNoStreet() {
        val here = GeoResult(59.38, 13.5, "Storgatan 3", "66530", "Kil", "Kils centrum", "Storgatan")
        assertEquals(StreetInfo("Storgatan", "Kils centrum"), StreetLookup.pickNear(listOf(here), 59.38 + 20 / 111_195.0, 13.5))
        assertEquals(StreetInfo(null, "Kils centrum"), StreetLookup.pickNear(listOf(here), 59.38 + 80 / 111_195.0, 13.5))
        assertNull(StreetLookup.pickNear(emptyList(), 59.38, 13.5))
    }

    private val districts = Districts.parse(
        listOf(File("src/main/assets/${Districts.ASSET}"), File("app/src/main/assets/${Districts.ASSET}")).first { it.exists() }.readText(),
    )

    /**
     * The street bar's area: Karlstad's own district where the position lies in one; in Karlstad
     * outside them the town alone, never the geocoder's neighbourhood; elsewhere the geocoder's.
     */
    @Test
    fun theAreaIsKarlstadsOwnDistrictNeverTheGeocoders() {
        val lamberget = 59.37744 to 13.53694
        val outside = 59.4447 to 13.3374 // Karlstad's airport: in no district
        fun at(p: Pair<Double, Double>, town: String?, neighbourhood: String?) =
            StreetLookup.pickNear(listOf(GeoResult(p.first, p.second, null, null, town, neighbourhood, "Storgatan")), p.first, p.second, districts)
        assertEquals(StreetInfo("Storgatan", "Lamberget"), at(lamberget, "Karlstad", "Tormestad"))
        assertEquals(StreetInfo("Storgatan", "Karlstad"), at(outside, "Karlstad", "Lamberget"))
        assertEquals(StreetInfo("Storgatan", "Karlstad"), at(outside, "Karlstad", null))
        assertEquals(StreetInfo("Storgatan", "Kils centrum"), at(outside, "Kil", "Kils centrum"))
        assertEquals(StreetInfo("Storgatan", "Kil"), at(outside, "Kil", null))
        assertEquals(StreetInfo("Storgatan", null), at(outside, null, null))
        // Without the districts (tests), Karlstad is said without a district.
        assertEquals(StreetInfo(null, "Karlstad"), StreetLookup.pickNear(listOf(GeoResult(0.0, 0.0, null, null, "Karlstad", "Lamberget", null)), 59.38, 13.5))
    }
}
