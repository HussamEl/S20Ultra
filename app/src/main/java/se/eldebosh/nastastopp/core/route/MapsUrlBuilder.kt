package se.eldebosh.nastastopp.core.route

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Builds Google Maps "directions" URLs:
 * https://www.google.com/maps/dir/?api=1&destination=…&waypoints=a|b|c&travelmode=driving&dir_action=navigate
 * At most [MAX_STOPS_PER_LAUNCH] stops per launch (9 waypoints + destination).
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

    /** RFC 3986 style encoding (spaces as %20). A literal "|" in an address would split waypoints. */
    fun encode(value: String): String {
        val cleaned = value.replace('|', ' ').replace(Regex("\\s+"), " ").trim()
        return URLEncoder.encode(cleaned, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%7E", "~")
    }
}
