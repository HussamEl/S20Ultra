package se.eldebosh.nastastopp.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import se.eldebosh.nastastopp.core.display.DisplayItem

/** The order the driver tries on his map, as the trips of his way change under it. */
class WayEditTest {

    private fun trip(id: Long) = DisplayItem("08:0$id", "Storgatan $id", "Karlstad", id = id)

    private val a = trip(1)
    private val b = trip(2)
    private val c = trip(3)
    private val d = trip(4)
    private val e = trip(5)

    /** The trip picked is the one looked at, wherever it moves; none picked, the one the map was opened for. */
    @Test
    fun thePickedTripIsTheOneLookedAt() {
        val edit = WayEdit()
        assertEquals(1, edit.lookedIndex(listOf(a, b, c), opened = 1))
        edit.picked = c
        assertEquals(2, edit.lookedIndex(listOf(a, b, c), opened = 1))
        edit.move(listOf(a, b, c), 2, 0)
        assertEquals(0, edit.lookedIndex(edit.preview!!, opened = 1))
        // Picked trip gone from the way: back to the one opened.
        assertEquals(1, edit.lookedIndex(listOf(a, b), opened = 1))
    }

    @Test
    fun aTripMovedTakesItsNewPlace() {
        val edit = WayEdit()
        edit.move(listOf(a, b, c), 2, 0)
        assertEquals(listOf(c, a, b), edit.preview)
        // Dragged past its neighbour, one place at a time.
        edit.move(edit.preview!!, 0, 1)
        assertEquals(listOf(a, c, b), edit.preview)
    }

    @Test
    fun anAddedTripComesInItsPlace() {
        val edit = WayEdit()
        edit.preview = listOf(a, c, b)
        // The next trip after the way: after the order tried.
        edit.settle(listOf(a, b, c, d))
        assertEquals(listOf(a, c, b, d), edit.preview)
        // None left after: the one before the way comes before it.
        edit.preview = listOf(c, b, d)
        edit.settle(listOf(a, b, c, d))
        assertEquals(listOf(a, c, b, d), edit.preview)
    }

    @Test
    fun theOrderTriedGoesWhenItIsThePhonesOrATripLeaves() {
        val edit = WayEdit()
        edit.preview = listOf(b, a, c)
        edit.sent = listOf(2, 1, 3)
        // Taken by the phone: the way now is the order sent.
        edit.settle(listOf(b, a, c))
        assertNull(edit.preview)
        assertNull(edit.sent)
        // A trip of it is no longer on the way (done, or the next stop moved on).
        edit.preview = listOf(c, b, d)
        edit.settle(listOf(c, d, e))
        assertNull(edit.preview)
    }
}
