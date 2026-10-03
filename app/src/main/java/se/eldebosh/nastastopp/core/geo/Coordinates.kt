package se.eldebosh.nastastopp.core.geo

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * A point the driver types or pastes for a stop's entrance: decimal degrees ("59.381234,
 * 13.501234"), degrees, minutes and seconds as Google Maps writes them (59°22'48.0"N
 * 13°30'00.0"E), or a Google Maps link holding a point ("…/@59.527,13.127,17z",
 * "…?query=59.381234%2C13.501234"). Only a point in Sweden is taken.
 */
object Coordinates {

    /** The point in [text] (latitude, longitude), or null. */
    fun parse(text: String): Pair<Double, Double>? {
        val decoded = runCatching { URLDecoder.decode(text.replace("+", " "), StandardCharsets.UTF_8.name()) }.getOrDefault(text)
        val point = dms(decoded) ?: decimal(decoded) ?: return null
        return point.takeIf { (lat, lng) -> lat in LAT_MIN..LAT_MAX && lng in LNG_MIN..LNG_MAX }
    }

    /** "59.381234, 13.501234": six decimals, a point and a comma, whatever the phone's language. */
    fun format(lat: Double, lng: Double): String = String.format(Locale.ROOT, "%.6f, %.6f", lat, lng)

    private fun dms(text: String): Pair<Double, Double>? {
        val parts = DMS.findAll(text).toList()
        if (parts.size < 2) return null
        val values = parts.take(2).map { m ->
            val (deg, min, sec, hemi) = m.destructured
            val v = deg.toDouble() + (min.toDoubleOrNull() ?: 0.0) / 60 + (sec.replace(',', '.').toDoubleOrNull() ?: 0.0) / 3600
            hemi.uppercase() to v
        }
        val lat = values.firstOrNull { it.first == "N" || it.first == "S" } ?: return null
        val lng = values.firstOrNull { it.first == "E" || it.first == "W" || it.first == "Ö" || it.first == "O" } ?: return null
        return (if (lat.first == "S") -lat.second else lat.second) to (if (lng.first == "W") -lng.second else lng.second)
    }

    private fun decimal(text: String): Pair<Double, Double>? {
        // Two numbers with a decimal point, side by side ("59.5, 13.1", "@59.5,13.1", "59.5 13.1").
        DECIMAL_DOT.find(text)?.let { m -> return m.groupValues[1].toDouble() to m.groupValues[2].toDouble() }
        // The Swedish way, a decimal comma, the two numbers apart ("59,381234 13,501234").
        DECIMAL_COMMA.find(text)?.let { m ->
            return m.groupValues[1].replace(',', '.').toDouble() to m.groupValues[2].replace(',', '.').toDouble()
        }
        return null
    }

    private val DMS = Regex("""(\d{1,3})\s*[°º]\s*(?:(\d{1,2})\s*['′’]\s*)?(?:(\d{1,2}(?:[.,]\d+)?)\s*(?:["″”]|'')\s*)?([NSEWÖO])""", RegexOption.IGNORE_CASE)
    private val DECIMAL_DOT = Regex("""(-?\d{1,2}\.\d{3,})\s*[,;\s]\s*(-?\d{1,3}\.\d{3,})""")
    private val DECIMAL_COMMA = Regex("""(\d{1,2},\d{3,})\s+(\d{1,3},\d{3,})""")

    /** Sweden, with a margin. */
    private const val LAT_MIN = 55.0
    private const val LAT_MAX = 69.5
    private const val LNG_MIN = 10.5
    private const val LNG_MAX = 24.5
}
