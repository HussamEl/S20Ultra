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
        val toPoint = RoutesApi.body(59.38, 13.5, 59.4, 13.52, "Hamngatan 7, Skoghall")!!
        assertTrue(toPoint, toPoint.contains("\"destination\":{\"location\":{\"latLng\":{\"latitude\":59.4,\"longitude\":13.52}}}"))
        assertTrue(toPoint, toPoint.contains("\"travelMode\":\"DRIVE\""))
        val toAddress = RoutesApi.body(59.38, 13.5, null, null, "Hamngatan 7, Skoghall")!!
        assertTrue(toAddress, toAddress.contains("\"destination\":{\"address\":\"Hamngatan 7, Skoghall\"}"))
        assertNull(RoutesApi.body(59.38, 13.5, null, null, null))
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

    @Test
    fun onlyKeysAsGoogleIssuesThem() {
        assertTrue(RoutesApi.isKey("AIzaSyA-1234567890abcdefghijklmnopqrstu"))
        assertFalse(RoutesApi.isKey("abc"))
        assertFalse(RoutesApi.isKey("AIzaSy\"><script>alert(1)</script>"))
        assertFalse(RoutesApi.isKey(null))
    }
}
