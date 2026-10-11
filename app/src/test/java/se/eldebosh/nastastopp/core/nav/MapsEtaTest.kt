package se.eldebosh.nastastopp.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapsEtaTest {
    private val now = 12 * 60 + 36

    @Test
    fun theDurationWhenMapsShowsOne() {
        assertEquals(DisplayEta(12, 5300), MapsEta.parse(listOf("Turn right onto Storgatan", "12 min · 5.3 km · 12:48 ETA"), now))
        assertEquals(DisplayEta(12, 5300), MapsEta.parse(listOf("Sväng höger", "12 min · 5,3 km · Ankomst 12:48"), now))
        assertEquals(DisplayEta(65, null), MapsEta.parse(listOf("1 h 5 min"), now))
        // Arabic: "7 minutes · 800 m" in Arabic-Indic digits.
        assertEquals(DisplayEta(7, 800), MapsEta.parse(listOf("\u0667 \u062F\u0642\u0627\u0626\u0642 \u00B7 \u0668\u0660\u0660 \u0645"), now))
    }

    @Test
    fun theArrivalTimeWhenThereIsNoDuration() {
        assertEquals(DisplayEta(12, null), MapsEta.parse(listOf("Ankomst 12:48"), now))
    }

    @Test
    fun nothingWithoutTimes() {
        assertNull(MapsEta.parse(listOf("Turn right onto Storgatan"), now))
        assertNull(MapsEta.parse(emptyList(), now))
    }
}
