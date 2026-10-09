package se.eldebosh.nastastopp.core.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun eachStopIsSaidWithItsTimeFirst() {
        assertEquals(
            "Nästa stopp: Klockan nio noll åtta. Hamngatan 7, Skoghall. Därefter: Klockan nio trettio. Storgatan 14, Karlstad.",
            Announcements.nextStops("Hamngatan 7, Skoghall", "Storgatan 14, Karlstad", false, "09:08", "09:30").swedish,
        )
        assertEquals("Nästa stopp: Klockan tio. Kil. Det är sista stoppet.", Announcements.nextStops("Kil", null, false, "10:00").swedish)
        assertEquals("Next stop: 9:08, Kil. Then: 9:30, Skoghall.", Announcements.nextStops("Kil", "Skoghall", true, "09:08", "09:30").english)
        // A stop without a time is said without one.
        assertEquals("Nästa stopp: Kil. Därefter: Klockan nio trettio. Skoghall.", Announcements.nextStops("Kil", "Skoghall", false, null, "09:30").swedish)
    }

    /** As a clock shows it, in words: the hour without a leading zero, the minutes as digits are read, never "minuter". */
    @Test
    fun theTimeIsReadAsAClockShowsIt() {
        assertEquals("åtta noll två", Announcements.timeWords("08:02"))
        assertEquals("nio trettio", Announcements.timeWords("09:30"))
        assertEquals("nio", Announcements.timeWords("09:00"))
        assertEquals("ett noll sju", Announcements.timeWords("01:07"))
        assertEquals("fjorton tjugoett", Announcements.timeWords("14:21"))
        assertEquals("tjugotre femtionio", Announcements.timeWords("23:59"))
        assertEquals("Klockan åtta noll två", Announcements.spokenTime("08:02"))
        assertNull(Announcements.spokenTime("9.08?"))
        assertEquals("Klockan är åtta noll fem.", Announcements.clock(8, 5).swedish)
        assertEquals("Klockan är fjorton.", Announcements.clock(14, 0).swedish)
        assertEquals("The time is 8:05.", Announcements.clock(8, 5).english)
        for (h in 0..23) for (m in 0..59) assertFalse(Announcements.clock(h, m).swedish.contains("minut"))
    }

    /** Each trip's time, then its address, said apart, in step with the display's show. */
    @Test
    fun anAnnouncementIsSaidInStepsTimeThenAddress() {
        val text = Announcements.nextStops("Hamngatan 7, Skoghall", "Kil", false, "09:08", "09:30").swedish
        val steps = Announcements.steps(text)
        assertEquals(
            listOf("Nästa stopp: Klockan nio noll åtta.", "Hamngatan 7, Skoghall.", "Därefter: Klockan nio trettio.", "Kil."),
            steps.map { it.text },
        )
        assertEquals(listOf(true, false, true, false), steps.map { it.time })
        assertEquals(listOf(0, 0, 1, 1), steps.map { it.stop })
        assertEquals(
            listOf(Announcements.LEAD_MS, Announcements.TO_ADDRESS_MS, Announcements.TO_NEXT_MS, Announcements.TO_ADDRESS_MS),
            steps.indices.map { Announcements.silenceBefore(steps, it) },
        )
        // Without a time, the address alone; the last stop says so after its address.
        assertEquals(listOf("Nästa stopp: Kil. Det är sista stoppet."), Announcements.parts(Announcements.nextStops("Kil", null, false).swedish))
        assertEquals(
            listOf("Nästa stopp: Klockan tio.", "Kil. Det är sista stoppet."),
            Announcements.parts(Announcements.nextStops("Kil", null, false, "10:00").swedish),
        )
        // Anything else is one step, said at once.
        assertEquals(listOf("Klockan är nio."), Announcements.parts("Klockan är nio."))
        assertFalse(Announcements.isShow("Klockan är nio."))
        assertFalse(Announcements.isShow(Announcements.at("09:08", "Kil", coming = true).swedish))
    }

    @Test
    fun aTappedTripIsShownAndSaidLikeTheAnnouncement() {
        val later = Announcements.at("08:05", "Hamngatan 7, Skoghall", coming = true, word = "Sen").swedish
        assertEquals("Sen: Klockan åtta noll fem. Hamngatan 7, Skoghall.", later)
        assertTrue(Announcements.isShow(later))
        assertEquals(listOf("Sen: Klockan åtta noll fem.", "Hamngatan 7, Skoghall."), Announcements.parts(later))
        assertEquals("Därefter: Hamngatan 7.", Announcements.at(null, "Hamngatan 7", coming = true, word = "Därefter").swedish)
        // A trip done: its time, then its place.
        val done = Announcements.at("07:30", "Järnvägsgatan 3B, Storfors", coming = false).swedish
        assertEquals("Klockan sju trettio. Järnvägsgatan 3B, Storfors.", done)
        assertEquals(listOf("Klockan sju trettio.", "Järnvägsgatan 3B, Storfors."), Announcements.parts(done))
    }

    @Test
    fun sameAreaIsStillSaidTwice() {
        assertEquals("Nästa stopp: Karlstad. Därefter: Karlstad.", Announcements.nextStops("Karlstad", "Karlstad", false).swedish)
    }

    @Test
    fun aTappedTripIsSaidWithItsTimeNeverAsTheNextStop() {
        // A trip still coming (the phone's panel): where the car is going, and when.
        assertEquals("Klockan åtta noll fem ska vi till Hamngatan 7, Skoghall.", Announcements.at("08:05", "Hamngatan 7, Skoghall", coming = true).swedish)
        assertEquals("Klockan nio ska vi till Storgatan 14, Karlstad.", Announcements.at("09:00", "Storgatan 14, Karlstad", coming = true).swedish)
        assertEquals("At 8:05 we are going to Hamngatan 7, Skoghall.", Announcements.at("08:05", "Hamngatan 7, Skoghall", coming = true).english)
        assertEquals("Vi ska till Hamngatan 7, Skoghall.", Announcements.at(null, "Hamngatan 7, Skoghall", coming = true).swedish)
        assertEquals("Järnvägsgatan 3B, Storfors.", Announcements.at(null, "Järnvägsgatan 3B, Storfors", coming = false).swedish)
        for (coming in listOf(true, false)) assertEquals(false, Announcements.at("08:05", "Hamngatan 7", coming).swedish.contains("Nästa"))
    }
}
