package se.eldebosh.nastastopp.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** The offline street map: the road you are on, never a guess. Invented roads around a crossing. */
class StreetMatcherTest {

    private val lat0 = 59.38
    private val lon0 = 13.50
    private fun north(m: Double) = lat0 + m / 111_195.0
    private fun east(m: Double) = lon0 + m / (111_195.0 * Math.cos(Math.toRadians(lat0)))

    /** Storgatan runs east–west through the crossing, Kungsgatan north–south, Parkvägen 40 m north of Storgatan. */
    private val map = StreetMapBuilder().apply {
        add(1, "Storgatan", listOf(lat0 to east(-300.0), lat0 to east(300.0)))
        add(2, "Kungsgatan", listOf(north(-300.0) to lon0, north(300.0) to lon0))
        add(3, "Parkvägen", listOf(north(40.0) to east(-300.0), north(40.0) to east(300.0)))
        add(3, "Parkvägen", listOf(north(40.0) to east(-300.0), north(40.0) to east(300.0))) // the same road again (two tiles)
        add(4, null, listOf(north(5.0) to east(-100.0), north(5.0) to east(100.0))) // no name: not a street
        add(5, "Ensam", listOf(lat0 to lon0)) // one point: not a road
    }.build(0)

    private fun fix(northM: Double, eastM: Double, heading: Float? = null, speed: Float? = null, accuracy: Float = 5f) =
        Fix(0, north(northM), east(eastM), speed, accuracy, heading)

    @Test
    fun theNearestRoadIsTheOneYouAreOn() {
        assertEquals(3, map.roadCount)
        assertEquals("Storgatan", StreetMatcher.match(map, fix(5.0, -150.0)))
        assertEquals("Storgatan", StreetMatcher.match(map, fix(15.0, -150.0))) // nearer Storgatan than Parkvägen
        assertEquals("Parkvägen", StreetMatcher.match(map, fix(33.0, -150.0)))
        assertEquals("Kungsgatan", StreetMatcher.match(map, fix(-150.0, 6.0)))
    }

    @Test
    fun atACrossingTheHeadingDecides() {
        // 6 m north-east of the crossing: both roads are 6 m away.
        assertEquals("Storgatan", StreetMatcher.match(map, fix(6.0, 5.0, heading = 90f, speed = 10f))) // driving east
        assertEquals("Kungsgatan", StreetMatcher.match(map, fix(5.0, 6.0, heading = 180f, speed = 10f))) // driving south
        assertEquals("Storgatan", StreetMatcher.match(map, fix(4.0, 6.0, heading = 272f, speed = 10f))) // driving west
    }

    @Test
    fun noGuessFarFromRoadsOrWithABadPosition() {
        assertNull("100 m from every road", StreetMatcher.match(map, fix(-100.0, 150.0)))
        assertNull("a position 40 m unsure", StreetMatcher.match(map, fix(2.0, -150.0, accuracy = 40f)))
    }

    @Test
    fun theMapSurvivesItsFile() {
        val bytes = ByteArrayOutputStream().also { map.write(DataOutputStream(it)) }.toByteArray()
        val again = StreetMap.read(DataInputStream(ByteArrayInputStream(bytes)))
        assertEquals(map.names, again.names)
        assertEquals(map.roadCount, again.roadCount)
        assertEquals("Kungsgatan", StreetMatcher.match(again, fix(-150.0, 6.0)))
    }

    @Test
    fun aNewStreetNeedsTwoReadingsAndFadesWithoutRoads() {
        val t = StreetTracker()
        assertNull(t.onReading("Storgatan", 0))
        assertEquals("Storgatan", t.onReading("Storgatan", 2_000))
        assertEquals("one stray reading does not change it", "Storgatan", t.onReading("Kungsgatan", 4_000))
        assertEquals("Storgatan", t.onReading("Storgatan", 6_000))
        assertEquals("Storgatan", t.onReading("Kungsgatan", 8_000))
        assertEquals("Kungsgatan", t.onReading("Kungsgatan", 10_000))
        assertEquals("kept a moment without any road", "Kungsgatan", t.onReading(null, 20_000))
        assertNull(t.onReading(null, 31_000))
    }

    @Test
    fun anglesToRoadsGoBothWays() {
        assertEquals(0.0, StreetMap.angleToRoad(10.0, 190.0), 1e-9)
        assertEquals(90.0, StreetMap.angleToRoad(0.0, 90.0), 1e-9)
        assertEquals(20.0, StreetMap.angleToRoad(350.0, 10.0), 1e-9)
    }
}
