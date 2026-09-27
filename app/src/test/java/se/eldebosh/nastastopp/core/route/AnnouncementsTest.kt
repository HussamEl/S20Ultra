package se.eldebosh.nastastopp.core.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnouncementsTest {
    @Test
    fun phrases() {
        assertEquals("Nästa stopp: Skoghall. Därefter: Kil.", Announcements.nextStops("Skoghall", "Kil", false).swedish)
        assertEquals("Nästa stopp: Kil. Det är sista stoppet.", Announcements.nextStops("Kil", null, false).swedish)
        assertEquals("Rutten är klar.", Announcements.finished(false).swedish)
        assertNull(Announcements.finished(false).english)
    }

    @Test
    fun englishRepeat() {
        assertEquals("Next stop: Skoghall. Then: Kil.", Announcements.nextStops("Skoghall", "Kil", true).english)
        assertEquals("Next stop: Kil. This is the last stop.", Announcements.nextStops("Kil", null, true).english)
        assertEquals("The route is finished.", Announcements.finished(true).english)
    }

    @Test
    fun sameAreaIsStillSaidTwice() {
        assertEquals("Nästa stopp: Karlstad. Därefter: Karlstad.", Announcements.forRemaining(listOf("Karlstad", "Karlstad", "Kil"), false).swedish)
    }
}
