package se.eldebosh.nastastopp.core.geo

import se.eldebosh.nastastopp.core.parse.TextNorm
import java.util.concurrent.ConcurrentHashMap

/**
 * Lantmäteriet's address register ("Belägenhetsadresser", CC BY 4.0) for the municipalities the
 * driver brought (Värmland's and Örebro's) (`assets/addresses.txt`, made by `tools/make-addresses.py`): every address in
 * force or reserved for a new building, with its point, and the places' popular names (a farm, a
 * school, a church), on the phone. It is asked before Android's geocoder and knows the addresses
 * the geocoder does not (village addresses, new streets). With it, Lantmäteriet's place names
 * ("Ortnamn", CC BY 4.0, `assets/places.txt`, made by `tools/make-places.py`): the villages, farms,
 * towns, churches and facilities of the same municipalities, for a place written by its name only
 * ("Glava, Arvika"). An address or a place is taken only when its written postcode or town is the
 * register's (the post town or the municipality): a town is never guessed.
 */
class AddressRegister private constructor(
    private val streets: Map<String, List<Street>>,
    private val named: Map<String, List<Named>> = emptyMap(),
    private val places: Map<String, List<Place>> = emptyMap(),
) {

    /**
     * A street of a post town and its houses, kept as written in the file ([spots]: "number:lat:lng"
     * separated by ";") and read only when the street is asked for: the register of two counties
     * stays small on the phone.
     */
    private class Street(val name: String, val town: String, val postcode: String, val municipality: String, private val spots: String) {
        val houses: Int get() = if (spots.isEmpty()) 0 else spots.count { it == ';' } + 1

        /** Every house's point. */
        fun points(): List<Pair<Double, Double>> = spots.split(';').mapNotNull { spot ->
            val p = spot.split(':')
            val lat = p.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val lng = p.getOrNull(2)?.toDoubleOrNull() ?: return@mapNotNull null
            lat to lng
        }

        /** The point of house [number] ("14", "14B"), or null. */
        fun at(number: String): Pair<Double, Double>? {
            var from = 0
            while (from < spots.length) {
                val end = spots.indexOf(';', from).let { if (it < 0) spots.length else it }
                val colon = from + number.length
                if (colon < end && spots[colon] == ':' && spots.regionMatches(from, number, 0, number.length, ignoreCase = true)) {
                    val second = spots.indexOf(':', colon + 1)
                    if (second in colon + 1 until end) {
                        val lat = spots.substring(colon + 1, second).toDoubleOrNull()
                        val lng = spots.substring(second + 1, end).toDoubleOrNull()
                        if (lat != null && lng != null) return lat to lng
                    }
                }
                from = end + 1
            }
            return null
        }
    }

    /** A popular name ([name]) of a post town, at one address or a few side by side ([spots]: point and address). */
    private class Named(val name: String, val town: String, val postcode: String, val municipality: String, val spots: List<Triple<Double, Double, String>>)

    /**
     * A place name ([name]: a village, a farm, a town, a church) in a municipality, with one point
     * for each place of that name there ([points]: 22 farms called Haget in Arvika).
     */
    private class Place(val name: String, val municipality: String, val points: List<Pair<Double, Double>>)

    /** The post towns' keys: a municipality named as one of them (Karlstad) is read as the post town. */
    private val towns: Set<String> = streets.values.flatten().mapTo(HashSet()) { TextNorm.key(it.town) }

    /** The streets of each postcode, of each post town (its key), and each municipality's name (by its key), made when a place is first asked for. */
    private val byPostcode by lazy { streets.values.flatten().groupBy { it.postcode } }
    private val byTown by lazy { streets.values.flatten().groupBy { TextNorm.key(it.town) } }
    private val municipalityNames by lazy { streets.values.flatten().associate { TextNorm.key(it.municipality) to it.municipality } }

    /** Where each postcode ("66530") or written town ("t:molkom") asked for lies, kept for the app's life. */
    private val wheres = ConcurrentHashMap<String, Where>()

    val size: Int get() = streets.values.sumOf { list -> list.sumOf { it.houses } }

    /** Popular names known, in all their post towns. */
    val names: Int get() = named.values.sumOf { it.size }

    /** Place names known, in all their municipalities. */
    val placeNames: Int get() = places.values.sumOf { it.size }

    /** The same register with Lantmäteriet's place names read from [text] (`assets/places.txt`). */
    fun withPlaces(text: String): AddressRegister {
        val read = HashMap<String, MutableList<Place>>()
        text.lineSequence().forEach { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val parts = line.split('|')
            if (parts.size < 3) return@forEach
            // lat:lng, one per place of the name, separated by ";".
            val points = parts[2].split(';').mapNotNull { spot ->
                val lat = spot.substringBefore(':').toDoubleOrNull() ?: return@mapNotNull null
                val lng = spot.substringAfter(':').toDoubleOrNull() ?: return@mapNotNull null
                lat to lng
            }
            if (points.isNotEmpty()) read.getOrPut(TextNorm.key(parts[0])) { mutableListOf() } += Place(parts[0], parts[1], points)
        }
        return AddressRegister(streets, named, read)
    }

    /**
     * The address [candidate] ("Storgatan 14 B, 652 24 Karlstad": its street and number before
     * the first comma) where it is written to be: its postcode [postal], else its post town
     * [town]. A municipality written as the town (Hammarö for Skoghall) is taken only when it is
     * no post town's name and every such address in it is in one post town. Null when the register
     * does not have it there, or when neither is written: a town is never guessed (Storgatan 14,
     * Karlstad is never Molkom's, in Karlstad municipality).
     */
    fun find(candidate: String, postal: String?, town: String?): GeoResult? {
        val digits = postal?.filter { it.isDigit() }?.takeIf { it.length == 5 }
        val place = town?.let { TextNorm.key(it) }?.takeIf { it.isNotEmpty() }
        if (digits == null && place == null) return null
        val written = NUMBERED.find(candidate.substringBefore(',').trim()) ?: return named(candidate, digits, place)
        val street = TextNorm.key(written.groupValues[1])
        val number = written.groupValues[2] + written.groupValues[3].uppercase()
        val numbered = streets[street].orEmpty().mapNotNull { s -> s.at(number)?.let { s to it } }
        // Only one such address there: two streets of one name in one town are never chosen between.
        val (found, point) = inTown(numbered, digits, place, { it.first.postcode }, { it.first.town }, { it.first.municipality }).singleOrNull() ?: return null
        val (lat, lng) = point
        val shown = "${found.name} ${written.groupValues[2]}${written.groupValues[3].uppercase().let { if (it.isEmpty()) "" else " $it" }}"
        return GeoResult(
            lat = lat,
            lng = lng,
            addressLine = "$shown, ${found.postcode.chunked(3).joinToString(" ")} ${found.town}",
            postalCode = found.postcode,
            locality = found.town,
            subLocality = null,
            thoroughfare = found.name,
        )
    }

    /**
     * A place written by its popular name ("Grava kyrka, Karlstad"), where it is written to be, as
     * [find]; taken only when it is one place there (its addresses within [SAME_PLACE_M] of each
     * other): the first of them.
     */
    private fun named(candidate: String, digits: String?, place: String?): GeoResult? {
        val written = candidate.substringBefore(',').trim()
        val found = inTown(named[TextNorm.key(written)].orEmpty(), digits, place, { it.postcode }, { it.town }, { it.municipality })
            .singleOrNull() ?: return placed(written, digits, place)
        val (lat, lng, address) = found.spots.first()
        if (found.spots.any { (la, ln) -> GeoLogic.distanceMeters(lat, lng, la, ln) > SAME_PLACE_M }) return null
        return GeoResult(
            lat = lat,
            lng = lng,
            addressLine = "${found.name}, $address, ${found.postcode.chunked(3).joinToString(" ")} ${found.town}".trim(),
            postalCode = found.postcode,
            locality = found.town.ifEmpty { null },
            subLocality = null,
            thoroughfare = null,
        )
    }

    /**
     * A place written by its name ("Glava, Arvika", "Ölme kyrka, 681 94 Kristinehamn") in
     * Lantmäteriet's place names, in the municipalities the written postcode [digits] or town
     * [place] lies in (the address register says which), and said in the written town (the
     * postcode's post town). Taken only when the name is one place there; or, when it is no
     * place's name, written after its village ("Provby Provgården": the village, then the farm),
     * when the village is one place there and only one place of the farm's name lies within
     * [NEAR_VILLAGE_M] of it. Near the postcode's or post town's own addresses first: a postcode
     * may cross into another municipality, whose places of the same name lie far away. Anywhere in
     * its municipalities only when none lies near. Two places of one name are never chosen between.
     */
    private fun placed(written: String, digits: String?, place: String?): GeoResult? {
        if (!mayBeAPlace(written)) return null
        val (municipalities, town, area) = municipalitiesOf(digits, place) ?: return null
        // The places of a name in those municipalities, with only their points in [near] when it is given.
        fun there(near: Area?): (String) -> List<Place> = { name ->
            places[TextNorm.key(name)].orEmpty()
                .filter { TextNorm.key(it.municipality) in municipalities }
                .map { p -> if (near == null) p else Place(p.name, p.municipality, p.points.filter(near::holds)) }
                .filter { it.points.isNotEmpty() }
        }
        val nearFirst = listOfNotNull(area?.let { there(it) }, there(null))
        // A name that is a place's name is that place; split into a village and its farm only when it is none.
        val found = nearFirst.firstNotNullOfOrNull { p -> p(written).flatMap { place -> place.points.map { place.name to it } }.ifEmpty { null } }
            ?: nearFirst.firstNotNullOfOrNull { p -> nearItsVillage(written, p, there(null)).ifEmpty { null } }
        val (name, point) = found?.singleOrNull() ?: return null
        return GeoResult(
            lat = point.first,
            lng = point.second,
            addressLine = listOfNotNull(name, town).joinToString(", "),
            postalCode = digits,
            locality = town,
            subLocality = null,
            thoroughfare = null,
        )
    }

    /** Whether [written], or the words it splits into at a space ([nearItsVillage]), are place names at all. */
    private fun mayBeAPlace(written: String): Boolean {
        if (TextNorm.key(written) in places) return true
        val words = written.trim().split(SPACES)
        return (1 until words.size).any { cut ->
            TextNorm.key(words.take(cut).joinToString(" ")) in places && TextNorm.key(words.drop(cut).joinToString(" ")) in places
        }
    }

    /**
     * [written] read as a village and a farm of it ("Provby Provgården", split at each space): the
     * places of the farm's name within [NEAR_VILLAGE_M] of the village, over every way of splitting
     * it. The village is one place of [villages]; its farms are counted among all of [farms], so a
     * second farm of the name near the village is never left out.
     */
    private fun nearItsVillage(
        written: String,
        villages: (String) -> List<Place>,
        farms: (String) -> List<Place>,
    ): List<Pair<String, Pair<Double, Double>>> {
        val words = written.trim().split(SPACES)
        if (words.size < 2) return emptyList()
        return (1 until words.size).flatMap { cut ->
            val village = villages(words.take(cut).joinToString(" ")).singleOrNull() ?: return@flatMap emptyList()
            val at = village.points.singleOrNull() ?: return@flatMap emptyList()
            farms(words.drop(cut).joinToString(" ")).flatMap { farm ->
                farm.points.filter { (lat, lng) -> GeoLogic.distanceMeters(at.first, at.second, lat, lng) <= NEAR_VILLAGE_M }
                    .map { "${village.name} ${farm.name}" to it }
            }
        }
    }

    /** Where a written postcode or post town lies: its addresses' extent, widened by [AREA_MARGIN_DEG]. */
    private class Area(val south: Double, val north: Double, val west: Double, val east: Double) {
        fun holds(p: Pair<Double, Double>) = p.first in south..north && p.second in west..east

        companion object {
            fun around(points: List<Pair<Double, Double>>): Area? = if (points.isEmpty()) null else Area(
                points.minOf { it.first } - AREA_MARGIN_DEG,
                points.maxOf { it.first } + AREA_MARGIN_DEG,
                points.minOf { it.second } - 2 * AREA_MARGIN_DEG,
                points.maxOf { it.second } + 2 * AREA_MARGIN_DEG,
            )
        }
    }

    /** The municipalities a written postcode or town lies in, the town to say, and its area (none for a municipality). */
    private data class Where(val municipalities: Set<String>, val town: String?, val area: Area?)

    /**
     * The municipalities (their keys) the written postcode [digits] or town [place] lies in, the
     * town to say (the postcode's post town, the post town written, or the municipality written as
     * the town, only when it is no post town's name), and the area of the postcode's or the post
     * town's addresses.
     */
    private fun municipalitiesOf(digits: String?, place: String?): Where? {
        val key = digits ?: place?.let { "t:$it" } ?: return null
        wheres[key]?.let { return it }
        val where = if (digits != null) {
            val there = byPostcode[digits]
            if (there.isNullOrEmpty()) return null
            Where(there.mapTo(HashSet()) { TextNorm.key(it.municipality) }, there.first().town, Area.around(there.flatMap { it.points() }))
        } else {
            val there = byTown[place].orEmpty()
            val asMunicipality = place?.takeIf { it !in towns }
            val named = there.firstOrNull()?.town ?: asMunicipality?.let { municipalityNames[it] }
            // A municipality written as the town has no area: all of it is the town.
            Where(there.mapTo(HashSet()) { TextNorm.key(it.municipality) } + listOfNotNull(asMunicipality), named, Area.around(there.flatMap { it.points() }))
        }
        return where.also { wheres[key] = it }
    }

    /**
     * Those of [all] at the written postcode [digits], else in the written post town [place]; a
     * municipality written as the town only when it is no post town's name.
     */
    private fun <T> inTown(all: List<T>, digits: String?, place: String?, postcode: (T) -> String, town: (T) -> String, municipality: (T) -> String): List<T> = when {
        digits != null -> all.filter { postcode(it) == digits }
        else -> all.filter { TextNorm.key(town(it)) == place }.ifEmpty {
            if (place in towns) emptyList() else all.filter { TextNorm.key(municipality(it)) == place }
        }
    }

    companion object {
        const val ASSET = "addresses.txt"
        const val PLACES_ASSET = "places.txt"

        /** A postcode's or post town's area reaches this far beyond its addresses (about 3 km north–south; twice as many degrees east–west at these latitudes). */
        private const val AREA_MARGIN_DEG = 0.03

        /** A farm written after its village is the one of its name this near the village. */
        private const val NEAR_VILLAGE_M = 2_000.0

        private val SPACES = Regex("""\s+""")

        /** The addresses of one popular name lie this near each other to be one place. */
        private const val SAME_PLACE_M = 250.0

        val EMPTY = AddressRegister(emptyMap())

        private const val NAMED = '@'

        /** A street and its number, with a letter perhaps: "Storgatan 14", "Storgatan 14B", "Storgatan 14 b". */
        private val NUMBERED = Regex("""^(.*\D)\s+(\d+)\s*([A-Za-zÅÄÖåäö]?)$""")

        fun parse(text: String): AddressRegister {
            val streets = HashMap<String, MutableList<Street>>()
            val named = HashMap<String, MutableList<Named>>()
            text.lineSequence().forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val parts = line.split('|')
                if (parts.size < 5) return@forEach
                if (line[0] == NAMED) {
                    // lat:lng:street number (a street may hold a colon: "S:t Olofsgatan").
                    val spots = parts[4].split(';').mapNotNull { spot ->
                        val p = spot.split(':', limit = 3)
                        val lat = p.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                        val lng = p.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                        Triple(lat, lng, p.getOrNull(2).orEmpty())
                    }
                    val name = parts[0].substring(1)
                    if (spots.isNotEmpty()) named.getOrPut(TextNorm.key(name)) { mutableListOf() } += Named(name, parts[1], parts[2], parts[3], spots)
                    return@forEach
                }
                streets.getOrPut(TextNorm.key(parts[0])) { mutableListOf() } += Street(parts[0], parts[1], parts[2], parts[3], parts[4])
            }
            return AddressRegister(streets, named)
        }
    }
}
