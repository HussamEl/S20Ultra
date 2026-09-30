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

    @Test
    fun aTappedCardIsAFollowingTripNeverTheNextStop() {
        assertEquals("Därefter: Hamngatan 7, Skoghall.", Announcements.following("Hamngatan 7, Skoghall").swedish)
        assertEquals("Then: Hamngatan 7, Skoghall.", Announcements.following("Hamngatan 7, Skoghall").english)
    }

    @Test
    fun theTimeIsReadLikeAnAnnouncement() {
        assertEquals("Klockan är 8 och 05.", Announcements.clock(8, 5).swedish)
        assertEquals("Klockan är 14 och 30.", Announcements.clock(14, 30).swedish)
        assertEquals("Klockan är 9.", Announcements.clock(9, 0).swedish)
        assertEquals("Klockan är 0 och 15.", Announcements.clock(0, 15).swedish)
        assertEquals("The time is 8:05.", Announcements.clock(8, 5).english)
    }
}
