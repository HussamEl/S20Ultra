package se.eldebosh.nastastopp.core.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * Each ask of the tablet's map, by its access: through the company's server with the pass in a
 * header, or as before with the driver's own keys; places only, never a name, a label or a time.
 * The stops, names and pass are the invented ones of testdata/README.md.
 */
class WayRequestsTest {

    private val tokenText = "ab12".repeat(16)
    private val token = DeviceToken(tokenText)
    private val googleKey = "AIzaSyA-1234567890abcdefghijklmnopqrstu"
    private val mapmapKey = "snk_" + "a1B2c3D4e5F6g7H8"

    private val company = WayAccess.Company(token, google = true, mapmap = true)
    private val own = WayAccess.Own(googleKey, mapmapKey)

    private val lat = 59.3794
    private val lng = 13.503

    /** Every stop with its point. */
    private val located = listOf(
        MapWay.Stop(59.3245, 13.4655, "Hamngatan 7, 663 30 Skoghall", id = 3, time = "08:05", label = "Hamngatan", name = "Testsson"),
        MapWay.Stop(59.504, 13.238, "Skolgatan 5, 664 30 Grums", id = 5, time = "10:20", label = "Skolgatan", name = "Provare"),
    )

    /** One stop known only by its address. */
    private val unlocated = located + MapWay.Stop(null, null, "Storgatan 14, 652 24 Karlstad", id = 4, time = "07:30", label = "Storgatan", name = "Exempel")

    @Test
    fun aGoogleWayThroughTheServerCarriesThePassAndTheFieldMaskOnly() {
        val r = WayRequests.route(company, WaySource.GOOGLE, lat, lng, located)!!
        assertEquals(WayService.GOOGLE_ROUTES, r.service)
        assertEquals("https://api.nastastopp.se/v1/google/routes", r.ask.url)
        assertTrue(r.ask.post)
        assertTrue(r.ask.viaServer)
        assertEquals(
            mapOf("Authorization" to "Bearer $tokenText", "X-Goog-FieldMask" to RoutesApi.FIELDS, "User-Agent" to "NastaStopp"),
            r.ask.headers,
        )
        assertFalse(r.ask.headers.containsKey("X-Goog-Api-Key"))
        assertFalse(r.ask.headers.containsKey("Referer"))
        assertEquals(RoutesApi.body(lat, lng, located), r.ask.body)
        assertEquals(25_000, r.ask.readTimeoutMs)
    }

    @Test
    fun googleTravelTimesThroughTheServerCarryTheMatrixFieldMask() {
        val r = WayRequests.matrix(company.copy(mapmap = false), WaySource.MAPMAP, lat, lng, located)!!
        assertEquals(WayService.GOOGLE_MATRIX, r.service)
        assertEquals("https://api.nastastopp.se/v1/google/matrix", r.ask.url)
        assertEquals(
            mapOf("Authorization" to "Bearer $tokenText", "X-Goog-FieldMask" to RoutesApi.MATRIX_FIELDS, "User-Agent" to "NastaStopp"),
            r.ask.headers,
        )
        assertEquals(RoutesApi.matrixBody(lat, lng, located), r.ask.body)
        assertEquals(25_000, r.ask.readTimeoutMs)
        assertTrue(r.ask.viaServer)
    }

    @Test
    fun mapmapThroughTheServerIsAPostOfPointsOnly() {
        val r = WayRequests.route(company, WaySource.MAPMAP, lat, lng, located)!!
        assertEquals(WayService.MAPMAP_ROUTE, r.service)
        assertEquals("https://api.nastastopp.se/v1/mapmap/route", r.ask.url)
        assertTrue(r.ask.post)
        assertEquals(mapOf("Authorization" to "Bearer $tokenText", "User-Agent" to "NastaStopp"), r.ask.headers)
        assertEquals(25_000, r.ask.readTimeoutMs)
        val body = Json.parseToJsonElement(r.ask.body!!).jsonObject
        assertEquals(setOf("points", "overview", "geometries", "steps"), body.keys)
        assertEquals("13.503000,59.379400;13.465500,59.324500;13.238000,59.504000", body["points"]!!.jsonPrimitive.content)
        assertTrue(serverTakesPoints(body["points"]!!.jsonPrimitive.content))
        assertFalse(r.ask.body.contains("gatan"))
        // No point in the address: nothing for a web server's log.
        assertFalse(r.ask.url.contains("13.5"))

        val matrix = WayRequests.matrix(company, WaySource.MAPMAP, lat, lng, located)!!
        assertEquals(WayService.MAPMAP_MATRIX, matrix.service)
        assertEquals("https://api.nastastopp.se/v1/mapmap/matrix", matrix.ask.url)
        assertEquals(mapOf("Authorization" to "Bearer $tokenText", "User-Agent" to "NastaStopp"), matrix.ask.headers)
        assertEquals(MapmapApi.matrixBody(lat, lng, located), matrix.ask.body)
    }

    @Test
    fun aStopWithoutItsPointGoesToGoogleThroughTheServer() {
        val r = WayRequests.route(company, WaySource.MAPMAP, lat, lng, unlocated)!!
        assertEquals(WayService.GOOGLE_ROUTES, r.service)
        assertEquals(CompanyServer.ROUTES, r.ask.url)
        assertEquals(RoutesApi.body(lat, lng, unlocated), r.ask.body)
        assertEquals(WayService.GOOGLE_MATRIX, WayRequests.matrix(company, WaySource.MAPMAP, lat, lng, unlocated)!!.service)
    }

    @Test
    fun withoutMapmapOnTheServerGoogleIsAsked() {
        val noMapmap = company.copy(mapmap = false)
        val r = WayRequests.route(noMapmap, WaySource.MAPMAP, lat, lng, located)!!
        assertEquals(WayService.GOOGLE_ROUTES, r.service)
        assertEquals(CompanyServer.ROUTES, r.ask.url)
        assertEquals(WayService.GOOGLE_ROUTES, WayRequests.routeService(noMapmap, WaySource.MAPMAP, located))
        assertEquals(WayService.MAPMAP_ROUTE, WayRequests.routeService(company, WaySource.MAPMAP, located))
        assertEquals(WayService.GOOGLE_ROUTES, WayRequests.routeService(company, WaySource.GOOGLE, located))
    }

    /** The driver's own keys: the requests as they have always been, byte for byte. */
    @Test
    fun ownKeysAskAsBefore() {
        val google = WayRequests.route(own, WaySource.GOOGLE, lat, lng, located)!!
        assertEquals(WayService.GOOGLE_ROUTES, google.service)
        assertEquals("https://routes.googleapis.com/directions/v2:computeRoutes", google.ask.url)
        assertEquals(
            mapOf("X-Goog-Api-Key" to googleKey, "X-Goog-FieldMask" to RoutesApi.FIELDS, "Referer" to "https://nastastopp.app/"),
            google.ask.headers,
        )
        assertEquals(RoutesApi.body(lat, lng, located), google.ask.body)
        assertEquals(15_000, google.ask.readTimeoutMs)
        assertFalse(google.ask.viaServer)

        val googleMatrix = WayRequests.matrix(own, WaySource.GOOGLE, lat, lng, located)!!
        assertEquals("https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix", googleMatrix.ask.url)
        assertEquals(
            mapOf("X-Goog-Api-Key" to googleKey, "X-Goog-FieldMask" to RoutesApi.MATRIX_FIELDS, "Referer" to "https://nastastopp.app/"),
            googleMatrix.ask.headers,
        )
        assertEquals(RoutesApi.matrixBody(lat, lng, located), googleMatrix.ask.body)

        val mapmap = WayRequests.route(own, WaySource.MAPMAP, lat, lng, located)!!
        assertEquals(WayService.MAPMAP_ROUTE, mapmap.service)
        assertEquals(
            "https://api.mapmap.ai/route/v1/driving/13.503000,59.379400;13.465500,59.324500;13.238000,59.504000" +
                "?overview=full&geometries=polyline&steps=true",
            mapmap.ask.url,
        )
        assertFalse(mapmap.ask.post)
        assertNull(mapmap.ask.body)
        assertEquals(mapOf("Authorization" to "Bearer $mapmapKey"), mapmap.ask.headers)
        assertEquals(15_000, mapmap.ask.readTimeoutMs)
        assertFalse(mapmap.ask.viaServer)

        val mapmapMatrix = WayRequests.matrix(own, WaySource.MAPMAP, lat, lng, located)!!
        assertEquals("https://api.mapmap.ai/matrix", mapmapMatrix.ask.url)
        assertEquals(mapOf("Authorization" to "Bearer $mapmapKey"), mapmapMatrix.ask.headers)
        assertEquals(MapmapApi.matrixBody(lat, lng, located), mapmapMatrix.ask.body)
    }

    @Test
    fun ownMapmapWithoutItsKeyAsksGoogleDirectly() {
        for (access in listOf(own.copy(mapmapKey = null), own.copy(mapmapKey = "snk_short"))) {
            val r = WayRequests.route(access, WaySource.MAPMAP, lat, lng, located)!!
            assertEquals(WayService.GOOGLE_ROUTES, r.service)
            assertEquals(RoutesApi.URL, r.ask.url)
            assertFalse(r.ask.viaServer)
        }
        // A Google key that is not one is never sent.
        assertNull(WayRequests.route(own.copy(googleKey = "abc"), WaySource.GOOGLE, lat, lng, located))
    }

    @Test
    fun withNoAccessNothingIsAsked() {
        for (source in WaySource.entries) {
            assertNull(WayRequests.route(WayAccess.None, source, lat, lng, located))
            assertNull(WayRequests.matrix(WayAccess.None, source, lat, lng, located))
        }
        // Nor without stops.
        assertNull(WayRequests.route(company, WaySource.GOOGLE, lat, lng, emptyList()))
        assertNull(WayRequests.route(own, WaySource.GOOGLE, lat, lng, emptyList()))
    }

    /** Places only: never a name, a label or a time; the pass only in a header; the driver's keys never to the server. */
    @Test
    fun onlyPlacesLeaveAndThePassOnlyInItsHeader() {
        val asks = buildList {
            for (access in listOf(company, company.copy(mapmap = false), own, own.copy(mapmapKey = null))) {
                for (source in WaySource.entries) {
                    for (stops in listOf(located, unlocated)) {
                        add(access to WayRequests.route(access, source, lat, lng, stops)!!)
                        add(access to WayRequests.matrix(access, source, lat, lng, stops)!!)
                    }
                }
            }
        }
        assertEquals(32, asks.size)
        for ((access, r) in asks) {
            val text = r.ask.url + " " + r.ask.body.orEmpty()
            for (word in listOf("Testsson", "Provare", "Exempel", "08:05", "10:20", "07:30", "\"id\"")) {
                assertFalse("$word: $r", text.contains(word))
            }
            // A located stop's label (its street) is never sent; only a stop without a point goes by its address.
            assertFalse(text.contains("Hamngatan") || text.contains("Skolgatan"))
            assertFalse(text.contains(tokenText))
            val host = URI(r.ask.url).host
            when (access) {
                is WayAccess.Own -> {
                    assertFalse(r.ask.viaServer)
                    assertTrue(host != CompanyServer.HOST)
                    assertFalse(r.ask.headers.values.any { it.contains(tokenText) })
                }
                is WayAccess.Company -> {
                    assertTrue(r.ask.viaServer)
                    assertEquals(CompanyServer.HOST, host)
                    assertTrue(CompanyServer.mayCarryToken(r.ask.url))
                    assertEquals("Bearer $tokenText", r.ask.headers["Authorization"])
                    assertEquals(listOf("Authorization"), r.ask.headers.filterValues { it.contains(tokenText) }.keys.toList())
                    assertFalse((r.ask.headers.values.joinToString() + text).contains(googleKey) || (r.ask.headers.values.joinToString() + text).contains(mapmapKey))
                }
                WayAccess.None -> error("none")
            }
            // The text forms show no key, pass or place.
            for (secret in listOf(tokenText, googleKey, mapmapKey, "59.3", "/v1/")) {
                assertFalse(r.toString().contains(secret))
            }
        }
    }

    @Test
    fun theAccessesCompareByValueAndHideTheirKeys() {
        assertEquals(company, WayAccess.Company(DeviceToken("ab12".repeat(16)), google = true, mapmap = true))
        assertEquals(own, WayAccess.Own(googleKey, mapmapKey))
        assertFalse(company == company.copy(mapmap = false))
        for (text in listOf(company.toString(), own.toString())) {
            assertFalse(text, text.contains(tokenText) || text.contains(googleKey) || text.contains(mapmapKey))
        }
    }

    /**
     * The server takes only the app's own requests ([CompanyServer]): these are copied byte for
     * byte from HussamEl/nastastopp-web src/Api.php (ROUTE_FIELDS, MATRIX_FIELDS, ROUTE_KEYS,
     * MATRIX_KEYS, MAPMAP_OPTIONS, MAX_INTERMEDIATES, MAX_POINTS, isPoints). A change on either side
     * needs the other changed with it, or every ask through the server is refused.
     */
    @Test
    fun theRequestsAreTheOnesTheServerTakes() {
        val serverRouteFields = "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline," +
            "routes.legs.duration,routes.legs.distanceMeters,routes.legs.polyline.encodedPolyline"
        val serverMatrixFields = "originIndex,destinationIndex,duration,condition"
        val serverRouteKeys = setOf("origin", "destination", "intermediates", "travelMode", "languageCode", "units")
        val serverMatrixKeys = setOf("origins", "destinations", "travelMode")
        val serverMapmapOptions = setOf("overview", "geometries", "steps")
        val serverMaxIntermediates = 10

        assertEquals(serverRouteFields, RoutesApi.FIELDS)
        assertEquals(serverMatrixFields, RoutesApi.MATRIX_FIELDS)

        val most = List(MapmapApi.MAX_STOPS) { i -> MapWay.Stop(59.0 + i / 100.0, -13.0 - i / 100.0, "Gatan $i") }
        val route = Json.parseToJsonElement(RoutesApi.body(lat, lng, unlocated)!!).jsonObject
        assertTrue(route.keys.toString(), serverRouteKeys.containsAll(route.keys))
        val longest = Json.parseToJsonElement(RoutesApi.body(lat, lng, most)!!).jsonObject
        assertTrue(longest["intermediates"]!!.jsonArray.size <= serverMaxIntermediates)
        for (word in listOf("languageCode", "units")) assertTrue(Regex("[A-Za-z_-]{1,12}").matches(route[word]!!.jsonPrimitive.content))
        assertEquals("DRIVE", route["travelMode"]!!.jsonPrimitive.content)

        val matrix = Json.parseToJsonElement(RoutesApi.matrixBody(lat, lng, unlocated)!!).jsonObject
        assertTrue(matrix.keys.toString(), serverMatrixKeys.containsAll(matrix.keys))

        val mapmap = Json.parseToJsonElement(MapmapApi.serverRouteBody(lat, lng, most)!!).jsonObject
        assertEquals(setOf("points") + serverMapmapOptions, mapmap.keys)
        for (option in serverMapmapOptions) assertTrue(option, Regex("[a-z0-9]{1,12}").matches(mapmap[option]!!.jsonPrimitive.content))
        assertTrue(serverTakesPoints(mapmap["points"]!!.jsonPrimitive.content))
        // The options are mapmap's own, as the direct route asks them.
        assertEquals(MapmapApi.ROUTE_OPTIONS, serverMapmapOptions.joinToString("&") { "$it=${mapmap[it]!!.jsonPrimitive.content}" })
    }

    /** The server's isPoints: 2 to 12 "lng,lat" pairs, each part a number with at most six decimals within its range. */
    private fun serverTakesPoints(points: String): Boolean {
        if (!Regex("[-0-9.,;]{1,400}").matches(points)) return false
        val pairs = points.split(';')
        if (pairs.size !in 2..12) return false
        val degrees = Regex("""-?[0-9]{1,3}(\.[0-9]{1,6})?""")
        return pairs.all { pair ->
            val parts = pair.split(',')
            parts.size == 2 &&
                degrees.matches(parts[0]) && kotlin.math.abs(parts[0].toDouble()) <= 180 &&
                degrees.matches(parts[1]) && kotlin.math.abs(parts[1].toDouble()) <= 90
        }
    }

    @Test
    fun anAskShowsOnlyItsHost() {
        val r = WayRequests.route(company, WaySource.MAPMAP, lat, lng, located)!!
        assertEquals("HttpAsk(api.nastastopp.se)", r.ask.toString())
        assertNotNull(r.toString())
    }
}
