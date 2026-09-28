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
) {
    companion object {
        /** "Storgatan 14, 652 24 Karlstad" → "Storgatan 14" (the part before the first comma). */
        fun streetPart(address: String): String = address.substringBefore(',').trim().ifEmpty { address.trim() }
    }
}

/**
 * Everything the passenger display shows — and the only route data that is ever sent to a
 * second device: one previous trip, the next destination, three upcoming trips (address and
 * area) and the current announcement text (area names only).
 */
@Serializable
data class DisplaySnapshot(
    val active: Boolean = false,
    val previous: DisplayItem? = null,
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
        const val UPCOMING = 3

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
            return DisplaySnapshot(
                active = true,
                previous = completed.lastOrNull()?.let(item),
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
