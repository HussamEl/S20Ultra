package se.eldebosh.nastastopp.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinatesTest {
    private fun assertPoint(lat: Double, lng: Double, text: String) {
        val p = Coordinates.parse(text) ?: throw AssertionError("no point in: $text")
        assertEquals(text, lat, p.first, 0.000_002)
        assertEquals(text, lng, p.second, 0.000_002)
    }

    /** As Google Maps writes a point, and as decimals (an invented point). */
    @Test
    fun degreesMinutesSecondsAsGoogleMapsWritesThem() {
        assertPoint(59.38, 13.5, "59°22'48.0\"N 13°30'00.0\"E")
        assertPoint(59.38, 13.5, "59° 22′ 48,0″ N, 13° 30′ 0″ E")
    }

    @Test
    fun decimalDegreesAndLinks() {
        assertPoint(59.381234, 13.501234, "59.381234, 13.501234")
        assertPoint(59.381234, 13.501234, "59.381234 13.501234")
        assertPoint(59.381234, 13.501234, "59,381234 13,501234")
        assertPoint(59.381234, 13.501234, "https://www.google.com/maps/search/?api=1&query=59.381234%2C13.501234")
        assertPoint(59.3812, 13.5012, "https://www.google.com/maps/@59.3812,13.5012,17z")
    }

    /** Nothing that is not a point in Sweden. */
    @Test
    fun onlyAPointInSweden() {
        assertNull(Coordinates.parse("Strandvägen 3, 665 30 Kil"))
        assertNull(Coordinates.parse("40.712800, -74.006000"))
        assertNull(Coordinates.parse(""))
    }

    @Test
    fun writtenWithSixDecimals() {
        assertEquals("59.381234, 13.501234", Coordinates.format(59.3812341, 13.5012339))
    }
}
