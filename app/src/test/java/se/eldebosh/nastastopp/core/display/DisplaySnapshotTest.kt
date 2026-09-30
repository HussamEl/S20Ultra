package se.eldebosh.nastastopp.core.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.eldebosh.nastastopp.core.route.Announcement

class DisplaySnapshotTest {

    private data class Trip(val time: String?, val area: String)

    private fun item(t: Trip) = DisplayItem(t.time, t.area)

    @Test
    fun sevenDoneTheNextAndSevenUpcoming() {
        val done = (0 until 9).map { Trip("11:0$it", "done$it") }
        val left = listOf(Trip("12:30", "C"), Trip("12:45", "D"), Trip(null, "E")) + (0 until 8).map { Trip("13:0$it", "later$it") }
        val s = DisplaySnapshot.build(true, done, left, ::item, Announcement("Nästa stopp: C. Därefter: D.", null))
        assertEquals(DisplayItem("11:08", "done8", doneHere = true), s.previous)
        assertEquals((2 until 9).map { "done$it" }, s.earlier.map { it.title })
        assertTrue(s.earlier.all { it.doneHere })
        assertEquals(DisplayItem("12:30", "C"), s.current)
        assertEquals(listOf("D", "E", "later0", "later1", "later2", "later3", "later4"), s.upcoming.map { it.title })
        assertEquals(11, s.remaining)
        assertEquals(9, s.completed)
        assertEquals("Nästa stopp: C. Därefter: D.", s.announcement?.swedish)
    }

    @Test
    fun inactiveOrEmptyRouteShowsNothing() {
        val s = DisplaySnapshot.build(false, emptyList(), listOf(Trip("12:30", "C")), ::item, null)
        assertFalse(s.active)
        assertNull(s.current)
        assertFalse(DisplaySnapshot.build(true, emptyList<Trip>(), emptyList(), ::item, null).active)
    }

    @Test
    fun firstTripHasNoPrevious() {
        val s = DisplaySnapshot.build(true, emptyList(), listOf(Trip("12:30", "C")), ::item, null)
        assertNull(s.previous)
        assertEquals(emptyList<DisplayItem>(), s.upcoming)
    }

    @Test
    fun aTripKeepsItsPlaceWhenMarkedDone() {
        val open = DisplayItem("12:30", "Storgatan 14", "Karlstad")
        assertEquals(open, open.copy(doneInYouDrive = true, doneHere = true).trip)
    }
}
