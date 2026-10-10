package se.eldebosh.nastastopp.core.nav

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapmapApiTest {

    private fun stop(lat: Double?, lng: Double?, address: String = "Storgatan 14, Karlstad") = MapWay.Stop(lat, lng, address)

    private val skoghall = stop(59.3245, 13.4655)
    private val kil = stop(59.504, 13.238)

    @Test
    fun theWayIsAskedByPointsOnlyLongitudeFirst() {
        val url = MapmapApi.routeUrl(59.3794, 13.503, listOf(skoghall, kil))!!
        assertEquals(
            "https://api.mapmap.ai/route/v1/driving/13.503000,59.379400;13.465500,59.324500;13.238000,59.504000" +
                "?overview=full&geometries=polyline&steps=true",
            url,
        )
        // Never an address or a name: a stop without its point is left to Google.
        assertFalse(url.contains("Storgatan"))
        assertNull(MapmapApi.routeUrl(59.3794, 13.503, listOf(skoghall, stop(null, null))))
        assertFalse(MapmapApi.canAsk(listOf(skoghall, stop(59.0, null))))
        assertFalse(MapmapApi.canAsk(emptyList()))
        assertNull(MapmapApi.routeUrl(59.3794, 13.503, List(MapmapApi.MAX_STOPS + 1) { skoghall }))
        val body = MapmapApi.matrixBody(59.3794, 13.503, listOf(skoghall, kil))!!
        assertEquals(
            """{"sources":[{"lat":59.3794,"lon":13.503},{"lat":59.3245,"lon":13.4655},{"lat":59.504,"lon":13.238}],""" +
                """"targets":[{"lat":59.3245,"lon":13.4655},{"lat":59.504,"lon":13.238}],"costing":"auto"}""",
            body,
        )
        assertNull(MapmapApi.matrixBody(59.3794, 13.503, listOf(stop(null, null))))
    }

    /** The points as mapmap takes them, in its address (the driver's own key) or in a body to the company's server. */
    @Test
    fun pointsAreLongitudeFirstAndPointsOnly() {
        val south = stop(-33.865143, -151.2099)
        assertEquals("-70.123457,-12.500000;-151.209900,-33.865143", MapmapApi.points(-12.5, -70.1234567, listOf(south)))
        assertEquals("13.503000,59.379400;13.465500,59.324500;13.238000,59.504000", MapmapApi.points(59.3794, 13.503, listOf(skoghall, kil)))
        assertNull(MapmapApi.points(59.3794, 13.503, listOf(skoghall, stop(null, 13.0))))
        assertNull(MapmapApi.points(59.3794, 13.503, emptyList()))
        assertNull(MapmapApi.points(59.3794, 13.503, List(MapmapApi.MAX_STOPS + 1) { skoghall }))
        assertNull(MapmapApi.points(Double.NaN, 13.503, listOf(skoghall)))
        assertNull(MapmapApi.points(59.3794, 13.503, listOf(stop(Double.POSITIVE_INFINITY, 13.0))))
        assertEquals(MapmapApi.MAX_STOPS + 1, MapmapApi.points(59.3794, 13.503, List(MapmapApi.MAX_STOPS) { skoghall })!!.split(';').size)

        assertEquals("overview=full&geometries=polyline&steps=true", MapmapApi.ROUTE_OPTIONS)
        assertEquals(
            """{"points":"13.503000,59.379400;13.465500,59.324500;13.238000,59.504000","overview":"full","geometries":"polyline","steps":"true"}""",
            MapmapApi.serverRouteBody(59.3794, 13.503, listOf(skoghall, kil)),
        )
        // Never an address or a name: a stop without its point is left to Google.
        assertNull(MapmapApi.serverRouteBody(59.3794, 13.503, listOf(skoghall, stop(null, null))))
        assertFalse(MapmapApi.serverRouteBody(59.3794, 13.503, listOf(skoghall))!!.contains("Storgatan"))
    }

    /** An answer as mapmap gives it: each leg's line is its steps' lines, so each leg is drawn in its stop's colour. */
    @Test
    fun theWayIsReadWithALegToEachStop() {
        val answer = """
            {"code":"Ok","routes":[{"distance":40773.0,"duration":2298.4,"geometry":"gpliJwhlqAvvDfpAj~CbxC{`b@zlk@",
              "legs":[
                {"distance":9024.0,"duration":608.43,"summary":"Hammaröleden","steps":[
                  {"geometry":"gpliJwhlqAvvDfpA","duration":300,"name":"Drottninggatan"},
                  {"geometry":"oxfiJowiqAj~CbxC","duration":308.43,"name":"Hammaröleden"}]},
                {"distance":31749.0,"duration":1689.98,"steps":[
                  {"geometry":"cyaiJk~dqA{`b@zlk@","duration":1689.98,"name":"61"}]}
              ]}],"waypoints":[]}
        """.trimIndent()
        val line = MapmapApi.parse(answer)!!
        assertEquals(38, line.minutes)
        assertEquals(40773, line.meters)
        assertEquals(4, line.path.size)
        assertEquals(2, line.legs.size)
        assertEquals(608, line.legs[0].seconds)
        assertEquals(9024, line.legs[0].meters)
        // The steps meet at their ends: the meeting point once.
        assertEquals(listOf(59.3794 to 13.503, 59.35 to 13.49, 59.3245 to 13.4655), line.legs[0].path)
        assertEquals(59.504 to 13.238, line.legs[1].path.last())
        assertEquals(10 to 9024, line.to(0))
        assertEquals(38 to 40773, line.to(1))
        assertNull(MapmapApi.parse("""{"code":"NoRoute","routes":[]}"""))
        assertNull(MapmapApi.parse("not json"))
    }

    @Test
    fun theTravelTimesAreReadInEitherForm() {
        // Cells, as Valhalla gives them by default; null where there is no way.
        val cells = """
            {"sources_to_targets":[
              [{"from_index":0,"to_index":0,"time":600.5,"distance":9.0},{"from_index":0,"to_index":1,"time":2300,"distance":40.7}],
              [{"from_index":1,"to_index":0,"time":0,"distance":0},{"from_index":1,"to_index":1,"time":1690,"distance":31.7}],
              [{"from_index":2,"to_index":0,"time":1700,"distance":31.8},{"from_index":2,"to_index":1,"time":null,"distance":null}]
            ],"units":"kilometers"}
        """.trimIndent()
        val seconds = MapmapApi.parseMatrix(cells, 2)!!
        assertArrayEquals(intArrayOf(600, 2300), seconds[0])
        assertArrayEquals(intArrayOf(0, 1690), seconds[1])
        assertArrayEquals(intArrayOf(1700, RoutesApi.NO_WAY), seconds[2])
        // Rows of durations.
        val rows = """{"sources_to_targets":{"durations":[[600,2300],[0,1690],[1700,null]],"distances":[[9,40.7],[0,31.7],[31.8,null]]}}"""
        val same = MapmapApi.parseMatrix(rows, 2)!!
        for (i in 0..2) assertArrayEquals(seconds[i], same[i])
        assertNull(MapmapApi.parseMatrix("""{"sources_to_targets":[]}""", 2))
        assertNull(MapmapApi.parseMatrix("""{"detail":"unauthorised"}""", 2))
    }

    @Test
    fun aKeyIsCheckedBeforeItIsKept() {
        assertTrue(MapmapApi.isKey("snk_" + "a1B2c3D4e5F6g7H8"))
        assertFalse(MapmapApi.isKey(null))
        assertFalse(MapmapApi.isKey("snk_short"))
        assertFalse(MapmapApi.isKey("snk_has space in it 1234567"))
        assertFalse(MapmapApi.isKey("snk_<script>aaaaaaaaaaaaaa"))
    }
}
