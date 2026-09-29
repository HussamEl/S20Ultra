package se.eldebosh.nastastopp.core.geo

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The named roads of the driver's region (from OpenStreetMap, downloaded once on the phone), to
 * tell exactly which road the vehicle is on. Each road is a line of points in millionths of
 * a degree. A grid of ~110 m cells over the road segments finds the roads near a position fast.
 */
class StreetMap(
    /** Every road name once. */
    val names: List<String>,
    /** Per road: the index of its name in [names]. */
    val roadName: IntArray,
    /** Per road: where its points start in [lat] / [lon]; one extra entry at the end. */
    val roadStart: IntArray,
    val lat: IntArray,
    val lon: IntArray,
    val createdAtMs: Long,
) {
    val roadCount: Int get() = roadName.size

    // The grid: one Long per (cell, segment) pair, the cell's number in the high half and the
    // segment (the index of its first point) in the low half, sorted, so a cell's segments are a
    // run found by binary search. Primitive arrays only: a region has about a million pairs.
    private val minRow: Int
    private val minCol: Int
    private val cols: Int
    private val rows: Int
    private val grid: LongArray

    init {
        require(roadStart.size == roadName.size + 1 && lat.size == lon.size && roadStart.last() == lat.size)
        var r0 = Int.MAX_VALUE; var r1 = Int.MIN_VALUE; var c0 = Int.MAX_VALUE; var c1 = Int.MIN_VALUE
        for (i in lat.indices) {
            r0 = min(r0, row(lat[i])); r1 = max(r1, row(lat[i]))
            c0 = min(c0, col(lon[i])); c1 = max(c1, col(lon[i]))
        }
        minRow = if (lat.isEmpty()) 0 else r0
        minCol = if (lat.isEmpty()) 0 else c0
        rows = if (lat.isEmpty()) 0 else r1 - r0 + 1
        cols = if (lat.isEmpty()) 0 else c1 - c0 + 1
        var count = 0
        forEachSegmentCell { _, _ -> count++ }
        val pairs = LongArray(count)
        var n = 0
        forEachSegmentCell { cell, seg -> pairs[n++] = (cell.toLong() shl 32) or seg.toLong() }
        pairs.sort()
        grid = pairs
    }

    private inline fun forEachSegmentCell(action: (cell: Int, seg: Int) -> Unit) {
        for (r in 0 until roadCount) {
            for (p in roadStart[r] until roadStart[r + 1] - 1) {
                val ra = row(lat[p]); val rb = row(lat[p + 1])
                val ca = col(lon[p]); val cb = col(lon[p + 1])
                for (row in min(ra, rb)..max(ra, rb)) for (col in min(ca, cb)..max(ca, cb)) {
                    action((row - minRow) * cols + (col - minCol), p)
                }
            }
        }
    }

    /** The road each point belongs to (points are stored road after road). */
    private fun roadOf(point: Int): Int {
        var lo = 0
        var hi = roadCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (roadStart[mid] <= point) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** A road segment near a position: its road's name, how far it is and its direction. */
    data class Near(val name: String, val distanceM: Double, val bearingDeg: Double)

    /** The nearest segment of each road within [radiusM] of the position (one entry per name). */
    fun near(latDeg: Double, lonDeg: Double, radiusM: Double): List<Near> {
        val la = (latDeg * E6).toInt()
        val lo = (lonDeg * E6).toInt()
        val kx = cos(Math.toRadians(latDeg)) * M_PER_DEG_LAT / E6 // metres per E6 unit of longitude
        val ky = M_PER_DEG_LAT / E6
        val best = HashMap<Int, Near>()
        val reach = (radiusM / CELL_M).toInt() + 1
        val rc = row(la) - minRow
        val cc = col(lo) - minCol
        for (row in rc - reach..rc + reach) {
            if (row !in 0 until rows) continue
            for (col in cc - reach..cc + reach) {
                if (col !in 0 until cols) continue
                val cell = row * cols + col
                var i = firstIndex(cell.toLong() shl 32)
                while (i < grid.size && (grid[i] ushr 32).toInt() == cell) {
                    val p = (grid[i++] and 0xFFFFFFFFL).toInt()
                    val ax = (lon[p] - lo) * kx; val ay = (lat[p] - la) * ky
                    val bx = (lon[p + 1] - lo) * kx; val by = (lat[p + 1] - la) * ky
                    val d = distanceToSegment(ax, ay, bx, by)
                    if (d > radiusM) continue
                    val name = roadName[roadOf(p)]
                    val prev = best[name]
                    if (prev == null || d < prev.distanceM) {
                        best[name] = Near(names[name], d, Math.toDegrees(atan2(bx - ax, by - ay)).let { if (it < 0) it + 360 else it })
                    }
                }
            }
        }
        return best.values.sortedBy { it.distanceM }
    }

    private fun firstIndex(k: Long): Int {
        var lo = 0
        var hi = grid.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (grid[mid] < k) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Writes the map (a small binary file: names, then each road's points). */
    fun write(out: DataOutputStream) {
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeLong(createdAtMs)
        out.writeInt(names.size)
        names.forEach { out.writeUTF(it) }
        out.writeInt(roadCount)
        out.writeInt(lat.size)
        for (r in 0 until roadCount) {
            out.writeInt(roadName[r])
            out.writeInt(roadStart[r + 1] - roadStart[r])
            for (p in roadStart[r] until roadStart[r + 1]) {
                out.writeInt(lat[p])
                out.writeInt(lon[p])
            }
        }
    }

    companion object {
        const val E6 = 1_000_000.0
        private const val M_PER_DEG_LAT = 111_195.0
        private const val MAGIC = 0x4E534D31 // "NSM1"
        private const val VERSION = 1

        /** The grid: 0.001° of latitude (~111 m) by 0.002° of longitude (~113 m at 59° N). */
        private const val CELL_LAT = 1_000
        private const val CELL_LON = 2_000
        private const val CELL_M = 110.0

        private fun row(latE6: Int) = floor(latE6.toDouble() / CELL_LAT).toInt()
        private fun col(lonE6: Int) = floor(lonE6.toDouble() / CELL_LON).toInt()

        /** Distance from the origin to the segment A–B (metres, local flat coordinates). */
        fun distanceToSegment(ax: Double, ay: Double, bx: Double, by: Double): Double {
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            return hypot(ax + t * dx, ay + t * dy)
        }

        /** Reads a map written by [write]; throws [IOException] for a file that is not one. */
        fun read(input: DataInputStream): StreetMap {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) throw IOException("not a street map")
            val created = input.readLong()
            val names = List(input.readInt()) { input.readUTF() }
            val roads = input.readInt()
            val points = input.readInt()
            val roadName = IntArray(roads)
            val roadStart = IntArray(roads + 1)
            val lats = IntArray(points)
            val lons = IntArray(points)
            var p = 0
            for (r in 0 until roads) {
                roadName[r] = input.readInt()
                roadStart[r] = p
                repeat(input.readInt()) {
                    lats[p] = input.readInt()
                    lons[p] = input.readInt()
                    p++
                }
            }
            roadStart[roads] = p
            if (p != points) throw IOException("damaged street map")
            return StreetMap(names, roadName, roadStart, lats, lons, created)
        }

        /** The smaller angle between a heading and a road's direction; roads go both ways (0–90°). */
        fun angleToRoad(headingDeg: Double, roadBearingDeg: Double): Double {
            val d = abs(((headingDeg - roadBearingDeg) % 180 + 180) % 180)
            return min(d, 180 - d)
        }
    }
}

/**
 * Collects roads while the map is downloaded, then makes the [StreetMap]: one entry per road, names
 * kept once, a road seen in two download tiles kept once.
 */
class StreetMapBuilder {
    private val nameIndex = HashMap<String, Int>()
    private val names = ArrayList<String>()
    private val seen = HashSet<Long>()
    private val roadName = IntList()
    private val roadStart = IntList().apply { add(0) }
    private val lats = IntList()
    private val lons = IntList()

    val roadCount: Int get() = roadName.size

    /** Adds a road ([id] from the map data), unless it has no name, fewer than 2 points or was added. */
    fun add(id: Long, name: String?, points: List<Pair<Double, Double>>) {
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (points.size < 2 || !seen.add(id)) return
        roadName.add(nameIndex.getOrPut(n) { names += n; names.size - 1 })
        points.forEach { (la, lo) ->
            lats.add(Math.round(la * StreetMap.E6).toInt())
            lons.add(Math.round(lo * StreetMap.E6).toInt())
        }
        roadStart.add(lats.size)
    }

    fun build(createdAtMs: Long): StreetMap =
        StreetMap(names.toList(), roadName.toArray(), roadStart.toArray(), lats.toArray(), lons.toArray(), createdAtMs)

    /** A growable IntArray (no boxing for the hundreds of thousands of points). */
    private class IntList {
        private var data = IntArray(1024)
        var size = 0
            private set

        fun add(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }

        fun toArray(): IntArray = data.copyOf(size)
    }
}
