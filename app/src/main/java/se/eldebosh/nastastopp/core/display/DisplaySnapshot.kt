package se.eldebosh.nastastopp.core.display

import kotlinx.serialization.Serializable
import se.eldebosh.nastastopp.core.route.Announcement

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
) {
    /** The same trip whatever its done marks: it keeps its place on the display when marked. */
    val trip: DisplayItem get() = if (doneInYouDrive || doneHere) copy(doneInYouDrive = false, doneHere = false) else this

    companion object {
        /** "Storgatan 14, 652 24 Karlstad" → "Storgatan 14" (the part before the first comma). */
        fun streetPart(address: String): String = address.substringBefore(',').trim().ifEmpty { address.trim() }
    }
}

/**
 * Everything the passenger display shows — and the only route data that is ever sent to a
 * second device: up to seven trips done, the next destination and up to seven upcoming trips
 * (time, address and area, and where each was marked done), and the current announcement text.
 * Never names.
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
) {
    val announcement: Announcement?
        get() = announcementSv?.let { Announcement(it, announcementEn) }

    companion object {
        const val UPCOMING = 7
        const val EARLIER = 7

        /**
         * @param completed finished trips, oldest first.
         * @param remaining remaining trips; the first is the next destination.
         */
        fun <T> build(
            active: Boolean,
            completed: List<T>,
            remaining: List<T>,
            item: (T) -> DisplayItem,
            announcement: Announcement?,
        ): DisplaySnapshot {
            if (!active || remaining.isEmpty()) return DisplaySnapshot(active = false, completed = completed.size)
            val done = completed.takeLast(EARLIER).map { item(it).copy(doneHere = true) }
            return DisplaySnapshot(
                active = true,
                previous = done.lastOrNull(),
                earlier = done,
                current = item(remaining.first()),
                upcoming = remaining.drop(1).take(UPCOMING).map(item),
                remaining = remaining.size,
                completed = completed.size,
                announcementSv = announcement?.swedish,
                announcementEn = announcement?.english,
            )
        }
    }
}
