package se.eldebosh.nastastopp.robo

import org.robolectric.shadows.ShadowTextToSpeech
import se.eldebosh.nastastopp.core.route.Announcements

/**
 * The last announcement as the voice said it, whole: a next-stop announcement is said in two parts
 * ("Nästa stopp …", then "Därefter …", [Announcements.parts]).
 */
val ShadowTextToSpeech.lastAnnouncement: String?
    get() {
        val said = spokenTextList
        val last = said.lastOrNull() ?: return null
        val before = said.getOrNull(said.size - 2)
        return if (last.startsWith("Därefter: ") && before != null && Announcements.isNextStops(before)) "$before $last" else last
    }
