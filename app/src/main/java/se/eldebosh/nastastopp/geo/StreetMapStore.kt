package se.eldebosh.nastastopp.geo

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.eldebosh.nastastopp.BuildConfig
import se.eldebosh.nastastopp.core.geo.StreetMap
import se.eldebosh.nastastopp.core.geo.StreetMapBuilder
import se.eldebosh.nastastopp.util.DebugLog
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * The offline street map: the named roads of Värmland from
 * OpenStreetMap, downloaded once when the driver taps "Download" in Settings, kept on the phone
 * (app-private, no backup, not part of the app itself) and loaded into [CurrentStreet].
 *
 * Privacy: the download asks for fixed tiles covering the whole region, never for the vehicle's
 * position, and nothing is sent but those requests. The data is © OpenStreetMap contributors
 * (ODbL), as the Settings row says.
 */
class StreetMapStore(context: Context, private val street: CurrentStreet, private val scope: CoroutineScope) {

    sealed interface State {
        data object None : State
        data object Loading : State
        data class Downloading(val done: Int, val total: Int) : State
        data class Ready(val roads: Int, val createdAtMs: Long) : State
        data object Failed : State
    }

    private val file = File(context.noBackupFilesDir, "streetmap/varmland.nsm")
    private val _state = MutableStateFlow<State>(State.None)
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    init {
        if (file.exists()) load()
    }

    private fun load() {
        _state.value = State.Loading
        job = scope.launch {
            val map = withContext(Dispatchers.IO) {
                runCatching { DataInputStream(BufferedInputStream(file.inputStream())).use { StreetMap.read(it) } }.getOrNull()
            }
            if (map == null) {
                file.delete() // damaged: the driver can download it again
                _state.value = State.None
            } else {
                street.map = map
                _state.value = State.Ready(map.roadCount, map.createdAtMs)
            }
        }
    }

    /** Downloads (or renews) the map; only after the driver's tap in Settings. */
    fun download() {
        if (job?.isActive == true) return
        val before = _state.value
        job = scope.launch {
            val tiles = OverpassDownload.tiles()
            _state.value = State.Downloading(0, tiles.size)
            val map = withContext(Dispatchers.IO) {
                runCatching {
                    val builder = StreetMapBuilder()
                    tiles.forEachIndexed { i, tile ->
                        ensureActive()
                        OverpassDownload.fetch(tile, builder)
                        _state.value = State.Downloading(i + 1, tiles.size)
                        delay(OverpassDownload.PAUSE_MS) // fair use of the free service
                    }
                    val built = builder.build(System.currentTimeMillis())
                    file.parentFile?.mkdirs()
                    val temp = File(file.path + ".part")
                    DataOutputStream(BufferedOutputStream(temp.outputStream())).use { built.write(it) }
                    if (!temp.renameTo(file)) throw IOException("could not save the street map")
                    built
                }.onFailure { DebugLog.w(it) { "street map download failed" } }.getOrNull()
            }
            if (map != null) {
                street.map = map
                _state.value = State.Ready(map.roadCount, map.createdAtMs)
            } else {
                _state.value = if (before is State.Ready) before else State.Failed
            }
        }
    }

    fun delete() {
        job?.cancel()
        file.delete()
        street.map = null
        _state.value = State.None
    }
}

/**
 * The street map's download from OpenStreetMap's Overpass API: named roads a car can drive on,
 * with their geometry, tile by tile over Värmland (with a margin), so each answer stays small.
 */
object OverpassDownload {
    private val ENDPOINTS = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")
    const val PAUSE_MS = 1_000L
    private const val TRIES = 3

    /** Värmland with a margin (south, west, north, east). */
    private const val SOUTH = 58.70
    private const val WEST = 11.55
    private const val NORTH = 61.10
    private const val EAST = 14.75
    private const val TILE_LAT = 0.3
    private const val TILE_LON = 0.6

    data class Tile(val south: Double, val west: Double, val north: Double, val east: Double)

    fun tiles(): List<Tile> {
        val out = ArrayList<Tile>()
        var s = SOUTH
        while (s < NORTH - 1e-9) {
            var w = WEST
            while (w < EAST - 1e-9) {
                out += Tile(s, w, minOf(s + TILE_LAT, NORTH), minOf(w + TILE_LON, EAST))
                w += TILE_LON
            }
            s += TILE_LAT
        }
        return out
    }

    /** Roads for vehicles only (no footways or cycle paths), and only those with a name. */
    fun query(t: Tile): String =
        "[out:json][timeout:180];" +
            "way[\"highway\"~\"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|road|" +
            "motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$\"][\"name\"]" +
            "(${t.south},${t.west},${t.north},${t.east});out geom qt;"

    /** Downloads one tile into [builder], trying again (and the second server) when busy. */
    fun fetch(tile: Tile, builder: StreetMapBuilder) {
        var last: IOException? = null
        for (attempt in 0 until TRIES) {
            val endpoint = ENDPOINTS[if (attempt < TRIES - 1) 0 else 1]
            try {
                val conn = URI(endpoint).toURL().openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 200_000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    conn.setRequestProperty("User-Agent", "NastaStopp/${BuildConfig.VERSION_NAME} (Android; offline street names for a driver)")
                    conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query(tile), "UTF-8")).toByteArray()) }
                    val code = conn.responseCode
                    if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
                    InputStreamReader(conn.inputStream, Charsets.UTF_8).use { parse(it, builder) }
                    return
                } finally {
                    conn.disconnect()
                }
            } catch (e: IOException) {
                last = e
                Thread.sleep(5_000L * (attempt + 1)) // busy server: wait a little longer each time
            }
        }
        throw last ?: IOException("street map tile failed")
    }

    /**
     * Reads an Overpass JSON answer ({"elements":[{"type":"way","id":…,"geometry":[{"lat","lon"}…],
     * "tags":{"name":…}}…]}) as a stream, road by road. An answer cut short by the server (a
     * "remark" with an error) is refused, so a map is never saved with holes in it.
     */
    fun parse(input: Reader, builder: StreetMapBuilder) {
        JsonReader(input).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "elements" -> {
                        r.beginArray()
                        while (r.hasNext()) readElement(r, builder)
                        r.endArray()
                    }
                    "remark" -> {
                        val remark = r.nextString()
                        if (remark.contains("error", ignoreCase = true)) throw IOException("server stopped early")
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }
    }

    private fun readElement(r: JsonReader, builder: StreetMapBuilder) {
        var type: String? = null
        var id = 0L
        var name: String? = null
        val points = ArrayList<Pair<Double, Double>>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "type" -> type = r.nextString()
                "id" -> id = r.nextLong()
                "geometry" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        if (r.peek() == JsonToken.NULL) {
                            r.nextNull()
                            continue
                        }
                        var la = Double.NaN
                        var lo = Double.NaN
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "lat" -> la = r.nextDouble()
                                "lon" -> lo = r.nextDouble()
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                        if (!la.isNaN() && !lo.isNaN()) points += la to lo
                    }
                    r.endArray()
                }
                "tags" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        if (r.nextName() == "name") name = r.nextString() else r.skipValue()
                    }
                    r.endObject()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        if (type == "way") builder.add(id, name, points)
    }
}
