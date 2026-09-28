package se.eldebosh.nastastopp.core.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import se.eldebosh.nastastopp.core.route.Announcement

class DisplaySnapshotTest {

    private data class Trip(val time: String?, val area: String)

    private fun item(t: Trip) = DisplayItem(t.time, t.area)

    @Test
    fun onePreviousCurrentAndThreeUpcoming() {
        val done = listOf(Trip("12:00", "A"), Trip("12:15", "B"))
        val left = listOf(Trip("12:30", "C"), Trip("12:45", "D"), Trip(null, "E"), Trip("13:10", "F"), Trip("13:20", "G"))
        val s = DisplaySnapshot.build(true, done, left, ::item, Announcement("Nästa stopp: C. Därefter: D.", null))
        assertEquals(DisplayItem("12:15", "B"), s.previous)
        assertEquals(DisplayItem("12:30", "C"), s.current)
        assertEquals(listOf("D", "E", "F"), s.upcoming.map { it.title })
        assertEquals(5, s.remaining)
        assertEquals(2, s.completed)
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
}
