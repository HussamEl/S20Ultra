package se.eldebosh.nastastopp.core.route

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Builds Google Maps URLs (no key, nothing billed: they open Google's own app or site):
 * - directions through a batch of stops:
 *   https://www.google.com/maps/dir/?api=1&destination=…&waypoints=a|b|c&travelmode=driving&dir_action=navigate
 *   At most [MAX_STOPS_PER_LAUNCH] stops per launch (9 waypoints + destination);
 * - navigation to one stop ([navigateUrl]), by its point when it has one ("59.381234,13.501234"),
 *   so Google does not search the address and pick a similar one;
 * - Google's street photos at a point ([streetViewUrl]) and a point on the map ([pointUrl]).
 */
object MapsUrlBuilder {
    const val MAX_STOPS_PER_LAUNCH = 10
    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    fun batches(stops: List<String>): List<List<String>> = stops.chunked(MAX_STOPS_PER_LAUNCH)

    /** URL for the first batch (≤ 10) of [stops]. */
    fun buildUrl(stops: List<String>): String {
        require(stops.isNotEmpty()) { "no stops" }
        val batch = stops.take(MAX_STOPS_PER_LAUNCH)
        val destination = batch.last()
        val waypoints = batch.dropLast(1)
        return buildString {
            append("https://www.google.com/maps/dir/?api=1")
            append("&destination=").append(encode(destination))
            if (waypoints.isNotEmpty()) {
                append("&waypoints=")
                append(waypoints.joinToString("%7C") { encode(it) })
            }
            append("&travelmode=driving&dir_action=navigate")
        }
    }

    /** Navigation to one place: a point ([point]) or an address. */
    fun navigateUrl(destination: String): String =
        "https://www.google.com/maps/dir/?api=1&destination=" + encode(destination) + "&travelmode=driving&dir_action=navigate"

    /** Google's street photos nearest a point. */
    fun streetViewUrl(lat: Double, lng: Double): String =
        "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=" + encode(point(lat, lng))

    /** A point on Google's map. */
    fun pointUrl(lat: Double, lng: Double): String = "https://www.google.com/maps/search/?api=1&query=" + encode(point(lat, lng))

    /** "59.381234,13.501234": a point as Google Maps takes it, whatever the phone's language. */
    fun point(lat: Double, lng: Double): String = String.format(Locale.ROOT, "%.6f,%.6f", lat, lng)

    /** RFC 3986 style encoding (spaces as %20). A literal "|" in an address would split waypoints. */
    fun encode(value: String): String {
        val cleaned = value.replace('|', ' ').replace(Regex("\\s+"), " ").trim()
        return URLEncoder.encode(cleaned, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%7E", "~")
    }
}
