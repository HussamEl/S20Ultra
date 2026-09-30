package se.eldebosh.nastastopp.core.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** The way to the next stop for the tablet's map: minutes, metres and the line to draw. */
data class RouteLine(val minutes: Int, val meters: Int, val path: List<Pair<Double, Double>>)

/**
 * Google's Routes API (computeRoutes), asked by the tablet with the driver's own key for the way
 * from the vehicle to the next stop. Only the two places are sent, never a name.
 */
object RoutesApi {
    const val URL = "https://routes.googleapis.com/directions/v2:computeRoutes"

    /** Only what the map needs, so the answer stays small (and the request in the basic tier). */
    const val FIELDS = "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline"

    /** A key as Google issues them: letters, digits, "-" and "_". Anything else is refused. */
    fun isKey(key: String?): Boolean = key != null && KEY.matches(key)

    /** The request: from the vehicle to the stop's point, or to its address when it has none. */
    fun body(fromLat: Double, fromLng: Double, toLat: Double?, toLng: Double?, toAddress: String?): String? {
        if (toLat == null || toLng == null) {
            if (toAddress.isNullOrBlank()) return null
        }
        return buildJsonObject {
            putJsonObject("origin") { putJsonObject("location") { putJsonObject("latLng") { put("latitude", fromLat); put("longitude", fromLng) } } }
            putJsonObject("destination") {
                if (toLat != null && toLng != null) {
                    putJsonObject("location") { putJsonObject("latLng") { put("latitude", toLat); put("longitude", toLng) } }
                } else {
                    put("address", toAddress)
                }
            }
            put("travelMode", "DRIVE")
            put("languageCode", "sv-SE")
            put("units", "METRIC")
        }.toString()
    }

    /** The first route of an answer, or null when it has none. */
    fun parse(text: String): RouteLine? = runCatching {
        val route = json.parseToJsonElement(text).jsonObject["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val seconds = route["duration"]?.jsonPrimitive?.contentOrNull?.removeSuffix("s")?.toDoubleOrNull() ?: return null
        val meters = route["distanceMeters"]?.jsonPrimitive?.intOrNull ?: 0
        val encoded = (route["polyline"] as? JsonObject)?.get("encodedPolyline")?.jsonPrimitive?.contentOrNull ?: return null
        RouteLine(minutes = ((seconds + 30) / 60).toInt(), meters = meters, path = decode(encoded))
    }.getOrNull()

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
