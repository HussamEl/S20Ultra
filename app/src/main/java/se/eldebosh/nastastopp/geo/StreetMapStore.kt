package se.eldebosh.nastastopp.geo

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.CancellationException
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
import se.eldebosh.nastastopp.core.geo.RoadSink
import se.eldebosh.nastastopp.core.geo.StreetMap
import se.eldebosh.nastastopp.core.geo.StreetMapBuilder
import se.eldebosh.nastastopp.core.geo.StreetMapPart
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
import java.util.Locale

/**
 * The offline street map: the named roads of Värmland from OpenStreetMap, downloaded once when the
 * driver taps "Download" in Settings, kept on the phone (app-private, no backup, not part of the
 * app itself) and loaded into [CurrentStreet].
 *
 * The download goes tile by tile. Each finished tile is kept in `parts/` until the whole map is
 * built, so a download that stops (a busy server, no signal, the app closed) continues from the
 * tile where it stopped; a busy server is waited out ([OverpassDownload.fetch]).
 *
 * Privacy: the download asks for fixed tiles covering the whole region, never for the vehicle's
 * position, and nothing is sent but those requests. The data is © OpenStreetMap contributors
 * (ODbL), as the Settings row says.
 */
class StreetMapStore(
    context: Context,
    private val street: CurrentStreet,
    private val scope: CoroutineScope,
    private val source: TileSource = OverpassDownload,
    private val pauseMs: Long = OverpassDownload.PAUSE_MS,
) {

    sealed interface State {
        data object None : State
        data object Loading : State

        /** [done] of [total] tiles are in; [busy] while waiting for a busy server. */
        data class Downloading(val done: Int, val total: Int, val busy: Boolean = false) : State
        data class Ready(val roads: Int, val createdAtMs: Long) : State

        /** Stopped after [done] of [total] tiles; the next download continues from there. */
        data class Failed(val done: Int, val total: Int) : State
    }

    /** Where the map's tiles come from (the Overpass API; a stand-in in tests). */
    interface TileSource {
        fun tiles(): List<OverpassDownload.Tile>

        /** Reads one tile's roads into [into]; [onBusy] tells when it waits for a busy server. */
        suspend fun fetch(tile: OverpassDownload.Tile, into: RoadSink, onBusy: (Boolean) -> Unit)
    }

    private val dir = File(context.noBackupFilesDir, "streetmap")
    private val file = File(dir, "varmland.nsm")
    private val partsDir = File(dir, "parts")
    private val _state = MutableStateFlow<State>(State.None)
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    init {
        when {
            file.exists() -> load()
            partsDone() > 0 -> _state.value = State.Failed(partsDone(), source.tiles().size)
        }
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

    /**
     * Downloads (or renews) the map; only after the driver's tap in Settings. Tiles kept from a
     * download that stopped are not asked for again.
     */
    fun download() {
        if (job?.isActive == true) return
        val before = _state.value
        job = scope.launch {
            val tiles = source.tiles()
            _state.value = State.Downloading(0, tiles.size)
            val map = withContext(Dispatchers.IO) {
                try {
                    dropOldParts()
                    partsDir.mkdirs()
                    val builder = StreetMapBuilder()
                    tiles.forEachIndexed { i, tile ->
                        ensureActive()
                        val partFile = File(partsDir, partName(tile))
                        val part = readPart(partFile) ?: StreetMapPart().also { p ->
                            source.fetch(tile, p) { busy -> _state.value = State.Downloading(i, tiles.size, busy) }
                            writeAtomically(partFile) { p.write(it) }
                            delay(pauseMs) // fair use of the free service
                        }
                        part.replayInto(builder)
                        _state.value = State.Downloading(i + 1, tiles.size)
                    }
                    val built = builder.build(System.currentTimeMillis())
                    writeAtomically(file) { built.write(it) }
                    partsDir.deleteRecursively()
                    built
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLog.w(e) { "street map download stopped" }
                    null
                }
            }
            if (map != null) {
                street.map = map
                _state.value = State.Ready(map.roadCount, map.createdAtMs)
            } else {
                _state.value = if (before is State.Ready) before else State.Failed(partsDone(), tiles.size)
            }
        }
    }

    fun delete() {
        job?.cancel()
        file.delete()
        partsDir.deleteRecursively()
        street.map = null
        _state.value = State.None
    }

    /** How many tiles of the current layout are already kept on disk. */
    private fun partsDone(): Int = source.tiles().count { File(partsDir, partName(it)).exists() }

    /** Tiles kept from a download that stopped long ago are asked for again (the roads change). */
    private fun dropOldParts() {
        val oldest = System.currentTimeMillis() - MAX_PART_AGE_MS
        partsDir.listFiles()?.forEach { if (it.lastModified() < oldest) it.delete() }
    }

    private fun readPart(f: File): StreetMapPart? {
        if (!f.exists()) return null
        return runCatching { DataInputStream(BufferedInputStream(f.inputStream())).use { StreetMapPart.read(it) } }
            .onFailure { f.delete() } // damaged: asked for again
            .getOrNull()
    }

    /** Writes through a temporary file, so a half-written file is never taken for a finished one. */
    private fun writeAtomically(target: File, write: (DataOutputStream) -> Unit) {
        target.parentFile?.mkdirs()
        val temp = File(target.path + ".tmp")
        DataOutputStream(BufferedOutputStream(temp.outputStream())).use(write)
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("could not save ${target.name}")
        }
    }

    private fun partName(t: OverpassDownload.Tile) =
        String.format(Locale.ROOT, "%.2f_%.2f_%.2f_%.2f.part", t.south, t.west, t.north, t.east)

    private companion object {
        const val MAX_PART_AGE_MS = 14L * 24 * 60 * 60 * 1000
    }
}

/**
 * The street map's download from OpenStreetMap's Overpass API: named roads a car can drive on,
 * with their geometry, tile by tile over Värmland (with a margin), so each answer stays small.
 */
object OverpassDownload : StreetMapStore.TileSource {
    private val ENDPOINTS = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")
    const val PAUSE_MS = 1_000L

    /**
     * Pauses before each new try of a tile. The public servers answer 429 / 504 in bursts when
     * busy, so a tile is tried 8 times over about 5 minutes, alternating the two servers.
     */
    val RETRY_DELAYS_MS = longArrayOf(10_000, 20_000, 30_000, 45_000, 60_000, 60_000, 60_000)

    /** Värmland with a margin (south, west, north, east). */
    private const val SOUTH = 58.70
    private const val WEST = 11.55
    private const val NORTH = 61.10
    private const val EAST = 14.75
    private const val TILE_LAT = 0.3
    private const val TILE_LON = 0.6

    data class Tile(val south: Double, val west: Double, val north: Double, val east: Double)

    override fun tiles(): List<Tile> {
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

    /**
     * Downloads one tile into [into]. A busy or failing server is tried again after the pauses of
     * [RETRY_DELAYS_MS]; [into] only gets a complete answer.
     */
    override suspend fun fetch(tile: Tile, into: RoadSink, onBusy: (Boolean) -> Unit) {
        withRetries(RETRY_DELAYS_MS, onBusy) { attempt ->
            val part = StreetMapPart()
            request(ENDPOINTS[attempt % ENDPOINTS.size], tile, part)
            part.replayInto(into)
        }
    }

    /**
     * Runs [block] (given the attempt number) until it succeeds, pausing [delaysMs] between tries
     * and telling [onBusy] while it waits. The last failure is thrown when every try failed.
     */
    suspend fun withRetries(delaysMs: LongArray, onBusy: (Boolean) -> Unit, block: suspend (Int) -> Unit) {
        var attempt = 0
        while (true) {
            try {
                block(attempt)
                if (attempt > 0) onBusy(false)
                return
            } catch (e: IOException) {
                if (attempt >= delaysMs.size) throw e
                onBusy(true)
                delay(delaysMs[attempt])
                attempt++
            }
        }
    }

    private fun request(endpoint: String, tile: Tile, into: RoadSink) {
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
            InputStreamReader(conn.inputStream, Charsets.UTF_8).use { parse(it, into) }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Reads an Overpass JSON answer ({"elements":[{"type":"way","id":…,"geometry":[{"lat","lon"}…],
     * "tags":{"name":…}}…]}) as a stream, road by road. An answer cut short by the server (a
     * "remark" with an error) is refused, so a map is never saved with holes in it.
     */
    fun parse(input: Reader, into: RoadSink) {
        JsonReader(input).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "elements" -> {
                        r.beginArray()
                        while (r.hasNext()) readElement(r, into)
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

    private fun readElement(r: JsonReader, into: RoadSink) {
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
        if (type == "way") into.add(id, name, points)
    }
}
