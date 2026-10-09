package se.eldebosh.nastastopp.core.route

import java.util.Locale

/** A spoken announcement: Swedish always, English optionally repeated after it. */
data class Announcement(val swedish: String, val english: String?)

/**
 * Builds the spoken phrases. Inputs are spoken names (area/town only — see GeoLogic.spokenName).
 *
 * A time is read as a clock shows it, in words: "Klockan åtta noll två" (08:02), "Klockan nio
 * trettio" (09:30), "Klockan nio" on the hour; the hour without a leading zero, never "minuter".
 */
object Announcements {

    /**
     * "Nästa stopp: Klockan åtta noll två. [a]. Därefter: Klockan nio trettio. [b]." Each stop's
     * trip time ([aTime], [bTime], "08:02") comes first, as the display shows it first; left out
     * when unknown.
     */
    fun nextStops(a: String, b: String?, withEnglish: Boolean, aTime: String? = null, bTime: String? = null): Announcement {
        val sv = buildString {
            append(said(NEXT, aTime, a))
            if (b != null) append(" ").append(said(THEN, bTime, b)) else append(" Det är sista stoppet.")
        }
        val en = if (!withEnglish) null else buildString {
            append("Next stop: ")
            englishTime(aTime)?.let { append("$it, ") }
            append("$a.")
            if (b != null) {
                append(" Then: ")
                englishTime(bTime)?.let { append("$it, ") }
                append("$b.")
            } else {
                append(" This is the last stop.")
            }
        }
        return Announcement(sv, en)
    }

    /** "[word]: Klockan åtta noll två. [name]." (no time: "[word]: [name].") */
    private fun said(word: String, time: String?, name: String): String =
        spokenTime(time)?.let { "$word: $it. $name." } ?: "$word: $name."

    fun finished(withEnglish: Boolean) = Announcement("Rutten är klar.", if (withEnglish) "The route is finished." else null)

    /**
     * A trip tapped: its time, then its place. A coming trip shown under its [word] ("Därefter",
     * "Sen") is said like the announcement: "Sen: Klockan åtta noll fem. Hamngatan 7, Skoghall."; a
     * trip done, its time and its place: "Klockan sju trettio. Järnvägsgatan 3B, Storfors." A
     * coming trip without a word (the phone's panel) says where the car is going: "Klockan åtta
     * noll fem ska vi till Hamngatan 7, Skoghall." Never "Nästa", so it is not taken for the next stop.
     */
    fun at(time: String?, name: String, coming: Boolean, word: String? = null): Announcement {
        // A coming trip shown with its word ("Därefter", "Sen"): said like the announcement.
        if (coming && word != null) {
            return Announcement(said(word, time, name), listOfNotNull(englishTime(time), name).joinToString(", ") + ".")
        }
        val at = spokenTime(time)
        val atEn = englishTime(time)?.let { "At $it" }
        if (at == null || atEn == null) {
            return if (coming) Announcement("Vi ska till $name.", "We are going to $name.") else Announcement("$name.", "$name.")
        }
        return if (coming) Announcement("$at ska vi till $name.", "$atEn we are going to $name.") else Announcement("$at. $name.", "$atEn: $name.")
    }

    /** The time, when a passenger taps the display's clock: "Klockan är åtta noll fem", "Klockan är fjorton" on the hour. */
    fun clock(hour: Int, minute: Int): Announcement =
        Announcement("Klockan är ${words(hour, minute)}.", String.format(Locale.ROOT, "The time is %d:%02d.", hour, minute))

    /**
     * The next stop's passenger's last name, said only when it is tapped on the passenger display
     * (never in an announcement).
     */
    fun passenger(lastName: String) = Announcement("$lastName.", "$lastName.")

    /** "åtta noll två" for "08:02", "nio trettio" for "09:30", "nio" for "09:00"; null without a time. */
    fun timeWords(time: String?): String? = parse(time)?.let { (h, m) -> words(h, m) }

    /** "Klockan åtta noll två" for "08:02"; null without a time. */
    fun spokenTime(time: String?): String? = timeWords(time)?.let { "Klockan $it" }

    /**
     * One step of what a passenger display shows large while it is said ([steps]): a trip's
     * [time] ("Nästa stopp: Klockan åtta noll två."), or its address; [stop] counts the trips said
     * (0 the first, 1 "Därefter").
     */
    data class Step(val text: String, val time: Boolean, val stop: Int)

    /**
     * How an announcement the display shows ([isShow]: the next stops, a tapped trip) is said, in
     * step with it: each trip's time, then its address, apart. [silenceBefore] gives the silence
     * before each while the display moves on. Anything else is one step, said at once.
     */
    fun steps(swedish: String): List<Step> {
        if (!isShow(swedish)) return listOf(Step(swedish, time = false, stop = 0))
        // Where each trip begins: the first at once, the others at their word.
        val starts = sortedSetOf(0)
        for (word in listOf(THEN, LATER)) {
            var i = swedish.indexOf(" $word: ")
            while (i >= 0) {
                starts += i + 1
                i = swedish.indexOf(" $word: ", i + 1)
            }
        }
        val begins = starts.toList()
        return begins.flatMapIndexed { n, from ->
            val trip = swedish.substring(from, begins.getOrNull(n + 1)?.let { it - 1 } ?: swedish.length).trim()
            val end = timeEnd(trip)
            val address = end?.let { trip.substring(it).trim() }
            when {
                end == null -> listOf(Step(trip, time = false, stop = n))
                address.isNullOrEmpty() -> listOf(Step(trip, time = true, stop = n))
                else -> listOf(Step(trip.substring(0, end), time = true, stop = n), Step(address, time = false, stop = n))
            }
        }
    }

    /** The texts of [steps], in order. */
    fun parts(swedish: String): List<String> = steps(swedish).map { it.text }

    /**
     * The silence before step [k] of [steps]: [LEAD_MS] before the first, while the screen before
     * goes and the trip comes; [TO_ADDRESS_MS] before an address after its time, while the time
     * gives way to it; [TO_NEXT_MS] before the next trip, while the address stays, goes and the
     * next trip comes.
     */
    fun silenceBefore(steps: List<Step>, k: Int): Long = when {
        k == 0 -> LEAD_MS
        steps[k].stop == steps[k - 1].stop -> TO_ADDRESS_MS
        else -> TO_NEXT_MS
    }

    /** Whether [swedish] is a next-stop announcement. */
    fun isNextStops(swedish: String?): Boolean = swedish?.startsWith("$NEXT: ") == true

    /**
     * Whether the passenger display shows [swedish] as it is said, step by step ([steps]): the next
     * stops, or a trip tapped ("Därefter: …", "Sen: …", a trip done "Klockan sju trettio. …").
     */
    fun isShow(swedish: String?): Boolean {
        if (swedish == null) return false
        return swedish.startsWith("$NEXT: ") || swedish.startsWith("$THEN: ") || swedish.startsWith("$LATER: ") || timeEnd(swedish) != null
    }

    /**
     * Where the time at the head of a trip's text ends ("Därefter: Klockan åtta noll två." or
     * "Klockan sju trettio."), just after its full stop; null when the trip starts with no time.
     * The time's words are only numbers, so an address or "Klockan är …" is never taken for one.
     */
    private fun timeEnd(trip: String): Int? {
        val head = listOf(NEXT, THEN, LATER).firstOrNull { trip.startsWith("$it: ") }?.let { it.length + 2 } ?: 0
        if (!trip.startsWith("$CLOCK ", head)) return null
        val stop = trip.indexOf('.', head)
        if (stop < 0) return null
        val said = trip.substring(head + CLOCK.length + 1, stop).split(' ')
        return if (said.isNotEmpty() && said.all { it in NUMBER_WORDS }) stop + 1 else null
    }

    /** Silence before an announcement the display shows: the screen before goes, the trip comes in and its time starts to grow. */
    const val LEAD_MS = 2_100L

    /** Silence between a trip's time and its address: the time stays a moment, goes, and the address starts to grow. */
    const val TO_ADDRESS_MS = 2_100L

    /** Silence between a trip's address and the next trip: the address stays two seconds, swells, turns away, the next trip comes and its time starts to grow. */
    const val TO_NEXT_MS = 5_300L

    private const val NEXT = "Nästa stopp"
    private const val THEN = "Därefter"
    private const val LATER = "Sen"
    private const val CLOCK = "Klockan"

    private val ONES = listOf(
        "noll", "ett", "två", "tre", "fyra", "fem", "sex", "sju", "åtta", "nio",
        "tio", "elva", "tolv", "tretton", "fjorton", "femton", "sexton", "sjutton", "arton", "nitton",
    )
    private val TENS = listOf("", "", "tjugo", "trettio", "fyrtio", "femtio")

    /** 0–59 in Swedish words: "åtta", "tjugoett", "femtionio". */
    private fun number(n: Int): String = if (n < 20) ONES[n] else TENS[n / 10] + if (n % 10 == 0) "" else ONES[n % 10]

    private val NUMBER_WORDS: Set<String> = (0..59).map(::number).toSet()

    /** As a clock shows it, in words: "åtta noll två" (08:02), "fjorton trettio", "nio" on the hour. */
    private fun words(hour: Int, minute: Int): String = when {
        minute == 0 -> number(hour)
        minute < 10 -> "${number(hour)} noll ${number(minute)}"
        else -> "${number(hour)} ${number(minute)}"
    }

    private fun englishTime(time: String?): String? = parse(time)?.let { (h, m) -> String.format(Locale.ROOT, "%d:%02d", h, m) }

    private fun parse(time: String?): Pair<Int, Int>? {
        val parts = time?.split(':') ?: return null
        val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.trim()?.take(2)?.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }
}
