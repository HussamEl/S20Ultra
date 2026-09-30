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
    fun aTappedTripIsSaidWithItsTimeNeverAsTheNextStop() {
        // A trip still coming: where the car is going, and when.
        assertEquals("Klockan 8 och 05 ska vi till Hamngatan 7, Skoghall.", Announcements.at("08:05", "Hamngatan 7, Skoghall", coming = true).swedish)
        assertEquals("Klockan 9 ska vi till Storgatan 14, Karlstad.", Announcements.at("09:00", "Storgatan 14, Karlstad", coming = true).swedish)
        assertEquals("At 8:05 we are going to Hamngatan 7, Skoghall.", Announcements.at("08:05", "Hamngatan 7, Skoghall", coming = true).english)
        assertEquals("Vi ska till Hamngatan 7, Skoghall.", Announcements.at(null, "Hamngatan 7, Skoghall", coming = true).swedish)
        // A trip done: its time and place only.
        assertEquals("Klockan 7 och 30: Järnvägsgatan 3B, Storfors.", Announcements.at("07:30", "Järnvägsgatan 3B, Storfors", coming = false).swedish)
        assertEquals("Järnvägsgatan 3B, Storfors.", Announcements.at(null, "Järnvägsgatan 3B, Storfors", coming = false).swedish)
        for (coming in listOf(true, false)) assertEquals(false, Announcements.at("08:05", "Hamngatan 7", coming).swedish.contains("Nästa"))
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
