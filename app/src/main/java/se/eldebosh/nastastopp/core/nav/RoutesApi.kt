package se.eldebosh.nastastopp.core.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The way for the tablet's map: minutes, metres and the line to draw, from the vehicle through
 * each stop in turn ([legs]: one per stop, to it from the one before).
 */
data class RouteLine(val minutes: Int, val meters: Int, val path: List<Pair<Double, Double>>, val legs: List<Leg> = emptyList()) {
    /** The way to one stop from the place before it. */
    data class Leg(val seconds: Int, val meters: Int, val path: List<Pair<Double, Double>>)

    /** Minutes and metres from the vehicle to the stop [at] (its leg and all before it). */
    fun to(at: Int): Pair<Int, Int> {
        if (legs.isEmpty() || at !in legs.indices) return minutes to meters
        val upTo = legs.take(at + 1)
        return (upTo.sumOf { it.seconds } + 30) / 60 to upTo.sumOf { it.meters }
    }
}

/**
 * Google's Routes API, asked by the tablet with the driver's own key: the way from the vehicle
 * through a few stops (computeRoutes), and the travel times between them all for an order to
 * suggest (computeRouteMatrix). Only the places are sent, never a name.
 */
object RoutesApi {
    const val URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
    const val MATRIX_URL = "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix"

    /** Only what the map needs, so the answer stays small (and the request in the basic tier). */
    const val FIELDS = "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline," +
        "routes.legs.duration,routes.legs.distanceMeters,routes.legs.polyline.encodedPolyline"

    const val MATRIX_FIELDS = "originIndex,destinationIndex,duration,condition"

    /** Stops after the first one on a way (Google's basic tier allows ten). */
    const val MAX_VIA = 10

    /** A matrix with a place given by its address may hold this many answers at most. */
    const val MAX_ADDRESS_ELEMENTS = 50

    /** A key as Google issues them: letters, digits, "-" and "_". Anything else is refused. */
    fun isKey(key: String?): Boolean = key != null && KEY.matches(key)

    /**
     * The request: from the vehicle through [stops] in turn, each by its point, or by its address
     * when it has none; null when a stop has neither, or there is none.
     */
    fun body(fromLat: Double, fromLng: Double, stops: List<MapWay.Stop>): String? {
        if (stops.isEmpty() || stops.size > MAX_VIA + 1 || stops.any { !it.routable }) return null
        return buildJsonObject {
            putJsonObject("origin") { point(fromLat, fromLng) }
            putJsonObject("destination") { place(stops.last()) }
            if (stops.size > 1) putJsonArray("intermediates") { stops.dropLast(1).forEach { s -> addJsonObject { place(s) } } }
            put("travelMode", "DRIVE")
            put("languageCode", "sv-SE")
            put("units", "METRIC")
        }.toString()
    }

    /** The travel times asked for: from the vehicle and from each stop, to each stop; null when it cannot be asked. */
    fun matrixBody(fromLat: Double, fromLng: Double, stops: List<MapWay.Stop>): String? {
        if (stops.isEmpty() || stops.any { !it.routable }) return null
        if (stops.any { it.lat == null || it.lng == null } && (stops.size + 1) * stops.size > MAX_ADDRESS_ELEMENTS) return null
        return buildJsonObject {
            putJsonArray("origins") {
                addJsonObject { putJsonObject("waypoint") { point(fromLat, fromLng) } }
                stops.forEach { s -> addJsonObject { putJsonObject("waypoint") { place(s) } } }
            }
            putJsonArray("destinations") { stops.forEach { s -> addJsonObject { putJsonObject("waypoint") { place(s) } } } }
            put("travelMode", "DRIVE")
        }.toString()
    }

    private fun JsonObjectBuilder.point(lat: Double, lng: Double) {
        putJsonObject("location") { putJsonObject("latLng") { put("latitude", lat); put("longitude", lng) } }
    }

    private fun JsonObjectBuilder.place(s: MapWay.Stop) {
        if (s.lat != null && s.lng != null) point(s.lat, s.lng) else put("address", s.address)
    }

    /** The first route of an answer, with its legs, or null when it has none. */
    fun parse(text: String): RouteLine? = runCatching {
        val route = json.parseToJsonElement(text).jsonObject["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val seconds = seconds(route["duration"]) ?: return null
        val meters = route["distanceMeters"]?.jsonPrimitive?.intOrNull ?: 0
        val encoded = (route["polyline"] as? JsonObject)?.get("encodedPolyline")?.jsonPrimitive?.contentOrNull ?: return null
        val legs = route["legs"]?.jsonArray?.map { element ->
            val leg = element.jsonObject
            val line = (leg["polyline"] as? JsonObject)?.get("encodedPolyline")?.jsonPrimitive?.contentOrNull
            RouteLine.Leg(seconds(leg["duration"]) ?: 0, leg["distanceMeters"]?.jsonPrimitive?.intOrNull ?: 0, line?.let(::decode).orEmpty())
        }.orEmpty()
        RouteLine(minutes = (seconds + 30) / 60, meters = meters, path = decode(encoded), legs = legs)
    }.getOrNull()

    /**
     * The travel times of a matrix answer, in seconds: [from][to], from the vehicle (0) or stop i
     * (i + 1), to stop j; [NO_WAY] where Google found none. Null when the answer is not one.
     */
    fun parseMatrix(text: String, stops: Int): Array<IntArray>? = runCatching {
        val answers = json.parseToJsonElement(text).jsonArray
        val out = Array(stops + 1) { IntArray(stops) { NO_WAY } }
        var found = 0
        for (element in answers) {
            val a = element.jsonObject
            // Google leaves out an index of 0.
            val from = a["originIndex"]?.jsonPrimitive?.intOrNull ?: 0
            val to = a["destinationIndex"]?.jsonPrimitive?.intOrNull ?: 0
            val condition = a["condition"]?.jsonPrimitive?.contentOrNull
            if (from !in 0..stops || to !in 0 until stops) continue
            if (condition != null && condition != "ROUTE_EXISTS") continue
            out[from][to] = seconds(a["duration"]) ?: continue
            found++
        }
        if (found == 0) null else out
    }.getOrNull()

    /** No way between two places in a matrix. */
    const val NO_WAY = Int.MAX_VALUE

    /** "731s" → 731. */
    private fun seconds(element: kotlinx.serialization.json.JsonElement?): Int? =
        element?.jsonPrimitive?.contentOrNull?.removeSuffix("s")?.toDoubleOrNull()?.toInt()

    /** Google's encoded polyline as latitude / longitude pairs. */
    fun decode(encoded: String): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        var i = 0
        var lat = 0
        var lng = 0
        fun next(): Int {
            var result = 0
            var shift = 0
            while (i < encoded.length) {
                val b = encoded[i++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
                if (b < 0x20) break
            }
            return if (result and 1 != 0) (result shr 1).inv() else result shr 1
        }
        while (i < encoded.length) {
            lat += next()
            lng += next()
            out += lat / 1e5 to lng / 1e5
        }
        return out
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val KEY = Regex("""[A-Za-z0-9_-]{20,80}""")
}
