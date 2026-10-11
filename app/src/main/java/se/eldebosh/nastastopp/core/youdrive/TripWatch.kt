package se.eldebosh.nastastopp.core.youdrive

import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.ExtractedStop
import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.core.parse.TripTimes

/**
 * A trip read from the YouDrive page: its scheduled time, address, kind and the passenger's first
 * and last name (the extractor drops every other text). [stop] is what is added to the route.
 *
 * @property booked the card's second time (a pick-up's booked time, a drop-off's latest arrival),
 *   which stays put when the schedule is re-planned; null if the card shows one time.
 * @property done the card's status says the trip is done ("Performed", "Departed").
 */
data class WatchedTrip(
    val time: String?,
    val address: String,
    val stop: ExtractedStop? = null,
    val booked: String? = null,
    val done: Boolean = false,
) {
    /** Identity used to compare readings: the booked (else scheduled) time + normalised address. */
    val key: String get() = "${(booked ?: time).orEmpty()}|${TextNorm.key(address)}"
}

/** A trip that appeared ([added]) or disappeared (cancelled) on the YouDrive page. */
data class TripChange(val trip: WatchedTrip, val added: Boolean)

/**
 * Detects added and cancelled trips between readings of the YouDrive page (pure logic).
 *
 * - The first non-empty reading is the baseline (no alerts).
 * - A change must be seen in [confirmReadings] readings in a row before it is reported, so a
 *   half-loaded page does not raise false alarms.
 * - Empty readings are ignored (page loading, logged out, network error).
 * - A trip that disappears more than [doneGraceMin] minutes after its time is treated as done,
 *   not cancelled (the page may drop finished trips).
 */
class TripWatch(private val confirmReadings: Int = 2, private val doneGraceMin: Int = 5) {

    var baseline: List<WatchedTrip>? = null
        private set
    private var pending: List<WatchedTrip>? = null
    private var pendingCount = 0

    fun reset() {
        baseline = null
        pending = null
        pendingCount = 0
    }

    /** Feeds one reading; returns the confirmed changes (usually none). */
    fun onReading(trips: List<WatchedTrip>, nowMinuteOfDay: Int): List<TripChange> {
        if (trips.isEmpty()) return emptyList()
        val base = baseline
        if (base == null) {
            baseline = trips
            return emptyList()
        }
        if (sameKeys(base, trips)) {
            baseline = trips
            pending = null
            pendingCount = 0
            return emptyList()
        }
        val p = pending
        if (p != null && sameKeys(p, trips)) pendingCount++ else {
            pending = trips
            pendingCount = 1
        }
        if (pendingCount < confirmReadings) return emptyList()
        baseline = trips
        pending = null
        pendingCount = 0
        return diff(base, trips, nowMinuteOfDay, doneGraceMin)
    }

    companion object {
        private fun sameKeys(a: List<WatchedTrip>, b: List<WatchedTrip>) = a.map { it.key }.sorted() == b.map { it.key }.sorted()

        /** Trips added to / removed from [before] (as multisets: the same address can occur twice). */
        fun diff(before: List<WatchedTrip>, after: List<WatchedTrip>, nowMinuteOfDay: Int, doneGraceMin: Int = 5): List<TripChange> {
            val remaining = before.groupBy { it.key }.mapValues { it.value.toMutableList() }.toMutableMap()
            val added = ArrayList<TripChange>()
            for (t in after) {
                val same = remaining[t.key]
                if (same != null && same.isNotEmpty()) same.removeAt(0) else added += TripChange(t, added = true)
            }
            val cancelled = remaining.values.flatten()
                .filter { t ->
                    val until = TripTimes.minutesUntil(t.time, nowMinuteOfDay)
                    until == null || until >= -doneGraceMin
                }
                .map { TripChange(it, added = false) }
            return (added + cancelled).sortedBy { TripTimes.minutes(it.trip.time) }
        }

        /**
         * The page shows another list, not single trips added or cancelled: many changes at once
         * (more than 3 and more than half the list), or none of the earlier trips is left (another
         * view or day was opened in YouDrive, or a new day's list came).
         */
        fun isNewList(before: List<WatchedTrip>, after: List<WatchedTrip>, changes: Int): Boolean {
            if (changes > maxOf(3, before.size / 2)) return true
            val keys = after.mapTo(HashSet()) { it.key }
            return before.size >= 2 && after.isNotEmpty() && before.none { it.key in keys }
        }

        /**
         * Trips on the page: card by card when the page reader found the trip cards ([cards]),
         * otherwise the visible text is parsed like screenshot text.
         */
        fun tripsIn(pageText: String, extractor: AddressExtractor, cards: List<String> = emptyList()): List<WatchedTrip> {
            if (cards.isNotEmpty()) YouDriveCards.parse(cards, extractor).let { if (it.isNotEmpty()) return it }
            val lines = pageText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return emptyList()
            return extractor.extract(lines).map { WatchedTrip(it.time, streetAddress(it), it) }
        }

        /**
         * The address without a leading surname: the shortest candidate whose street contains a
         * word like "Storgatan" (a street suffix with a name in front, so a lone "väg" does not
         * count). "Andersson Storgatan 14, …" → "Storgatan 14, …". Otherwise the full text.
         */
        fun streetAddress(stop: ExtractedStop): String = streetAddress(stop.candidates, stop.displayText)

        /** [streetAddress] for any stop's address candidates (also the route's stops). */
        fun streetAddress(candidates: List<String>, displayText: String): String =
            candidates.filter { c ->
                c.substringBefore(',').split(' ').any { w ->
                    val word = TextNorm.fold(w)
                    AddressExtractor.STREET_SUFFIXES.any { s -> word.length > s.length && word.endsWith(TextNorm.fold(s)) }
                }
            }.minByOrNull { it.length } ?: displayText

        /** The page asks for a login (used to tell the driver instead of showing "no trips"). */
        fun looksLoggedOut(pageText: String): Boolean {
            val t = TextNorm.fold(pageText)
            return LOGIN_WORDS.any { t.contains(it) }
        }

        private val LOGIN_WORDS = listOf("logga in", "log in", "login", "bankid", "losenord", "password", "anvandarnamn", "username")
    }
}
