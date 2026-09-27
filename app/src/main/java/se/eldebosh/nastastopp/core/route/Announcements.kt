package se.eldebosh.nastastopp.core.route

/** A spoken announcement: Swedish always, English optionally repeated after it. */
data class Announcement(val swedish: String, val english: String?)

/**
 * Builds the spoken phrases. Inputs are spoken names (area/town only — see GeoLogic.spokenName).
 */
object Announcements {

    fun nextStops(a: String, b: String?, withEnglish: Boolean): Announcement {
        val sv = if (b != null) "Nästa stopp: $a. Därefter: $b." else "Nästa stopp: $a. Det är sista stoppet."
        val en = if (!withEnglish) null else if (b != null) "Next stop: $a. Then: $b." else "Next stop: $a. This is the last stop."
        return Announcement(sv, en)
    }

    fun finished(withEnglish: Boolean) = Announcement("Rutten är klar.", if (withEnglish) "The route is finished." else null)

    /** Announcement for the remaining stops (current first), or "finished" if none remain. */
    fun forRemaining(spokenNames: List<String>, withEnglish: Boolean): Announcement =
        if (spokenNames.isEmpty()) finished(withEnglish) else nextStops(spokenNames[0], spokenNames.getOrNull(1), withEnglish)
}
