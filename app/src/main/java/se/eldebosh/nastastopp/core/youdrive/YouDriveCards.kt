package se.eldebosh.nastastopp.core.youdrive

import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.ExtractedStop
import se.eldebosh.nastastopp.core.parse.Places
import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.core.parse.TripKinds
import se.eldebosh.nastastopp.core.parse.TripTimes

/**
 * The trip cards of the YouDrive page. The page reader hands over each card's own text (a card is
 * found around its kind label), so one card's time, kind, name and address are never mixed with
 * a neighbour's. A card reads, top to bottom:
 *
 * - the left column: the scheduled time (clock icon), sometimes a second time (the booked time of
 *   a pick-up, the latest arrival of a drop-off), the kind label and a status ("Performed");
 * - the right column: always the passenger's name first (none on the depot's cards), then the
 *   address, phone, codes, fees and notes.
 */
object YouDriveCards {

    /** Trips of the cards in page order; cards without a kind or an address are skipped. */
    fun parse(cards: List<String>, extractor: AddressExtractor): List<WatchedTrip> =
        cards.mapIndexedNotNull { i, card -> parseCard(card, i, extractor) }

    /** One card, or null without a kind label or an address. */
    fun parseCard(card: String, order: Int, extractor: AddressExtractor): WatchedTrip? {
        val lines = card.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val kind = lines.firstNotNullOfOrNull { TripKinds.labelIn(it) } ?: return null
        val done = lines.any { TextNorm.fold(it) in DONE_STATUS }
        var at = lines.indexOfFirst { TripKinds.labelIn(it) == null && extractor.extract(listOf(it)).isNotEmpty() }
        var stop: ExtractedStop? = if (at >= 0) extractor.extract(listOf(lines[at])).firstOrNull() else null
        if (stop == null) {
            // A place without a street number ("Sjukhuset huvudentrén,"): the line after
            // the passenger's name. It has no town: the geocoder finds it in one town only, or the
            // driver adds the town ([Places.known] knows a few).
            val nameAt = lines.indexOfFirst { extractor.personName(it, strict = false) != null }
            val place = lines.getOrNull(nameAt + 1)?.takeIf { nameAt >= 0 && isPlace(it) } ?: return null
            at = nameAt + 1
            stop = extractor.fromManualText(place.trim().trimEnd(',', '.', ' '))?.let { it.copy(place = it.displayText) } ?: return null
        } else {
            // The place written before the street ("Provby Vårdcentral Strandvägen 3"): the card's
            // name is on its own line, so this is never the passenger's name.
            stop = stop.copy(place = placeBeforeStreet(stop))
        }
        stop = withKnownTown(stop)
        // The times sit in the left column, before the name and address (a time in the notes
        // below does not count). The scheduled time comes first (YouDrive orders the route by
        // it); a second time is the booked one, which does not move when the schedule is re-planned.
        val time = { l: String -> if (TripKinds.labelIn(l) == null) TripTimes.timeIn(l) else null }
        val times = lines.take(at).mapNotNull(time).ifEmpty { lines.drop(at + 1).mapNotNull(time) }
        // The card's first line (the right column's top) is the passenger's name.
        val name = lines.take(at).firstNotNullOfOrNull { extractor.personName(it, strict = false) }
        val trip = stop.copy(time = times.firstOrNull(), kind = kind, name = name, sourceOrder = order, youDriveDone = done)
        return WatchedTrip(trip.time, TripWatch.streetAddress(trip), trip, booked = times.getOrNull(1), done = done)
    }

    /** The words before the street ("Provby Vårdcentral" in "Provby Vårdcentral Strandvägen 3, 652 25 Karlstad"), or null. */
    private fun placeBeforeStreet(stop: ExtractedStop): String? {
        val street = TripWatch.streetAddress(stop).substringBefore(',').trim()
        val full = stop.candidates.first().substringBefore(',').trim()
        if (street.isEmpty() || !full.endsWith(street)) return null
        return full.removeSuffix(street).trim().takeIf { TextNorm.letterCount(it) >= 3 }
    }

    /**
     * A well-known place written without a town ("Centralsjukhuset huvudentrén,") is asked for in
     * its own town. Another one keeps no town: it is never given the town of the list's other trips.
     */
    private fun withKnownTown(stop: ExtractedStop): ExtractedStop {
        if (stop.parsedTown != null || stop.parsedPostalCode != null) return stop
        val known = Places.known(stop.place) ?: return stop
        return stop.copy(candidates = (listOf(known.candidate) + stop.candidates).distinct(), parsedTown = known.town, parsedTownKnown = true)
    }

    /** A line that can name a place: letters, no digits, not a label, time or fee. */
    private fun isPlace(line: String): Boolean =
        TextNorm.letterCount(line) >= 4 && line.none { it.isDigit() } && TripKinds.labelIn(line) == null &&
            TextNorm.fold(line) !in DONE_STATUS

    /** Statuses of a trip that is already done (the depot's "Performed" too). */
    private val DONE_STATUS = setOf("performed", "departed", "utford", "avgatt")

    /**
     * Trips to add to the route: all of them, the done ones marked ([ExtractedStop.youDriveDone]).
     * A driver may mark trips done in YouDrive before reaching them, so they stay in the route
     * until Next passes them.
     */
    fun toAdd(trips: List<WatchedTrip>): List<ExtractedStop> = trips.mapNotNull { it.stop }
}
