package se.eldebosh.nastastopp.core.nav

/**
 * What the tablet's map draws the way from and to: the tablet's own position ([lat], [lng],
 * heading [bearing]) and the stop (its point [toLat]/[toLng] when the phone has located it, else
 * its address [to]). Kept in memory only.
 */
data class MapWay(
    val lat: Double,
    val lng: Double,
    val bearing: Float? = null,
    val toLat: Double? = null,
    val toLng: Double? = null,
    val to: String? = null,
) {
    /** A trip the map shows the way to: its point when located, else its [address]. */
    data class Stop(val lat: Double?, val lng: Double?, val address: String)
}
