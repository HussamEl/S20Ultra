package se.eldebosh.nastastopp.core.route

import java.util.Locale

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

    /**
     * A trip tapped on the display: its time, then its place. A trip still [coming] says where the
     * car is going ("Klockan 8 och 05 ska vi till Hamngatan 7, Skoghall"); a trip done, only its
     * time and place ("Klockan 7 och 30: Järnvägsgatan 3B, Storfors"). Never "Nästa", so it is not
     * taken for the next stop.
     */
    fun at(time: String?, name: String, coming: Boolean): Announcement {
        val parts = time?.split(':')
        val hour = parts?.getOrNull(0)?.toIntOrNull()
        val minute = parts?.getOrNull(1)?.toIntOrNull()
        if (hour == null || minute == null) {
            return if (coming) Announcement("Vi ska till $name.", "We are going to $name.") else Announcement("$name.", "$name.")
        }
        val at = if (minute == 0) "Klockan $hour" else String.format(Locale.ROOT, "Klockan %d och %02d", hour, minute)
        val atEn = String.format(Locale.ROOT, "At %d:%02d", hour, minute)
        return if (coming) Announcement("$at ska vi till $name.", "$atEn we are going to $name.") else Announcement("$at: $name.", "$atEn: $name.")
    }

    /**
     * The time, when a passenger taps the display's clock, read the way Swedish announcements read
     * a time: "Klockan är 8 och 05" ("åtta och noll fem"), "Klockan är 14" on the hour.
     */
    fun clock(hour: Int, minute: Int): Announcement {
        val sv = if (minute == 0) "Klockan är $hour." else String.format(Locale.ROOT, "Klockan är %d och %02d.", hour, minute)
        return Announcement(sv, String.format(Locale.ROOT, "The time is %d:%02d.", hour, minute))
    }

    /** Announcement for the remaining stops (current first), or "finished" if none remain. */
    fun forRemaining(spokenNames: List<String>, withEnglish: Boolean): Announcement =
        if (spokenNames.isEmpty()) finished(withEnglish) else nextStops(spokenNames[0], spokenNames.getOrNull(1), withEnglish)
}
