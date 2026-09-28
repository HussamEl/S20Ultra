package se.eldebosh.nastastopp.core.parse

import java.util.Locale

/**
 * Finds the scheduled time ("12:48") of each trip in the OCR lines of one screenshot.
 *
 * A time on the address line itself wins. Otherwise dispatch apps put the time either on a line
 * above each address or on a line below it; the layout is decided once per screenshot (whichever
 * direction gives more addresses a time), so one trip never takes the next trip's time.
 */
object TripTimes {

    private val TIME = Regex("(?<![\\p{N}])([01]?\\d|2[0-3])([:.])([0-5]\\d)(?![\\p{N}])")
    private val MONEY = Regex("(?<![\\p{L}])(?:KR|kr|Kr|SEK|sek)(?![\\p{L}])")
    private val UNIT_AFTER = Regex("^\\s*(?:km|mil|m|kr|sek|%)(?![\\p{L}])", RegexOption.IGNORE_CASE)
    private val TYPED = Regex("^([01]?\\d|2[0-3])[:.]([0-5]\\d)$")

    /** "12:48" / "9.05" → "12:48" / "09:05"; null if the line has no (plausible) time. */
    fun timeIn(line: String): String? {
        if (MONEY.containsMatchIn(line)) return null
        for (m in TIME.findAll(line)) {
            val (h, sep, min) = m.destructured
            // A dot is only a time separator if no unit follows ("12.50 km" is a distance).
            if (sep == "." && UNIT_AFTER.containsMatchIn(line.substring(m.range.last + 1))) continue
            return String.format(Locale.ROOT, "%02d:%s", h.toInt(), min)
        }
        return null
    }

    /**
     * The Android status bar clock is the first OCR line of most screenshots: a first line with a
     * time and (almost) no letters is ignored.
     */
    private fun isStatusBar(lines: List<String>, index: Int): Boolean =
        index == 0 && TextNorm.letterCount(lines[index]) <= 3

    /**
     * @param spans for every address, the first and last OCR line index it was read from
     *   (in reading order, not overlapping).
     * @return the time of every address, or null.
     */
    fun assign(lines: List<String>, spans: List<Pair<Int, Int>>): List<String?> {
        if (spans.isEmpty()) return emptyList()
        val lineTimes = lines.indices.map { i -> if (isStatusBar(lines, i)) null else timeIn(lines[i]) }
        val same = spans.map { (a, b) -> (a..b).firstNotNullOfOrNull { lineTimes.getOrNull(it) } }
        val above = spans.mapIndexed { k, (a, _) ->
            val lo = if (k == 0) 0 else spans[k - 1].second + 1
            (a - 1 downTo lo).firstNotNullOfOrNull { lineTimes.getOrNull(it) }
        }
        val below = spans.mapIndexed { k, (_, b) ->
            val hi = if (k == spans.lastIndex) lines.lastIndex else spans[k + 1].first - 1
            (b + 1..hi).firstNotNullOfOrNull { lineTimes.getOrNull(it) }
        }
        val needing = spans.indices.filter { same[it] == null }
        val useAbove = needing.count { above[it] != null } >= needing.count { below[it] != null }
        return spans.indices.map { same[it] ?: if (useAbove) above[it] else below[it] }
    }

    /** Validates / normalises a time typed by the driver ("9:05" → "09:05"); null if invalid. */
    fun normalizeTyped(text: String): String? {
        val m = TYPED.find(text.trim()) ?: return null
        return String.format(Locale.ROOT, "%02d:%s", m.groupValues[1].toInt(), m.groupValues[2])
    }

    /** Sort key in minutes; stops without a time sort last. */
    fun minutes(time: String?): Int = time?.let { t ->
        val parts = t.split(':')
        parts.getOrNull(0)?.toIntOrNull()?.let { h -> h * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0) }
    } ?: Int.MAX_VALUE
}
