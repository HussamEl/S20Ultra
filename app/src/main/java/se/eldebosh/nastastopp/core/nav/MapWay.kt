package se.eldebosh.nastastopp.core.nav

/**
 * What the tablet's map draws the way from and through: the tablet's own position ([lat], [lng],
 * heading [bearing]) and the next stops ([stops], the next first). Kept in memory only.
 */
data class MapWay(
    val lat: Double,
    val lng: Double,
    val bearing: Float? = null,
    val stops: List<Stop> = emptyList(),
) {
    /**
     * A stop on the map: its point when the phone has located it, else its [address]; [id] is the
     * trip's number in the route, when known. On its pin only, never sent to Google: [time], its
     * trip's time; [label], its street and number as the display writes it; [name], its
     * passenger's last name.
     */
    data class Stop(
        val lat: Double?,
        val lng: Double?,
        val address: String,
        val id: Long? = null,
        val time: String? = null,
        val label: String? = null,
        val name: String? = null,
    ) {
        /** Google can be asked the way to it. */
        val routable: Boolean get() = (lat != null && lng != null) || address.isNotBlank()

        /** The same stop on another answer: its number, or its point or address. */
        val key: String get() = id?.toString() ?: if (lat != null && lng != null) "$lat,$lng" else address
    }
}
