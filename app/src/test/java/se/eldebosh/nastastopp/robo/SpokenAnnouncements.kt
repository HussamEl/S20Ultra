package se.eldebosh.nastastopp.robo

import org.robolectric.shadows.ShadowTextToSpeech
import se.eldebosh.nastastopp.core.route.Announcements

/**
 * The last announcement as the voice said it, whole: an announcement the display shows is said in
 * steps (each trip's time, then its address, [Announcements.steps]); anything else is one.
 */
val ShadowTextToSpeech.lastAnnouncement: String?
    get() {
        val said = spokenTextList
        val last = said.lastOrNull() ?: return null
        val from = said.indexOfLast { it.startsWith("Nästa stopp: ") }
        if (from < 0) return last
        val steps = said.subList(from, said.size)
        val whole = steps.joinToString(" ")
        return if (Announcements.parts(whole) == steps) whole else last
    }

/** What the voice said for the last thing tapped, its steps joined. */
fun ShadowTextToSpeech.lastSaid(steps: Int): String = spokenTextList.takeLast(steps).joinToString(" ")
