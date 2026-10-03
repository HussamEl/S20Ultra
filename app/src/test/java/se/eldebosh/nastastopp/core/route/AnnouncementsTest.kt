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
    fun eachStopIsSaidWithItsTime() {
        assertEquals(
            "Nästa stopp: Hamngatan 7, Skoghall. Klockan 9 och 8 minuter. Därefter: Storgatan 14, Karlstad. Klockan 9 och 30 minuter.",
            Announcements.nextStops("Hamngatan 7, Skoghall", "Storgatan 14, Karlstad", false, "09:08", "09:30").swedish,
        )
        assertEquals("Nästa stopp: Kil. Klockan 10. Det är sista stoppet.", Announcements.nextStops("Kil", null, false, "10:00").swedish)
        assertEquals("Next stop: Kil, at 9:08. Then: Skoghall, at 9:30.", Announcements.nextStops("Kil", "Skoghall", true, "09:08", "09:30").english)
        // A stop without a time is said without one.
        assertEquals("Nästa stopp: Kil. Därefter: Skoghall. Klockan 9 och 30 minuter.", Announcements.nextStops("Kil", "Skoghall", false, null, "09:30").swedish)
    }

    @Test
    fun aNextStopAnnouncementIsSaidInTwoParts() {
        val text = Announcements.nextStops("Hamngatan 7, Skoghall", "Kil", false, "09:08", "09:30").swedish
        assertEquals(listOf("Nästa stopp: Hamngatan 7, Skoghall. Klockan 9 och 8 minuter.", "Därefter: Kil. Klockan 9 och 30 minuter."), Announcements.parts(text))
        assertEquals(listOf("Nästa stopp: Kil. Det är sista stoppet."), Announcements.parts(Announcements.nextStops("Kil", null, false).swedish))
        // Anything else is one part.
        assertEquals(listOf("Klockan är 9."), Announcements.parts("Klockan är 9."))
        assertEquals(false, Announcements.isNextStops(Announcements.at("09:08", "Kil", coming = true).swedish))
    }

    @Test
    fun sameAreaIsStillSaidTwice() {
        assertEquals("Nästa stopp: Karlstad. Därefter: Karlstad.", Announcements.forRemaining(listOf("Karlstad", "Karlstad", "Kil"), false).swedish)
    }

    @Test
    fun aTappedTripIsSaidWithItsTimeNeverAsTheNextStop() {
        // A trip still coming: where the car is going, and when.
        assertEquals("Klockan 8 och 5 minuter ska vi till Hamngatan 7, Skoghall.", Announcements.at("08:05", "Hamngatan 7, Skoghall", coming = true).swedish)
        assertEquals("Klockan 9 ska vi till Storgatan 14, Karlstad.", Announcements.at("09:00", "Storgatan 14, Karlstad", coming = true).swedish)
        assertEquals("At 8:05 we are going to Hamngatan 7, Skoghall.", Announcements.at("08:05", "Hamngatan 7, Skoghall", coming = true).english)
        assertEquals("Vi ska till Hamngatan 7, Skoghall.", Announcements.at(null, "Hamngatan 7, Skoghall", coming = true).swedish)
        // A trip done: its time and place only.
        assertEquals("Klockan 7 och 30 minuter: Järnvägsgatan 3B, Storfors.", Announcements.at("07:30", "Järnvägsgatan 3B, Storfors", coming = false).swedish)
        assertEquals("Järnvägsgatan 3B, Storfors.", Announcements.at(null, "Järnvägsgatan 3B, Storfors", coming = false).swedish)
        for (coming in listOf(true, false)) assertEquals(false, Announcements.at("08:05", "Hamngatan 7", coming).swedish.contains("Nästa"))
    }

    @Test
    fun theTimeIsReadWithoutALeadingZero() {
        assertEquals("Klockan är 8 och 5 minuter.", Announcements.clock(8, 5).swedish)
        assertEquals("Klockan är 14 och 30 minuter.", Announcements.clock(14, 30).swedish)
        assertEquals("Klockan är 9.", Announcements.clock(9, 0).swedish)
        assertEquals("Klockan är 9 och 1 minut.", Announcements.clock(9, 1).swedish)
        assertEquals("Klockan 1 och 7 minuter", Announcements.spokenTime("01:07"))
        assertEquals("Klockan 9 och 8 minuter", Announcements.spokenTime("09:08"))
        assertEquals(null, Announcements.spokenTime("9.08?"))
        assertEquals("The time is 8:05.", Announcements.clock(8, 5).english)
    }
}
