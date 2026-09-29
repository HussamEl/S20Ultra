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
     * The times around every address span (see [Nearby]); the status bar clock is skipped.
     * [spans] holds, for every address, the first and last OCR line index it was read from (in
     * reading order, not overlapping).
     */
    fun timesNearby(lines: List<String>, spans: List<Pair<Int, Int>>): Nearby<String> =
        nearby(lines, spans) { i -> if (isStatusBar(lines, i)) null else timeIn(lines[i]) }

    /**
     * For every address span, the value ([valueOf] a line index) on its own lines, and the one on
     * the nearest line above and below it, never past a neighbouring address.
     */
    fun <T : Any> nearby(lines: List<String>, spans: List<Pair<Int, Int>>, valueOf: (Int) -> T?): Nearby<T> {
        val values = lines.indices.map(valueOf)
        val same = spans.map { (a, b) -> (a..b).firstNotNullOfOrNull { values.getOrNull(it) } }
        val upAt = spans.mapIndexed { k, (a, _) ->
            val lo = if (k == 0) 0 else spans[k - 1].second + 1
            (a - 1 downTo lo).firstOrNull { values.getOrNull(it) != null }
        }
        val downAt = spans.mapIndexed { k, (_, b) ->
            val hi = if (k == spans.lastIndex) lines.lastIndex else spans[k + 1].first - 1
            (b + 1..hi).firstOrNull { values.getOrNull(it) != null }
        }
        return Nearby(same, upAt.map { it?.let(values::get) }, downAt.map { it?.let(values::get) }, upAt, downAt)
    }

    /**
     * Values found on, above and below each address span; the caller picks one direction for all.
     * [upAt] / [downAt] are the line indices of the values above / below.
     */
    class Nearby<T : Any>(
        private val same: List<T?>,
        private val up: List<T?>,
        private val down: List<T?>,
        private val upAt: List<Int?>,
        private val downAt: List<Int?>,
    ) {
        /** How many spans without a value of their own find one [above] (or below). */
        fun count(above: Boolean): Int = same.indices.count { same[it] == null && (if (above) up[it] else down[it]) != null }

        /**
         * Each span's value: its own, else the one in the chosen direction ([above] or below), else
         * the one on the other side when the neighbouring span does not take that same line. The
         * last case is YouDrive's start point: its time sits beside (so, read before) its address
         * while every trip card has its time below.
         */
        fun values(above: Boolean): List<T?> = same.indices.map { i ->
            same[i] ?: (if (above) up[i] else down[i]) ?: if (above) down[i]?.takeUnless { takenBelow(i) } else up[i]?.takeUnless { takenAbove(i) }
        }

        /** The value below span [i] is the next span's value above it (the next span takes it). */
        private fun takenBelow(i: Int) = i < same.lastIndex && same[i + 1] == null && upAt[i + 1] == downAt[i]

        /** The value above span [i] is the previous span's value below it (the previous span takes it). */
        private fun takenAbove(i: Int) = i > 0 && same[i - 1] == null && downAt[i - 1] == upAt[i]
    }

    /** Validates / normalises a time typed by the driver ("9:05" → "09:05"); null if invalid. */
    fun normalizeTyped(text: String): String? {
        val m = TYPED.find(text.trim()) ?: return null
        return String.format(Locale.ROOT, "%02d:%s", m.groupValues[1].toInt(), m.groupValues[2])
    }

    /**
     * Minutes from [nowMinuteOfDay] until the scheduled [time] (negative = late), or null without
     * a time. Wraps around midnight: a trip counts as late for at most [MAX_LATE_MIN] minutes,
     * otherwise it is the next occurrence (e.g. "00:10" at 23:50 is in 20 minutes).
     */
    fun minutesUntil(time: String?, nowMinuteOfDay: Int): Int? {
        val t = minutes(time)
        if (t == Int.MAX_VALUE) return null
        var d = t - nowMinuteOfDay
        if (d < -MAX_LATE_MIN) d += DAY
        if (d > DAY - MAX_LATE_MIN) d -= DAY
        return d
    }

    /** How the next trip's scheduled time compares with now. */
    fun level(minutesUntil: Int): TimeLevel = when {
        minutesUntil < 0 -> TimeLevel.LATE
        minutesUntil <= SOON_MIN -> TimeLevel.SOON
        else -> TimeLevel.AHEAD
    }

    /** Within this many minutes of the scheduled time the trip counts as "soon". */
    const val SOON_MIN = 5
    private const val DAY = 24 * 60
    private const val MAX_LATE_MIN = 8 * 60

    /** Sort key in minutes; stops without a time sort last. */
    fun minutes(time: String?): Int = time?.let { t ->
        val parts = t.split(':')
        parts.getOrNull(0)?.toIntOrNull()?.let { h -> h * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0) }
    } ?: Int.MAX_VALUE
}

/** Scheduled time vs. now: more than [TripTimes.SOON_MIN] minutes ahead, soon, or late. */
enum class TimeLevel { AHEAD, SOON, LATE }
