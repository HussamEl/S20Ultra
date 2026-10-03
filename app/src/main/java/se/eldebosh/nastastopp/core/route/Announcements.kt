package se.eldebosh.nastastopp.core.route

import java.util.Locale

/** A spoken announcement: Swedish always, English optionally repeated after it. */
data class Announcement(val swedish: String, val english: String?)

/**
 * Builds the spoken phrases. Inputs are spoken names (area/town only — see GeoLogic.spokenName).
 *
 * A time is read without a leading zero, its minutes counted: "Klockan 9 och 8 minuter" (09:08),
 * "Klockan 1 och 7 minuter" (01:07), "Klockan 9" on the hour; never "noll åtta".
 */
object Announcements {

    /**
     * "Nästa stopp: [a]. Klockan …. Därefter: [b]. Klockan …." Each stop is said with its trip's
     * time after its address ([aTime], [bTime], "08:05"; left out when unknown).
     */
    fun nextStops(a: String, b: String?, withEnglish: Boolean, aTime: String? = null, bTime: String? = null): Announcement {
        val sv = buildString {
            append("$NEXT: $a.")
            spokenTime(aTime)?.let { append(" $it.") }
            if (b != null) {
                append(" $THEN: $b.")
                spokenTime(bTime)?.let { append(" $it.") }
            } else {
                append(" Det är sista stoppet.")
            }
        }
        val en = if (!withEnglish) null else buildString {
            append("Next stop: $a")
            englishTime(aTime)?.let { append(", at $it") }
            append('.')
            if (b != null) {
                append(" Then: $b")
                englishTime(bTime)?.let { append(", at $it") }
                append('.')
            } else {
                append(" This is the last stop.")
            }
        }
        return Announcement(sv, en)
    }

    fun finished(withEnglish: Boolean) = Announcement("Rutten är klar.", if (withEnglish) "The route is finished." else null)

    /**
     * A trip tapped on the display: its time, then its place. A trip still [coming] says where the
     * car is going ("Klockan 8 och 5 minuter ska vi till Hamngatan 7, Skoghall"); a trip done, only
     * its time and place ("Klockan 7 och 30 minuter: Järnvägsgatan 3B, Storfors"). Never "Nästa",
     * so it is not taken for the next stop.
     */
    fun at(time: String?, name: String, coming: Boolean): Announcement {
        val at = spokenTime(time)
        val atEn = englishTime(time)?.let { "At $it" }
        if (at == null || atEn == null) {
            return if (coming) Announcement("Vi ska till $name.", "We are going to $name.") else Announcement("$name.", "$name.")
        }
        return if (coming) Announcement("$at ska vi till $name.", "$atEn we are going to $name.") else Announcement("$at: $name.", "$atEn: $name.")
    }

    /** The time, when a passenger taps the display's clock: "Klockan är 8 och 5 minuter", "Klockan är 14" on the hour. */
    fun clock(hour: Int, minute: Int): Announcement =
        Announcement("Klockan är ${hourAndMinutes(hour, minute)}.", String.format(Locale.ROOT, "The time is %d:%02d.", hour, minute))

    /**
     * The next stop's passenger's last name, said only when it is tapped on the passenger display
     * (never in an announcement).
     */
    fun passenger(lastName: String) = Announcement("$lastName.", "$lastName.")

    /** Announcement for the remaining stops (current first, with their [times]), or "finished" if none remain. */
    fun forRemaining(spokenNames: List<String>, withEnglish: Boolean, times: List<String?> = emptyList()): Announcement =
        if (spokenNames.isEmpty()) {
            finished(withEnglish)
        } else {
            nextStops(spokenNames[0], spokenNames.getOrNull(1), withEnglish, times.getOrNull(0), times.getOrNull(1))
        }

    /** "Klockan 9 och 8 minuter" for "09:08"; null without a time. */
    fun spokenTime(time: String?): String? = parse(time)?.let { (h, m) -> "Klockan ${hourAndMinutes(h, m)}" }

    /**
     * How a next-stop announcement is said, in step with the passenger display: "Nästa stopp …"
     * and "Därefter …" apart, with [GAP_MS] of silence between them while the next stop gives way
     * to the trip after it, and [LEAD_MS] before the first while the screen before it goes and the
     * next stop comes. Any other announcement is one part, said at once.
     */
    fun parts(swedish: String): List<String> {
        if (!isNextStops(swedish)) return listOf(swedish)
        val then = swedish.indexOf(" $THEN: ")
        return if (then < 0) listOf(swedish) else listOf(swedish.substring(0, then), swedish.substring(then + 1))
    }

    /** Whether [swedish] is a next-stop announcement (said in [parts], the display in step). */
    fun isNextStops(swedish: String?): Boolean = swedish?.startsWith("$NEXT: ") == true

    /** Silence before a next-stop announcement, while the screen before it goes and the next stop comes into view. */
    const val LEAD_MS = 1_000L

    /** Silence between "Nästa stopp …" and "Därefter …": the next stop stays a moment, fades, and the trip after it comes into view. */
    const val GAP_MS = 2_200L

    private const val NEXT = "Nästa stopp"
    private const val THEN = "Därefter"

    private fun hourAndMinutes(hour: Int, minute: Int): String = when (minute) {
        0 -> "$hour"
        1 -> "$hour och 1 minut"
        else -> "$hour och $minute minuter"
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
