package se.eldebosh.nastastopp.core.geo

import se.eldebosh.nastastopp.core.parse.TextNorm

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

    /** A place's name ([name]: a village, a farm, a town, a church) in a municipality, at one point. */
    private class Place(val name: String, val municipality: String, val lat: Double, val lng: Double)

    /** The post towns' keys: a municipality named as one of them (Karlstad) is read as the post town. */
    private val towns: Set<String> = streets.values.flatten().mapTo(HashSet()) { TextNorm.key(it.town) }

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
            val lat = parts[2].substringBefore(':').toDoubleOrNull() ?: return@forEach
            val lng = parts[2].substringAfter(':').toDoubleOrNull() ?: return@forEach
            read.getOrPut(TextNorm.key(parts[0])) { mutableListOf() } += Place(parts[0], parts[1], lat, lng)
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
     * Lantmäteriet's place names: taken only when it is one place in the municipalities the written
     * postcode [digits] or town [place] lies in (the address register says which), and said in the
     * written town (the postcode's post town).
     */
    private fun placed(written: String, digits: String?, place: String?): GeoResult? {
        val all = places[TextNorm.key(written)] ?: return null
        val (municipalities, town) = when {
            digits != null -> {
                val there = streets.values.flatten().filter { it.postcode == digits }
                there.mapTo(HashSet()) { TextNorm.key(it.municipality) } to there.firstOrNull()?.town
            }
            else -> {
                val there = streets.values.flatten().filter { TextNorm.key(it.town) == place }
                val named = there.firstOrNull()?.town ?: all.firstOrNull { TextNorm.key(it.municipality) == place }?.municipality
                (there.mapTo(HashSet()) { TextNorm.key(it.municipality) } + listOfNotNull(place.takeIf { it !in towns })) to named
            }
        }
        val found = all.filter { TextNorm.key(it.municipality) in municipalities }.singleOrNull() ?: return null
        return GeoResult(
            lat = found.lat,
            lng = found.lng,
            addressLine = listOfNotNull(found.name, town).joinToString(", "),
            postalCode = digits,
            locality = town,
            subLocality = null,
            thoroughfare = null,
        )
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
