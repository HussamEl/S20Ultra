package se.eldebosh.nastastopp.core.geo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.eldebosh.nastastopp.core.parse.TextNorm

/**
 * Karlstad's districts (stadsdelar) as the municipality draws them, and the streets in each: the
 * app's own address archive (`assets/karlstad_districts.json`, made by `tools/make-districts.py`
 * from Karlstads kommun's open data and OpenStreetMap's street names; no house numbers, no names
 * of people). A district is never taken from the geocoder inside Karlstad: its neighbourhood
 * names are often the next district's.
 */
class Districts private constructor(private val areas: List<Area>) {

    private class Area(val name: String, val rings: List<DoubleArray>, val streets: Set<String>) {
        val west = rings.minOf { r -> (r.indices step 2).minOf { r[it] } }
        val east = rings.maxOf { r -> (r.indices step 2).maxOf { r[it] } }
        val south = rings.minOf { r -> (1 until r.size step 2).minOf { r[it] } }
        val north = rings.maxOf { r -> (1 until r.size step 2).maxOf { r[it] } }

        /** Even–odd over every ring, so a hole is left out. */
        fun contains(lat: Double, lng: Double): Boolean {
            if (lng < west || lng > east || lat < south || lat > north) return false
            var inside = false
            for (r in rings) {
                var j = r.size - 2
                for (i in 0 until r.size step 2) {
                    val xi = r[i]; val yi = r[i + 1]; val xj = r[j]; val yj = r[j + 1]
                    if ((yi > lat) != (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
                    j = i
                }
            }
            return inside
        }
    }

    /** The district the point lies in, or null outside them. */
    fun at(lat: Double, lng: Double): String? = areas.firstOrNull { it.contains(lat, lng) }?.name

    /** The districts a street runs through, or null for a street the archive does not have. */
    fun of(street: String?): List<String>? {
        val key = street?.let { TextNorm.fold(it).trim() }?.takeIf { it.isNotEmpty() } ?: return null
        return areas.filter { key in it.streets }.map { it.name }.ifEmpty { null }
    }

    /**
     * The district for a stop or a position: where its point lies, when its street runs there
     * too; else, in Karlstad, its street's only district, or none (the town alone is said, never a
     * guess). The street archive is Karlstad's only: a street of the same name in another town
     * (Järnvägsgatan in Storfors) never gets a Karlstad district. Outside Karlstad the geocoder's
     * [subLocality] is kept.
     */
    fun district(lat: Double, lng: Double, street: String?, locality: String?, subLocality: String?): String? {
        val here = at(lat, lng)
        val streets = of(street)
        if (here != null && (streets == null || here in streets)) return here
        val inKarlstad = locality != null && TextNorm.fold(locality) == KARLSTAD
        if (!inKarlstad) return if (here != null) null else subLocality
        return streets?.singleOrNull()
    }

    /** [name] is one of Karlstad's own districts. */
    fun isOfficial(name: String): Boolean = areas.any { it.name == name }

    val size: Int get() = areas.size

    @Serializable
    private class FileArea(val name: String, val rings: List<List<Double>>, val streets: List<String>)

    @Serializable
    private class FileData(val districts: List<FileArea>)

    companion object {
        private const val KARLSTAD = "karlstad"

        const val ASSET = "karlstad_districts.json"

        val EMPTY = Districts(emptyList())

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): Districts = Districts(
            json.decodeFromString<FileData>(text).districts.map { a ->
                Area(a.name, a.rings.map { it.toDoubleArray() }, a.streets.map { TextNorm.fold(it).trim() }.toSet())
            },
        )
    }
}
