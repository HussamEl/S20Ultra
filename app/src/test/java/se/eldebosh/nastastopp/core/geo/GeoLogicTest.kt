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

    @Test
    fun choosePrefersPostalThenLocalityThenFirst() {
        val a = r("652 25", "Karlstad")
        val b = r("652 24", "Karlstad")
        val c = r("663 41", "Hammarö")
        assertEquals(b, GeoLogic.choose(listOf(a, b, c), "652 24", "Karlstad"))
        assertEquals(c, GeoLogic.choose(listOf(a, b, c), null, "HAMMARÖ"))
        assertEquals(a, GeoLogic.choose(listOf(a, b, c), "111 11", "Kiruna"))
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
}
