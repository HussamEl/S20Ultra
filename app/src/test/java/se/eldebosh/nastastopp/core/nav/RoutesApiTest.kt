package se.eldebosh.nastastopp.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesApiTest {
    @Test
    fun decodesGooglesPolyline() {
        // Google's own example.
        val path = RoutesApi.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453), path)
    }

    @Test
    fun asksFromTheVehicleToTheStopsPointOrAddress() {
        val toPoint = RoutesApi.body(59.38, 13.5, listOf(MapWay.Stop(59.4, 13.52, "Hamngatan 7, Skoghall")))!!
        assertTrue(toPoint, toPoint.contains("\"destination\":{\"location\":{\"latLng\":{\"latitude\":59.4,\"longitude\":13.52}}}"))
        assertTrue(toPoint, toPoint.contains("\"travelMode\":\"DRIVE\""))
        assertFalse(toPoint, toPoint.contains("intermediates"))
        val toAddress = RoutesApi.body(59.38, 13.5, listOf(MapWay.Stop(null, null, "Hamngatan 7, Skoghall")))!!
        assertTrue(toAddress, toAddress.contains("\"destination\":{\"address\":\"Hamngatan 7, Skoghall\"}"))
        assertNull(RoutesApi.body(59.38, 13.5, listOf(MapWay.Stop(null, null, " "))))
        assertNull(RoutesApi.body(59.38, 13.5, emptyList()))
    }

    /** Through several stops: each in turn, the last the destination; only places, never a name. */
    @Test
    fun asksTheWayThroughEveryStopInTurn() {
        val body = RoutesApi.body(
            59.38,
            13.5,
            listOf(
                MapWay.Stop(59.4, 13.52, "Hamngatan 7", id = 3, time = "08:05", label = "Hamngatan 7", name = "Testsson"),
                MapWay.Stop(null, null, "Storgatan 14, Karlstad", id = 4, name = "Provare"),
                MapWay.Stop(59.5, 13.6, "Skolgatan 5", id = 5),
            ),
        )!!
        assertTrue(body, body.contains("\"intermediates\":[{\"location\":{\"latLng\":{\"latitude\":59.4,\"longitude\":13.52}}},{\"address\":\"Storgatan 14, Karlstad\"}]"))
        assertTrue(body, body.contains("\"destination\":{\"location\":{\"latLng\":{\"latitude\":59.5,\"longitude\":13.6}}}"))
        assertFalse(body, body.contains("\"id\""))
        // The pin's time and last name stay on the page.
        assertFalse(body, body.contains("Testsson") || body.contains("Provare") || body.contains("08:05"))
        val matrix = RoutesApi.matrixBody(59.38, 13.5, listOf(MapWay.Stop(59.4, 13.52, "Hamngatan 7", name = "Testsson"), MapWay.Stop(59.5, 13.6, "Skolgatan 5", name = "Provare")))!!
        assertFalse(matrix, matrix.contains("Testsson") || matrix.contains("Provare"))
    }

    @Test
    fun readsTheFirstRoute() {
        val answer = """{"routes":[{"distanceMeters":5300,"duration":"731s","polyline":{"encodedPolyline":"_p~iF~ps|U_ulLnnqC"}}]}"""
        val route = RoutesApi.parse(answer)!!
        assertEquals(12, route.minutes)
        assertEquals(5300, route.meters)
        assertEquals(2, route.path.size)
        assertNull(RoutesApi.parse("{}"))
        assertNull(RoutesApi.parse("""{"error":{"code":403}}"""))
        assertNull(RoutesApi.parse("not json"))
    }

    /** Each leg's time, length and line; the minutes to a stop are its leg and all before it. */
    @Test
    fun readsEachLeg() {
        val answer = """{"routes":[{"distanceMeters":9000,"duration":"900s","polyline":{"encodedPolyline":"_p~iF~ps|U_ulLnnqC"},""" +
            """"legs":[{"distanceMeters":4000,"duration":"300s","polyline":{"encodedPolyline":"_p~iF~ps|U"}},{"distanceMeters":5000,"duration":"600s","polyline":{"encodedPolyline":"_ulLnnqC"}}]}]}"""
        val route = RoutesApi.parse(answer)!!
        assertEquals(2, route.legs.size)
        assertEquals(300, route.legs[0].seconds)
        assertEquals(1, route.legs[1].path.size)
        assertEquals(5 to 4000, route.to(0))
        assertEquals(15 to 9000, route.to(1))
    }

    /** The matrix: from the vehicle and each stop to each stop; an index of 0 is left out by Google. */
    @Test
    fun asksAndReadsTheTravelTimesBetweenTheStops() {
        val stops = listOf(MapWay.Stop(59.4, 13.52, "A"), MapWay.Stop(59.5, 13.6, "B"))
        val body = RoutesApi.matrixBody(59.38, 13.5, stops)!!
        assertTrue(body, body.startsWith("{\"origins\":[{\"waypoint\":{\"location\":{\"latLng\":{\"latitude\":59.38"))
        val answer = """[{"destinationIndex":1,"duration":"700s","condition":"ROUTE_EXISTS"},{"originIndex":1,"duration":"100s","condition":"ROUTE_EXISTS"},""" +
            """{"originIndex":2,"destinationIndex":0,"condition":"ROUTE_NOT_FOUND"},{"duration":"400s","condition":"ROUTE_EXISTS"}]"""
        val seconds = RoutesApi.parseMatrix(answer, 2)!!
        assertEquals(400, seconds[0][0])
        assertEquals(700, seconds[0][1])
        assertEquals(100, seconds[1][0])
        assertEquals(RoutesApi.NO_WAY, seconds[2][0])
        assertNull(RoutesApi.parseMatrix("""{"error":{"code":403}}""", 2))
        // Places by address: at most 50 answers.
        val many = List(7) { MapWay.Stop(null, null, "Gatan $it") }
        assertNull(RoutesApi.matrixBody(59.38, 13.5, many))
    }

    @Test
    fun onlyKeysAsGoogleIssuesThem() {
        assertTrue(RoutesApi.isKey("AIzaSyA-1234567890abcdefghijklmnopqrstu"))
        assertFalse(RoutesApi.isKey("abc"))
        assertFalse(RoutesApi.isKey("AIzaSy\"><script>alert(1)</script>"))
        assertFalse(RoutesApi.isKey(null))
    }
}
