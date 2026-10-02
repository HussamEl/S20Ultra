package se.eldebosh.nastastopp.core.geo

import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.TextNorm
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Plain copy of the fields we use from android.location.Address (keeps logic unit-testable). */
data class GeoResult(
    val lat: Double,
    val lng: Double,
    val addressLine: String?,
    val postalCode: String?,
    val locality: String?,
    val subLocality: String?,
    val thoroughfare: String?,
)

/**
 * What the announcement says. FULL (the default): the next stop's street and number, then the
 * district and the town; the stop after it with its street and number, then its district.
 * DISTRICT and TOWN_ONLY name both stops by district (or town) only.
 */
enum class AnnouncementDetail { FULL, DISTRICT, TOWN_ONLY }

object GeoLogic {
    /** Sweden bounding box used for the geocoder. */
    const val SWEDEN_LAT_MIN = 55.3
    const val SWEDEN_LNG_MIN = 10.9
    const val SWEDEN_LAT_MAX = 69.1
    const val SWEDEN_LNG_MAX = 24.2

    /** Värmland's bounding box, for a place written without a town. */
    const val VARMLAND_LAT_MIN = 58.8
    const val VARMLAND_LNG_MIN = 11.5
    const val VARMLAND_LAT_MAX = 61.7
    const val VARMLAND_LNG_MAX = 14.7

    const val NEXT_ADDRESS = "nästa adress"

    /**
     * For a place written without a town or postal code: the answer only when every result in
     * Värmland is in one and the same town; otherwise null, as the town would be a guess.
     */
    fun inOneTown(results: List<GeoResult>): GeoResult? {
        val inside = results.filter { it.lat in VARMLAND_LAT_MIN..VARMLAND_LAT_MAX && it.lng in VARMLAND_LNG_MIN..VARMLAND_LNG_MAX }
        val towns = inside.map { TextNorm.fold(it.locality ?: it.subLocality ?: return null) }.distinct()
        return if (towns.size == 1) inside.first() else null
    }

    /**
     * Prefer a result whose postal code matches [parsedPostal]; else whose locality matches
     * [parsedTown]; else the first result.
     */
    fun choose(results: List<GeoResult>, parsedPostal: String?, parsedTown: String?): GeoResult? {
        if (results.isEmpty()) return null
        val postalDigits = parsedPostal?.filter { it.isDigit() }
        if (!postalDigits.isNullOrEmpty()) {
            results.firstOrNull { it.postalCode?.filter { c -> c.isDigit() } == postalDigits }?.let { return it }
        }
        if (!parsedTown.isNullOrBlank()) {
            val town = TextNorm.fold(parsedTown)
            results.firstOrNull { r ->
                (r.locality != null && TextNorm.fold(r.locality) == town) ||
                    (r.subLocality != null && TextNorm.fold(r.subLocality) == town)
            }?.let { return it }
        }
        return results.first()
    }

    /**
     * The next stop said in full: "Storgatan 14, Herrhagen, Karlstad". The street and
     * number, then the district and the town, each once (compared without case or å ä ö). An
     * apartment number ("lgh 1402") is left out, and so is the placeholder for an unknown place.
     */
    fun fullSpokenName(street: String, district: String?, town: String?): String {
        val parts = mutableListOf<String>()
        for (part in listOf(street.replace(APARTMENT, "").trim().trimEnd(','), district, town)) {
            val p = part?.trim()?.takeIf { it.isNotEmpty() && it != NEXT_ADDRESS } ?: continue
            if (parts.none { TextNorm.fold(it) == TextNorm.fold(p) }) parts += p
        }
        return parts.joinToString(", ").ifEmpty { NEXT_ADDRESS }
    }

    private val APARTMENT = Regex("""(?i)\s*\b(lgh|lägenhet)\.?\s*\S+""")

    /**
     * The only text that is ever spoken for a stop: an area/town name, never a street, number,
     * person or facility. subLocality if present and different from locality, otherwise
     * locality, otherwise the parsed town (only if it is a known locality), otherwise
     * "nästa adress".
     */
    fun spokenName(
        subLocality: String?,
        locality: String?,
        parsedTown: String?,
        parsedTownKnown: Boolean,
        detail: AnnouncementDetail,
        thoroughfare: String? = null,
        isKnownLocality: (String) -> Boolean = { false },
    ): String {
        val loc = locality?.trim()?.takeIf { isSafeAreaName(it, thoroughfare, strict = false) }
        val sub = subLocality?.trim()?.takeIf {
            isKnownLocality(it) && isSafeAreaName(it, thoroughfare, strict = false) ||
                isSafeAreaName(it, thoroughfare, strict = true)
        }
        if (detail != AnnouncementDetail.TOWN_ONLY && sub != null &&
            (loc == null || TextNorm.fold(sub) != TextNorm.fold(loc))
        ) {
            return sub
        }
        if (loc != null) return loc
        val town = parsedTown?.trim()?.takeIf { parsedTownKnown && isSafeAreaName(it, thoroughfare, strict = false) }
        if (town != null) return town
        return NEXT_ADDRESS
    }

    /**
     * Guard so that a street, number or facility is never spoken: no digits, not the street
     * itself, reasonable length. [strict] (used for districts) also rejects names ending in a
     * street suffix ("…gatan", "…vägen" …); towns from the geocoder / locality list are trusted.
     */
    fun isSafeAreaName(name: String, thoroughfare: String? = null, strict: Boolean = true): Boolean {
        if (name.isBlank() || name.length > 40) return false
        if (name.any { it.isDigit() }) return false
        if (name.count { it.isLetter() } < 2) return false
        val folded = TextNorm.fold(name)
        if (thoroughfare != null && TextNorm.fold(thoroughfare).let { it.isNotEmpty() && folded.contains(it) }) return false
        if (strict) {
            val suffixes = AddressExtractor.STREET_SUFFIXES.map { TextNorm.fold(it) }
            if (folded.split(' ').any { w -> suffixes.any { s -> w.endsWith(s) } }) return false
        }
        return true
    }

    /** Great-circle distance in metres. */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
