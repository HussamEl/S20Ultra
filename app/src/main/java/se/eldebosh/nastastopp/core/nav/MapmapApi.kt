package se.eldebosh.nastastopp.core.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.Locale

/**
 * Who gives the tablet's map its ways and travel times, chosen by the driver on the tablet (304);
 * asked through the company's server while the tablet is connected to it ([CompanyServer]), else
 * with the driver's own keys ([WayRequests]).
 */
enum class WaySource {
    /** Google's Routes API ([RoutesApi]): the company's key through its server, or the driver's own map key. */
    GOOGLE,

    /** mapmap.ai (OpenStreetMap's roads, [MapmapApi]): the company's key through its server, or the driver's own mapmap key. */
    MAPMAP,
}

/**
 * mapmap.ai, when the driver chose it for the ways (304): through the company's server with the
 * company's key while the tablet is connected to it, else with the driver's own key. It gives the
 * way from the vehicle through a few stops (its OSRM-style route, by points only), and the travel
 * times between them for an order to suggest (its Valhalla-style matrix). The same answers as
 * [RoutesApi]'s ([RouteLine], seconds), so the map and [OrderPlanner] work with either. Only the
 * points are sent, never an address or a name; a stop known only by its address is left to Google.
 */
object MapmapApi {
    const val BASE = "https://api.mapmap.ai"

    /** Stops on one way, as with Google. */
    const val MAX_STOPS = RoutesApi.MAX_VIA + 1

    /** A key as mapmap issues them ("snk_…"): letters, digits, "-" and "_". Anything else is refused. */
    fun isKey(key: String?): Boolean = key != null && KEY.matches(key)

    /** Whether every stop of [stops] has its point: mapmap is asked only by points. */
    fun canAsk(stops: List<MapWay.Stop>): Boolean = stops.isNotEmpty() && stops.all { it.lat != null && it.lng != null }

    /**
     * The vehicle's point, then each stop's, as mapmap takes them: "longitude,latitude" with six
     * decimals, joined by ";". Null when a stop has no point, a point is not a number, or there
     * are more than [MAX_STOPS] stops.
     */
    fun points(fromLat: Double, fromLng: Double, stops: List<MapWay.Stop>): String? {
        if (!canAsk(stops) || stops.size > MAX_STOPS) return null
        val points = listOf(fromLat to fromLng) + stops.map { it.lat!! to it.lng!! }
        if (points.any { (lat, lng) -> !lat.isFinite() || !lng.isFinite() }) return null
        return points.joinToString(";") { (lat, lng) -> String.format(Locale.ROOT, "%.6f,%.6f", lng, lat) }
    }

    /** What the route answers with: the whole line, as Google's polyline, and each leg's steps. */
    const val ROUTE_OPTIONS = "overview=full&geometries=polyline&steps=true"

    /**
     * The way from the vehicle through [stops] in turn, each leg with its own line (the steps'
     * lines, so each stop's leg can be drawn in its colour); null when a stop has no point.
     */
    fun routeUrl(fromLat: Double, fromLng: Double, stops: List<MapWay.Stop>): String? =
        points(fromLat, fromLng, stops)?.let { "$BASE/route/v1/driving/$it?$ROUTE_OPTIONS" }

    /**
     * The same way asked through the company's server ([CompanyServer.MAPMAP_ROUTE]): a POST of the
     * points and [ROUTE_OPTIONS], so no point is ever in an address (or a web server's log).
     */
    fun serverRouteBody(fromLat: Double, fromLng: Double, stops: List<MapWay.Stop>): String? {
        val points = points(fromLat, fromLng, stops) ?: return null
        return buildJsonObject {
            put("points", points)
            ROUTE_OPTIONS.split('&').forEach { option ->
                val (name, value) = option.split('=', limit = 2)
                put(name, value)
            }
        }.toString()
    }

    const val MATRIX_URL = "$BASE/matrix"

    /** The travel times asked for: from the vehicle and from each stop, to each stop; null when a stop has no point. */
    fun matrixBody(fromLat: Double, fromLng: Double, stops: List<MapWay.Stop>): String? {
        if (!canAsk(stops)) return null
        return buildJsonObject {
            putJsonArray("sources") {
                addJsonObject { put("lat", fromLat); put("lon", fromLng) }
                stops.forEach { s -> addJsonObject { put("lat", s.lat!!); put("lon", s.lng!!) } }
            }
            putJsonArray("targets") { stops.forEach { s -> addJsonObject { put("lat", s.lat!!); put("lon", s.lng!!) } } }
            put("costing", "auto")
        }.toString()
    }

    /** The first route of an answer, with a leg to each stop, or null when it has none. */
    fun parse(text: String): RouteLine? = runCatching {
        val answer = json.parseToJsonElement(text).jsonObject
        if (answer["code"]?.jsonPrimitive?.contentOrNull.let { it != null && it != "Ok" }) return null
        val route = answer["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val seconds = route["duration"]?.jsonPrimitive?.doubleOrNull ?: return null
        val meters = route["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val encoded = route["geometry"]?.jsonPrimitive?.contentOrNull ?: return null
        val legs = route["legs"]?.jsonArray?.map { element ->
            val leg = element.jsonObject
            val path = leg["steps"]?.jsonArray.orEmpty().flatMap { step ->
                step.jsonObject["geometry"]?.jsonPrimitive?.contentOrNull?.let(RoutesApi::decode).orEmpty()
            }
            RouteLine.Leg(
                seconds = leg["duration"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0,
                meters = leg["distance"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0,
                path = path.distinctNeighbours(),
            )
        }.orEmpty()
        RouteLine(minutes = (seconds.toInt() + 30) / 60, meters = meters.toInt(), path = RoutesApi.decode(encoded), legs = legs)
    }.getOrNull()

    /**
     * The travel times of a matrix answer, in seconds: [from][to], from the vehicle (0) or stop i
     * (i + 1), to stop j; [RoutesApi.NO_WAY] where none was found. Read from either of the
     * matrix's forms (a list of cells, or rows of durations). Null when the answer is not one.
     */
    fun parseMatrix(text: String, stops: Int): Array<IntArray>? = runCatching {
        val cells = json.parseToJsonElement(text).jsonObject["sources_to_targets"] ?: return null
        val out = Array(stops + 1) { IntArray(stops) { RoutesApi.NO_WAY } }
        var found = 0
        fun put(from: Int, to: Int, time: JsonElement?) {
            if (from !in 0..stops || to !in 0 until stops) return
            // No way between them: null.
            val seconds = (time as? JsonPrimitive)?.doubleOrNull ?: return
            out[from][to] = seconds.toInt()
            found++
        }
        when (cells) {
            is JsonArray -> cells.forEachIndexed { from, row ->
                row.jsonArray.forEachIndexed { to, cell ->
                    val c = cell.jsonObject
                    put(c["from_index"]?.jsonPrimitive?.intOrNull ?: from, c["to_index"]?.jsonPrimitive?.intOrNull ?: to, c["time"])
                }
            }
            is JsonObject -> cells["durations"]?.jsonArray?.forEachIndexed { from, row ->
                row.jsonArray.forEachIndexed { to, time -> put(from, to, time) }
            }
            else -> return null
        }
        if (found == 0) null else out
    }.getOrNull()

    /** The steps' lines meet at their ends: each meeting point once. */
    private fun List<Pair<Double, Double>>.distinctNeighbours(): List<Pair<Double, Double>> =
        filterIndexed { i, p -> i == 0 || p != this[i - 1] }

    private val json = Json { ignoreUnknownKeys = true }
    private val KEY = Regex("""[A-Za-z0-9_-]{16,128}""")
}
