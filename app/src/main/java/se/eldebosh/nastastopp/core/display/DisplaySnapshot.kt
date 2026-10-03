package se.eldebosh.nastastopp.core.display

import kotlinx.serialization.Serializable
import se.eldebosh.nastastopp.core.nav.DisplayEta
import se.eldebosh.nastastopp.core.nav.MapWay
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
    /** How [title] is said when it differs from what is written: a place of care's full name ("Centralsjukhuset, huvudentrén" for "C-Sjukhuset"). */
    val said: String? = null,
    /** Everything on the trip's YouDrive card, as written: shown only when the driver opens it (234), never said. */
    val card: String? = null,
    /** The trip's number in the route, for the order the driver sets on the tablet's map. */
    val id: Long? = null,
    /**
     * The same number on a passenger's pick-up and drop-off (never the name), so the tablet's map
     * keeps a pick-up before its drop-off when the driver changes the order.
     */
    val rider: Int? = null,
) {
    /**
     * The same trip whatever its done marks or name: it keeps its place on the display when marked,
     * and "Därefter" grows into the next stop.
     */
    val trip: DisplayItem get() = if (doneInYouDrive || doneHere || lastName != null || card != null) copy(doneInYouDrive = false, doneHere = false, lastName = null, card = null) else this

    /** The trip on the tablet's map: its point, or the address the phone navigates to (never a name). */
    val mapStop: MapWay.Stop get() = MapWay.Stop(lat, lng, place ?: listOfNotNull(title, subtitle).joinToString(", "), id)

    companion object {
        /** "Storgatan 14, 652 24 Karlstad" → "Storgatan 14" (the part before the first comma). */
        fun streetPart(address: String): String = address.substringBefore(',').trim().ifEmpty { address.trim() }
    }
}

/**
 * Everything the passenger display shows — and the only route data that is ever sent to a
 * second device: up to seven trips done, the next destination and up to seven upcoming trips
 * (time, address and area, the address and point the tablet's map routes to, trip kind, where
 * each was marked done, its number and a number shared by a passenger's pick-up and drop-off),
 * the next stop's passenger's last name, each YouDrive trip's whole card
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

    /** The trips still to come, the next first. */
    val ahead: List<DisplayItem> get() = listOfNotNull(current) + upcoming

    companion object {
        const val UPCOMING = 7
        const val EARLIER = 7

        /** Trips before ([BEFORE]) and after ([AFTER]) the one looked at on the tablet's map; [MOST] with the ones added. */
        const val BEFORE = 1
        const val AFTER = 2
        const val MOST = 7

        /**
         * The trips of the tablet's map around [at] (an index into [ahead]): up to [BEFORE] before it
         * and [AFTER] after it, and its place among them. Each of the [added] trips the driver asked
         * for comes in its place: the next one after the way, or, when none is left after, the one
         * before it; [MOST] at most.
         */
        fun <T> around(ahead: List<T>, at: Int, added: Int = 0): Pair<List<T>, Int> {
            if (ahead.isEmpty()) return emptyList<T>() to 0
            val i = at.coerceIn(0, ahead.size - 1)
            var from = (i - BEFORE).coerceAtLeast(0)
            var to = minOf(ahead.size, i + AFTER + 1)
            repeat(added) {
                if (to - from >= MOST) return@repeat
                if (to < ahead.size) to++ else if (from > 0) from--
            }
            return ahead.subList(from, to) to i - from
        }

        /** One more trip can be added to the way around [at] ([around]). */
        fun <T> canAdd(ahead: List<T>, at: Int, added: Int): Boolean =
            around(ahead, at, added + 1).first.size > around(ahead, at, added).first.size

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
