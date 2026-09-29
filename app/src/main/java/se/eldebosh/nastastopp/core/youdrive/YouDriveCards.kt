package se.eldebosh.nastastopp.core.youdrive

import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.ExtractedStop
import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.core.parse.TripKind
import se.eldebosh.nastastopp.core.parse.TripKinds
import se.eldebosh.nastastopp.core.parse.TripTimes

/**
 * The trip cards of the YouDrive page. The page reader hands over each card's own text (a card is
 * found around its kind label), so one card's time, kind, name and address are never mixed with
 * a neighbour's. A card reads, top to bottom:
 *
 * - the left column: the scheduled time (clock icon), sometimes a second time (the booked time of
 *   a pick-up, the latest arrival of a drop-off), the kind label and a status ("Performed");
 * - the right column: the passenger's name, the address, then phone, codes, fees and notes.
 */
object YouDriveCards {

    /** Trips of the cards in page order; cards without a kind or an address are skipped. */
    fun parse(cards: List<String>, extractor: AddressExtractor): List<WatchedTrip> {
        val trips = cards.mapIndexedNotNull { i, card -> parseCard(card, i, extractor) }
        return withListTown(trips)
    }

    /** One card, or null without a kind label or an address. */
    fun parseCard(card: String, order: Int, extractor: AddressExtractor): WatchedTrip? {
        val lines = card.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val kind = lines.firstNotNullOfOrNull { TripKinds.labelIn(it) } ?: return null
        val done = lines.any { TextNorm.fold(it) in DONE_STATUS }
        var at = lines.indexOfFirst { TripKinds.labelIn(it) == null && extractor.extract(listOf(it)).isNotEmpty() }
        var stop: ExtractedStop? = if (at >= 0) extractor.extract(listOf(lines[at])).firstOrNull() else null
        if (stop == null) {
            // A place without a street number ("Sjukhuset huvudentrén,"): the line after
            // the passenger's name.
            val nameAt = lines.indexOfFirst { extractor.personName(it) != null }
            val place = lines.getOrNull(nameAt + 1)?.takeIf { nameAt >= 0 && isPlace(it) } ?: return null
            at = nameAt + 1
            stop = extractor.fromManualText(place.trim().trimEnd(',', '.', ' ')) ?: return null
        }
        // The times sit in the left column, before the name and address (a time in the notes
        // below does not count). The scheduled time comes first (YouDrive orders the route by
        // it); a second time is the booked one, which does not move when the schedule is re-planned.
        val time = { l: String -> if (TripKinds.labelIn(l) == null) TripTimes.timeIn(l) else null }
        val times = lines.take(at).mapNotNull(time).ifEmpty { lines.drop(at + 1).mapNotNull(time) }
        val name = lines.getOrNull(at - 1)?.let { extractor.personName(it) }
        val trip = stop.copy(time = times.firstOrNull(), kind = kind, name = name, sourceOrder = order)
        return WatchedTrip(trip.time, TripWatch.streetAddress(trip), trip, booked = times.getOrNull(1), done = done)
    }

    /**
     * A place without a town ("Sjukhuset huvudentrén") gets the list's most common town as
     * its first geocoder candidate, so the right hospital is found.
     */
    private fun withListTown(trips: List<WatchedTrip>): List<WatchedTrip> {
        val towns = trips.mapNotNull { it.stop?.takeIf { s -> s.parsedTown != null } }
        val common = towns.groupingBy { it.parsedTown!! }.eachCount().maxByOrNull { it.value }?.key ?: return trips
        val known = towns.first { it.parsedTown == common }.parsedTownKnown
        return trips.map { t ->
            val s = t.stop ?: return@map t
            if (s.parsedTown != null || s.parsedPostalCode != null) return@map t
            val withTown = s.candidates.map { "$it, $common" } + s.candidates
            t.copy(stop = s.copy(candidates = withTown.distinct(), parsedTown = common, parsedTownKnown = known))
        }
    }

    /** A line that can name a place: letters, no digits, not a label, time or fee. */
    private fun isPlace(line: String): Boolean =
        TextNorm.letterCount(line) >= 4 && line.none { it.isDigit() } && TripKinds.labelIn(line) == null &&
            TextNorm.fold(line) !in DONE_STATUS

    /** Statuses of a trip that is already done (the depot's "Performed" too). */
    private val DONE_STATUS = setOf("performed", "departed", "utford", "avgatt")

    /** Trips worth adding to the route: not done yet (the start point is always kept). */
    fun toAdd(trips: List<WatchedTrip>): List<ExtractedStop> =
        trips.filter { !it.done || it.stop?.kind == TripKind.PULL_OUT }.mapNotNull { it.stop }
}
