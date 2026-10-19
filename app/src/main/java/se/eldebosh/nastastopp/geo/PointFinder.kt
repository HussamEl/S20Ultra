package se.eldebosh.nastastopp.geo

import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.Places
import se.eldebosh.nastastopp.route.PlaceMemory
import se.eldebosh.nastastopp.route.withTown

/**
 * The point of any address the driver writes (the find tool, 323–333, on the phone and the
 * tablet): read as a typed address is ([AddressExtractor.fromManualText]), with the town a
 * well-known place has ([Places.withKnownTown]) or the driver once gave the place ([withTown]), then
 * looked for as a stop is ([Geocoding.locate]: Lantmäteriet's register first, then the geocoder;
 * only where the written postcode or town is, never a guessed town).
 *
 * It never adds a stop or touches the route, the history or the address log, and keeps nothing:
 * neither the text nor the answer is stored or logged.
 */
class PointFinder(
    private val extractor: AddressExtractor,
    private val places: PlaceMemory,
    /** [Geocoding.locate] (tests give their own). */
    private val locate: suspend (candidates: List<String>, postal: String?, town: String?) -> LocateResult?,
) {
    /** What a search found. */
    sealed interface Answer {
        /** [address] as found (the register's or the geocoder's line), at [lat], [lng]. */
        data class Found(val address: String, val lat: Double, val lng: Double) : Answer

        /** No one place for the text: the postcode or town may be missing (a town is never guessed). */
        data object NotFound : Answer

        /** The geocoder could not answer (offline, busy): asked again, it may. */
        data object NoAnswer : Answer
    }

    /** Looks for [text]; call on the main thread, as the route's own lookups are. */
    suspend fun find(text: String): Answer {
        val typed = extractor.fromManualText(text.trim()) ?: return Answer.NotFound
        val asked = places.withTown(Places.withKnownTown(typed, typed.place ?: typed.displayText), extractor)
        val located = try {
            locate(asked.candidates, asked.parsedPostalCode, asked.parsedTown)
        } catch (_: GeocoderFailed) {
            return Answer.NoAnswer
        } ?: return Answer.NotFound
        val found = located.result
        return Answer.Found(found.addressLine?.takeIf { it.isNotBlank() } ?: located.candidate, found.lat, found.lng)
    }
}
