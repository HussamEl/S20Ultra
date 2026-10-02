package se.eldebosh.nastastopp.core.display

import kotlinx.serialization.Serializable
import se.eldebosh.nastastopp.core.nav.DisplayEta
import se.eldebosh.nastastopp.core.parse.TripKind
import se.eldebosh.nastastopp.core.route.Announcement
import se.eldebosh.nastastopp.core.weather.DisplayWeather

/** One trip as shown on the passenger display. */
@Serializable
data class DisplayItem(
    /** Scheduled time ("12:48") or null. */
    val time: String? = null,
    /** Street address with the house number ("Storgatan 14"), or the area name if the driver turned that off. */
    val title: String,
    /** Area / town name under the address (null when the title already is the area). */
    val subtitle: String? = null,
    /** YouDrive shows the trip as done (the driver may mark trips there before reaching them). */
    val doneInYouDrive: Boolean = false,
    /** Done in this app: the route has passed it. */
    val doneHere: Boolean = false,
    /** The list's Pick-up / Drop-off / Pull-out, for the stripe on a tablet's floating panel. */
    val kind: TripKind? = null,
    /** The address as the phone navigates to it ("Storgatan 14, 652 24 Karlstad"), for the tablet's map. */
    val place: String? = null,
    /** The stop's point when the phone has located it, for the tablet's map. */
    val lat: Double? = null,
    val lng: Double? = null,
    /** The passenger's last name, on the next stop only: shown under its address and said when tapped. */
    val lastName: String? = null,
    /** How [title] is said when it differs from what is written: a place of care's full name ("Centralsjukhuset, huvudentrén" for "Sjukhuset C"). */
    val said: String? = null,
    /** Everything on the trip's YouDrive card, as written: shown only when the driver opens it (234), never said. */
    val card: String? = null,
) {
    /**
     * The same trip whatever its done marks or name: it keeps its place on the display when marked,
     * and "Därefter" grows into the next stop.
     */
    val trip: DisplayItem get() = if (doneInYouDrive || doneHere || lastName != null || card != null) copy(doneInYouDrive = false, doneHere = false, lastName = null, card = null) else this

    companion object {
        /** "Storgatan 14, 652 24 Karlstad" → "Storgatan 14" (the part before the first comma). */
        fun streetPart(address: String): String = address.substringBefore(',').trim().ifEmpty { address.trim() }
    }
}

/**
 * Everything the passenger display shows — and the only route data that is ever sent to a
 * second device: up to seven trips done, the next destination and up to seven upcoming trips
 * (time, address and area, the address and point the tablet's map routes to, trip kind, and where
 * each was marked done), the next stop's passenger's last name, each YouDrive trip's whole card
 * (for the driver, shown only when opened), the current announcement text, the area's weather and
 * Google Maps' remaining travel time. Outside the trip cards, never a first name or another trip's
 * name; never the vehicle's position.
 */
@Serializable
data class DisplaySnapshot(
    val active: Boolean = false,
    /** The trip just done ([earlier]'s last). */
    val previous: DisplayItem? = null,
    /** Trips done, oldest first. */
    val earlier: List<DisplayItem> = emptyList(),
    val current: DisplayItem? = null,
    val upcoming: List<DisplayItem> = emptyList(),
    val remaining: Int = 0,
    val completed: Int = 0,
    val announcementSv: String? = null,
    val announcementEn: String? = null,
    /** The weather for the driver's area (a fixed place, never the vehicle's position), or null. */
    val weather: DisplayWeather? = null,
    /** Google Maps' remaining travel time, from its navigation notification on the phone, or null. */
    val eta: DisplayEta? = null,
) {
    val announcement: Announcement?
        get() = announcementSv?.let { Announcement(it, announcementEn) }

    companion object {
        const val UPCOMING = 7
        const val EARLIER = 7

        /**
         * @param completed finished trips, oldest first.
         * @param remaining remaining trips; the first is the next destination.
         * @param nextName the last name of the next destination's passenger (no other trip's).
         */
        fun <T> build(
            active: Boolean,
            completed: List<T>,
            remaining: List<T>,
            item: (T) -> DisplayItem,
            announcement: Announcement?,
            nextName: (T) -> String? = { null },
        ): DisplaySnapshot {
            if (!active || remaining.isEmpty()) return DisplaySnapshot(active = false, completed = completed.size)
            val done = completed.takeLast(EARLIER).map { item(it).copy(doneHere = true) }
            return DisplaySnapshot(
                active = true,
                previous = done.lastOrNull(),
                earlier = done,
                current = item(remaining.first()).copy(lastName = nextName(remaining.first())),
                upcoming = remaining.drop(1).take(UPCOMING).map(item),
                remaining = remaining.size,
                completed = completed.size,
                announcementSv = announcement?.swedish,
                announcementEn = announcement?.english,
            )
        }
    }
}
