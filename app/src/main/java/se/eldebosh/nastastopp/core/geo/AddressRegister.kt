package se.eldebosh.nastastopp.core.geo

import se.eldebosh.nastastopp.core.parse.TextNorm

/**
 * Lantmäteriet's address register ("Belägenhetsadresser", CC BY 4.0) for the municipalities the
 * driver brought (`assets/addresses.txt`, made by `tools/make-addresses.py`): every address in
 * force with its point, on the phone. It is asked before Android's geocoder and knows the
 * addresses the geocoder does not. An address is taken only when its written postcode or town
 * is the register's (the post town or the municipality): a town is never guessed.
 */
class AddressRegister private constructor(private val streets: Map<String, List<Street>>) {

    private class Street(val name: String, val town: String, val postcode: String, val municipality: String, val places: Map<String, Pair<Double, Double>>)

    /** The post towns' keys: a municipality named as one of them (Karlstad) is read as the post town. */
    private val towns: Set<String> = streets.values.flatten().mapTo(HashSet()) { TextNorm.key(it.town) }

    val size: Int get() = streets.values.sumOf { list -> list.sumOf { it.places.size } }

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
        val written = NUMBERED.find(candidate.substringBefore(',').trim()) ?: return null
        val street = TextNorm.key(written.groupValues[1])
        val number = written.groupValues[2] + written.groupValues[3].uppercase()
        val numbered = streets[street].orEmpty().filter { it.places.containsKey(number) }
        // Only one such address there: two streets of one name in one town are never chosen between.
        val found = when {
            digits != null -> numbered.filter { it.postcode == digits }
            else -> numbered.filter { TextNorm.key(it.town) == place }.ifEmpty {
                if (place in towns) emptyList() else numbered.filter { TextNorm.key(it.municipality) == place }
            }
        }.singleOrNull() ?: return null
        val (lat, lng) = found.places.getValue(number)
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

    companion object {
        const val ASSET = "addresses.txt"

        val EMPTY = AddressRegister(emptyMap())

        /** A street and its number, with a letter perhaps: "Storgatan 14", "Storgatan 14B", "Storgatan 14 b". */
        private val NUMBERED = Regex("""^(.*\D)\s+(\d+)\s*([A-Za-zÅÄÖåäö]?)$""")

        fun parse(text: String): AddressRegister {
            val streets = HashMap<String, MutableList<Street>>()
            text.lineSequence().forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val parts = line.split('|')
                if (parts.size < 5) return@forEach
                val places = HashMap<String, Pair<Double, Double>>()
                for (spot in parts[4].split(';')) {
                    val p = spot.split(':')
                    val lat = p.getOrNull(1)?.toDoubleOrNull() ?: continue
                    val lng = p.getOrNull(2)?.toDoubleOrNull() ?: continue
                    places[p[0].uppercase()] = lat to lng
                }
                streets.getOrPut(TextNorm.key(parts[0])) { mutableListOf() } += Street(parts[0], parts[1], parts[2], parts[3], places)
            }
            return AddressRegister(streets)
        }
    }
}
