package se.eldebosh.nastastopp.core.geo

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/**
 * The named roads of one download tile. Each finished tile is kept on disk until the whole map is
 * built, so a download that stops part-way (a busy server, no signal) continues from the tile
 * where it stopped instead of starting again.
 */
class StreetMapPart : RoadSink {
    private class Road(val id: Long, val name: String, val lat: IntArray, val lon: IntArray)

    private val roads = ArrayList<Road>()

    val roadCount: Int get() = roads.size

    override fun add(id: Long, name: String?, points: List<Pair<Double, Double>>) {
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (points.size < 2) return
        roads += Road(
            id,
            n,
            IntArray(points.size) { (points[it].first * StreetMap.E6).roundToInt() },
            IntArray(points.size) { (points[it].second * StreetMap.E6).roundToInt() },
        )
    }

    /** Hands every road to [sink] (the map being built). */
    fun replayInto(sink: RoadSink) {
        roads.forEach { r ->
            sink.add(r.id, r.name, List(r.lat.size) { r.lat[it] / StreetMap.E6 to r.lon[it] / StreetMap.E6 })
        }
    }

    fun write(out: DataOutputStream) {
        out.writeInt(MAGIC)
        out.writeInt(roads.size)
        roads.forEach { r ->
            out.writeLong(r.id)
            out.writeUTF(r.name)
            out.writeInt(r.lat.size)
            for (i in r.lat.indices) {
                out.writeInt(r.lat[i])
                out.writeInt(r.lon[i])
            }
        }
    }

    companion object {
        private const val MAGIC = 0x4E535031 // "NSP1"

        /** Reads a part written by [write]; throws [IOException] if it is damaged. */
        fun read(inp: DataInputStream): StreetMapPart {
            if (inp.readInt() != MAGIC) throw IOException("not a street map part")
            val part = StreetMapPart()
            repeat(inp.readInt()) {
                val id = inp.readLong()
                val name = inp.readUTF()
                val n = inp.readInt()
                if (n !in 2..1_000_000) throw IOException("damaged street map part")
                val lat = IntArray(n)
                val lon = IntArray(n)
                for (i in 0 until n) {
                    lat[i] = inp.readInt()
                    lon[i] = inp.readInt()
                }
                part.roads += Road(id, name, lat, lon)
            }
            return part
        }
    }
}
