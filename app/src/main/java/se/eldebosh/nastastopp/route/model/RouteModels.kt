package se.eldebosh.nastastopp.route.model

import kotlinx.serialization.Serializable

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
 * One stop. Holds only address data and the trip's scheduled time — never names, phone numbers
 * or other OCR text.
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
) {
    val isLocated: Boolean get() = geoStatus == GeoStatus.LOCATED && geo != null

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
) {
    val completedCount: Int get() = completed.size
    val previousStop: Stop? get() = completed.lastOrNull()
}
