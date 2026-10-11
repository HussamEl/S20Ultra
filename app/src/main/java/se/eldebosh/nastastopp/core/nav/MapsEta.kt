package se.eldebosh.nastastopp.core.nav

import kotlinx.serialization.Serializable

/** Google Maps' remaining travel time to its next destination, and the distance when it says one. */
@Serializable
data class DisplayEta(val minutes: Int, val meters: Int? = null)

/**
 * Reads the remaining time from the text of Google Maps' navigation notification ("12 min ·
 * 5,3 km · 12:48", "1 h 5 min", "ankomst 12:48", Arabic digits too). Only the numbers are taken:
 * the text also names streets, and it is never kept or logged.
 */
object MapsEta {

    /** The remaining time in [texts] (the notification's lines), or null when it shows none. */
    fun parse(texts: List<String>, nowMinuteOfDay: Int): DisplayEta? {
        val text = texts.joinToString("  ") { western(it) }
        val meters = distance(text)
        duration(text)?.let { return DisplayEta(it, meters) }
        // No duration: the arrival time ("ETA 12:48", "ankomst 12:48").
        val arrival = ARRIVAL.find(text) ?: return null
        val at = arrival.groupValues[1].toInt() * 60 + arrival.groupValues[2].toInt()
        var left = at - nowMinuteOfDay
        if (left < -12 * 60) left += 24 * 60
        return if (left in 0..(12 * 60)) DisplayEta(left, meters) else null
    }

    private fun duration(text: String): Int? {
        val hours = HOURS.find(text)?.groupValues?.get(1)?.toIntOrNull()
        val minutes = MINUTES.find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (hours == null && minutes == null) return null
        return (hours ?: 0) * 60 + (minutes ?: 0)
    }

    private fun distance(text: String): Int? {
        val m = DISTANCE.find(text) ?: return null
        val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return if (m.groupValues[2].lowercase() == "km" || m.groupValues[2] == "كم") (value * 1000).toInt() else value.toInt()
    }

    /** Arabic-Indic and Persian digits as 0–9, and the Arabic decimal sign as a point. */
    private fun western(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                '\u066B' -> '.'
                else -> c
            },
        )
    }

    private val HOURS = Regex("""(\d{1,2})\s*(?:h|hr|hrs|hour|hours|tim|timme|timmar|t|ساعة|ساعات|س)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d{1,3})\s*(?:min|mins|minute|minutes|minut|minuter|دقيقة|دقائق|د)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val DISTANCE = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m|كم|م)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val ARRIVAL = Regex("""\b([01]?\d|2[0-3])[:.]([0-5]\d)\b""")
}
