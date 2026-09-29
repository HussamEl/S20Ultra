package se.eldebosh.nastastopp.core.geo

import se.eldebosh.nastastopp.core.route.Fix

/** The street the vehicle is on now and its area. Shown to the driver only; never stored. */
data class StreetInfo(val street: String?, val area: String?) {
    /** What a tap on the street says: the street and the area ("Drottninggatan, Centrum"). */
    val spoken: String? get() = listOfNotNull(street, area).distinct().joinToString(", ").ifEmpty { null }
}

/**
 * When to ask the geocoder for the current street (pure logic, unit-tested): the first good fix,
 * then only after moving [MIN_MOVE_M] and at most every [MIN_INTERVAL_MS]; a failed lookup is
 * retried after [RETRY_MS] even when standing still.
 */
object StreetLookup {
    const val MIN_INTERVAL_MS = 8_000L
    const val MIN_MOVE_M = 35.0
    const val RETRY_MS = 30_000L
    const val MAX_ACCURACY_M = 60f

    fun shouldLookup(last: Fix?, fix: Fix, lastFailed: Boolean): Boolean {
        val acc = fix.accuracyM
        if (acc != null && acc > MAX_ACCURACY_M) return false
        if (last == null) return true
        val elapsed = fix.timeMs - last.timeMs
        if (elapsed in 0 until MIN_INTERVAL_MS) return false
        if (lastFailed && (elapsed < 0 || elapsed >= RETRY_MS)) return true
        return GeoLogic.distanceMeters(last.lat, last.lng, fix.lat, fix.lng) >= MIN_MOVE_M
    }

    /** Picks the street from reverse-geocoder results (the first one that has a street). */
    fun pick(results: List<GeoResult>): StreetInfo? {
        val r = results.firstOrNull { !it.thoroughfare.isNullOrBlank() } ?: results.firstOrNull() ?: return null
        val street = r.thoroughfare?.trim()?.takeIf { it.isNotEmpty() }
        val area = (r.subLocality?.trim()?.takeIf { it.isNotEmpty() } ?: r.locality?.trim())?.takeIf { it.isNotEmpty() }
        if (street == null && area == null) return null
        return StreetInfo(street, area)
    }
}
