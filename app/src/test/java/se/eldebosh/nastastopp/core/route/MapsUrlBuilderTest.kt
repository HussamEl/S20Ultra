package se.eldebosh.nastastopp.core.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapsUrlBuilderTest {

    @Test
    fun singleStopHasNoWaypoints() {
        val url = MapsUrlBuilder.buildUrl(listOf("Storgatan 14, 652 24 Karlstad"))
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=Storgatan%2014%2C%20652%2024%20Karlstad" +
                "&travelmode=driving&dir_action=navigate",
            url,
        )
        assertFalse(url.contains("waypoints"))
    }

    @Test
    fun encodesSwedishLettersAndSeparatesWaypointsWithPipe() {
        val url = MapsUrlBuilder.buildUrl(listOf("Björkvägen 7, 663 41 Hammarö", "Järnvägsgatan 3B, 688 30 Storfors", "Lindvägen 9, 664 30"))
        assertTrue(url, url.contains("destination=Lindv%C3%A4gen%209%2C%20664%2030&"))
        assertTrue(url, url.contains("&waypoints=Bj%C3%B6rkv%C3%A4gen%207%2C%20663%2041%20Hammar%C3%B6%7CJ%C3%A4rnv%C3%A4gsgatan%203B%2C%20688%2030%20Storfors&"))
        assertTrue(url.endsWith("&travelmode=driving&dir_action=navigate"))
        assertFalse(url.contains(" "))
        assertFalse(url.contains("+"))
    }

    @Test
    fun specialCharactersAreEscaped() {
        val url = MapsUrlBuilder.buildUrl(listOf("A&B|C#D?E=F+G"))
        assertTrue(url, url.contains("destination=A%26B%20C%23D%3FE%3DF%2BG&"))
    }

    @Test
    fun batches23StopsInto10_10_3() {
        val stops = (1..23).map { "Gata $it, Karlstad" }
        val batches = MapsUrlBuilder.batches(stops)
        assertEquals(listOf(10, 10, 3), batches.map { it.size })
        assertEquals("Gata 11, Karlstad", batches[1].first())
        // Each URL has at most 9 waypoints + 1 destination.
        batches.forEach { b ->
            val url = MapsUrlBuilder.buildUrl(b)
            val waypointCount = url.substringAfter("waypoints=", "").substringBefore("&").let { if (it.isEmpty()) 0 else it.split("%7C").size }
            assertEquals(b.size - 1, waypointCount)
            assertTrue(url.contains("destination=" + MapsUrlBuilder.encode(b.last())))
        }
    }

    @Test
    fun buildUrlCapsAtTenStops() {
        val stops = (1..15).map { "S$it" }
        val url = MapsUrlBuilder.buildUrl(stops)
        assertTrue(url.contains("destination=S10&"))
        assertEquals(9, url.substringAfter("waypoints=").substringBefore("&").split("%7C").size)
    }
}
