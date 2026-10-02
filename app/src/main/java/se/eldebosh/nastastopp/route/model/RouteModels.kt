package se.eldebosh.nastastopp.route.model

import kotlinx.serialization.Serializable
import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.core.parse.TripKind
import se.eldebosh.nastastopp.core.youdrive.TripWatch

@Serializable
enum class GeoStatus { PENDING, LOCATED, NOT_LOCATED }

@Serializable
data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val addressLine: String? = null,
    val postalCode: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
    val thoroughfare: String? = null,
)

/**
 * One stop. Holds only address data, the trip's scheduled time, its kind (the list's label) and
 * the passenger's first + last name — never phone numbers or other OCR text. The name is shown
 * on the driver's own screens only: it is never spoken, sent to the passenger display, put in a
 * notification or the history, or logged.
 */
@Serializable
data class Stop(
    val id: Long,
    val displayText: String,
    val candidates: List<String>,
    val parsedPostalCode: String? = null,
    val parsedTown: String? = null,
    val parsedTownKnown: Boolean = false,
    val sourceOrder: Int = 0,
    val geoStatus: GeoStatus = GeoStatus.PENDING,
    val geo: GeoPoint? = null,
    /** Scheduled time from the screenshot ("12:48"), or null. */
    val time: String? = null,
    /** Pick-up / drop-off / back to the depot, from the dispatch list's label, or null. */
    val kind: TripKind? = null,
    /** The passenger's first and last name ("Anna Testsson"), or null. Driver's screens only. */
    val name: String? = null,
    /**
     * YouDrive shows the trip as done ("Performed"). The route does not follow it: the driver may
     * mark trips done in YouDrive before reaching them, so only Next moves the route. Done in this
     * app means being in [RouteData.completed].
     */
    val youDriveDone: Boolean = false,
    /**
     * The named place YouDrive writes with the address ("Kils Vårdcentral", "Centralsjukhuset
     * Huvudentrén"), or null. Only a place of care's own name ever reaches the passengers
     * ([se.eldebosh.nastastopp.core.parse.Places]).
     */
    val place: String? = null,
) {
    val isLocated: Boolean get() = geoStatus == GeoStatus.LOCATED && geo != null

    /**
     * The address as the driver's screens show it: the place, then the street address
     * ("Provby Vårdcentral · Strandvägen 3, 652 25 Karlstad"); a place without a street as written.
     */
    val shownAddress: String
        get() {
            val place = place ?: return displayText
            return if (TextNorm.fold(streetText).contains(TextNorm.fold(place))) streetText else "$place · $streetText"
        }

    /**
     * The fullest address that starts at the street ("Strandvägen 3, 665 30 Kil"): without a
     * place or surname written before it; a place without a street as written.
     */
    val streetText: String
        get() {
            val core = TripWatch.streetAddress(candidates, displayText).substringBefore(',').trim()
            return candidates.filter { it.startsWith(core) }.maxByOrNull { it.length } ?: displayText
        }

    /** Written without a town, postal code or house number ("Sjukhuset Huvudentrén"): the town is not known. */
    val townUnknown: Boolean
        get() = parsedTown == null && parsedPostalCode == null && displayText.none { it.isDigit() } && geo?.locality == null

    /** Text handed to Google Maps: the geocoder's formatted address when located, else the cleaned text. */
    val navigationText: String
        get() = geo?.addressLine?.takeIf { isLocated && it.isNotBlank() } ?: displayText
}

/**
 * The route. [stops] are the remaining stops in order; when [active], stops[0] is the current
 * (next) stop. Completed stops move to [completed] (oldest first) and stay visible until the
 * route ends; the most recent one is also used for the "two stops at the same place" rule.
 */
@Serializable
data class RouteData(
    val createdAtMs: Long,
    val active: Boolean = false,
    val stops: List<Stop> = emptyList(),
    val completed: List<Stop> = emptyList(),
    val batchEndStopId: Long? = null,
    val nextId: Long = 1,
    /** First stop of the batch last handed to Google Maps (decides whether "back" re-launches it). */
    val batchStartStopId: Long? = null,
    /**
     * The day's start point (YouDrive's "Pull-out", the depot): shown above the trips, but not a
     * stop — it is not navigated to, announced or counted.
     */
    val depot: Stop? = null,
) {
    val completedCount: Int get() = completed.size
}
