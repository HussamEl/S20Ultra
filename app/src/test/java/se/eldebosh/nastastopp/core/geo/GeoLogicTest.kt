package se.eldebosh.nastastopp.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.eldebosh.nastastopp.core.parse.TestLocalities
import java.io.File

class GeoLogicTest {

    private fun r(postal: String?, locality: String?, sub: String? = null) =
        GeoResult(59.0, 13.0, "x", postal, locality, sub, "Storgatan")

    /** The written postal code first, then the written town; a point in another place never; the first only when nothing is written. */
    @Test
    fun choosePrefersPostalThenLocalityAndNeverAnotherPlace() {
        val a = r("652 25", "Karlstad")
        val b = r("652 24", "Karlstad")
        val c = r("663 41", "Hammarö")
        assertEquals(b, GeoLogic.choose(listOf(a, b, c), "652 24", "Karlstad"))
        assertEquals(c, GeoLogic.choose(listOf(a, b, c), null, "HAMMARÖ"))
        // Another postal code in the written town still counts.
        assertEquals(a, GeoLogic.choose(listOf(a, c), "652 99", "Karlstad"))
        assertEquals(null, GeoLogic.choose(listOf(a, b, c), "111 11", "Kiruna"))
        assertEquals(null, GeoLogic.choose(listOf(a, b), null, "Grums"))
        assertEquals(a, GeoLogic.choose(listOf(a, b, c), null, null))
        assertEquals(null, GeoLogic.choose(emptyList(), null, null))
    }

    @Test
    fun spokenNameRules() {
        val d = AnnouncementDetail.DISTRICT
        assertEquals("Herrhagen", GeoLogic.spokenName("Herrhagen", "Karlstad", null, false, d))
        assertEquals("Karlstad", GeoLogic.spokenName("Karlstad", "Karlstad", null, false, d))
        assertEquals("Karlstad", GeoLogic.spokenName("Herrhagen", "Karlstad", null, false, AnnouncementDetail.TOWN_ONLY))
        assertEquals("Grums", GeoLogic.spokenName(null, null, "Grums", true, d))
        assertEquals("nästa adress", GeoLogic.spokenName(null, null, "Andersson", false, d))
        assertEquals("nästa adress", GeoLogic.spokenName(null, null, null, false, d))
    }

    @Test
    fun spokenNameNeverContainsStreetOrNumber() {
        val d = AnnouncementDetail.DISTRICT
        assertEquals("Karlstad", GeoLogic.spokenName("Storgatan 14", "Karlstad", null, false, d))
        assertEquals("Karlstad", GeoLogic.spokenName("Storgatan", "Karlstad", null, false, d, thoroughfare = "Storgatan"))
        assertEquals("nästa adress", GeoLogic.spokenName("Lindvägen", null, null, false, d))
        assertEquals("Smedjebacken", GeoLogic.spokenName(null, "Smedjebacken", null, false, d))
        assertFalse(GeoLogic.isSafeAreaName("Järnvägsgatan"))
        assertFalse(GeoLogic.isSafeAreaName("12"))
    }

    @Test
    fun everyBundledLocalityIsASafeSpokenName() {
        val file = listOf(File("src/main/assets/localities_se.txt"), File("app/src/main/assets/localities_se.txt")).first { it.exists() }
        val names = file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val unsafe = names.filterNot { GeoLogic.isSafeAreaName(it, strict = false) }
        assertTrue("unsafe: $unsafe", unsafe.isEmpty())
        assertTrue(TestLocalities.instance.size > 290)
    }

    @Test
    fun distance() {
        val d = GeoLogic.distanceMeters(59.3793, 13.5036, 59.3793, 13.5036 + 0.001)
        assertTrue(d in 55.0..60.0)
    }

    /** The next stop said in full — street and number, district, town — each part once. */
    @Test
    fun fullSpokenNameSaysStreetDistrictAndTownOnce() {
        assertEquals("Storgatan 14, Herrhagen, Karlstad", GeoLogic.fullSpokenName("Storgatan 14", "Herrhagen", "Karlstad"))
        assertEquals("Storgatan 14, Karlstad", GeoLogic.fullSpokenName("Storgatan 14", "Karlstad", "Karlstad"))
        assertEquals("Storgatan 14, Hammarö", GeoLogic.fullSpokenName("Storgatan 14", "Hammarö", "HAMMARÖ"))
        assertEquals("Björkvägen 7, Hammarö", GeoLogic.fullSpokenName("Björkvägen 7 Lgh 1102", "Hammarö", "Hammarö"))
        assertEquals("Storgatan 14", GeoLogic.fullSpokenName("Storgatan 14", GeoLogic.NEXT_ADDRESS, GeoLogic.NEXT_ADDRESS))
        assertEquals(GeoLogic.NEXT_ADDRESS, GeoLogic.fullSpokenName("", null, null))
        // A town already said in the place's own name is not said again.
        assertEquals("Centralsjukhuset Karlstad", GeoLogic.fullSpokenName("Centralsjukhuset Karlstad", "Karlstad", "Karlstad"))
        assertEquals("Centralsjukhuset, huvudentrén, Karlstad", GeoLogic.fullSpokenName("Centralsjukhuset, huvudentrén", null, "Karlstad"))
        // Only whole words count: "Karlstadsvägen" is not "Karlstad".
        assertEquals("Karlstadsvägen 3, Karlstad", GeoLogic.fullSpokenName("Karlstadsvägen 3", "Karlstad", "Karlstad"))
    }

    private fun at(lat: Double, lng: Double, town: String?) = GeoResult(lat, lng, null, null, town, null, null)

    /** A place written without a town is taken only when it is in one town in Värmland: never a guess. */
    @Test
    fun aPlaceWithoutATownMustBeInOneTown() {
        val karlstad = at(59.38, 13.50, "Karlstad")
        assertEquals(karlstad, GeoLogic.inOneTown(listOf(karlstad, at(59.39, 13.51, "Karlstad"))))
        assertEquals(null, GeoLogic.inOneTown(listOf(karlstad, at(59.57, 13.21, "Kil"))))
        // Outside Värmland it does not count.
        assertEquals(karlstad, GeoLogic.inOneTown(listOf(karlstad, at(56.03, 14.15, "Kristianstad"))))
        assertEquals(null, GeoLogic.inOneTown(listOf(at(56.03, 14.15, "Kristianstad"))))
        assertEquals(null, GeoLogic.inOneTown(listOf(at(59.38, 13.50, null))))
        assertEquals(null, GeoLogic.inOneTown(emptyList()))
    }
}
