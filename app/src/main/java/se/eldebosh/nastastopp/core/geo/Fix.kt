package se.eldebosh.nastastopp.core.geo

/**
 * A location fix, used only to name the street and show the speed. Speed in m/s and heading in
 * degrees from north (null if unknown), accuracy in metres.
 */
data class Fix(
    val timeMs: Long,
    val lat: Double,
    val lng: Double,
    val speedMps: Float?,
    val accuracyM: Float?,
    val bearingDeg: Float? = null,
)
