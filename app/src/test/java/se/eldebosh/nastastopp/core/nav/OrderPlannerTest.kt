package se.eldebosh.nastastopp.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.eldebosh.nastastopp.core.nav.OrderPlanner.Trip

class OrderPlannerTest {
    /** Seconds from the vehicle (row 0) or trip i (row i + 1) to trip j. */
    private fun times(vararg rows: IntArray): Array<IntArray> = arrayOf(*rows)

    private val eight = 8 * 3600

    /** A booked order that crosses town twice: the planner takes the near trip first. */
    @Test
    fun theNearTripFirstWhenNobodyIsLate() {
        val trips = listOf(Trip(9 * 60, true, 1), Trip(9 * 60, true, 2))
        val seconds = times(
            intArrayOf(1200, 300),
            intArrayOf(0, 1100),
            intArrayOf(1000, 0),
        )
        val best = OrderPlanner.best(trips, seconds, eight)!!
        assertEquals(listOf(1, 0), best.order)
        assertEquals(0, best.lateMinutes)
        assertEquals(300 + 120 + 1000, best.seconds)
        assertEquals(listOf(300 + 120 + 1000, 300), best.arrivals)
    }

    /** Fewer minutes late wins over a shorter way. */
    @Test
    fun lessLateBeatsShorter() {
        val trips = listOf(Trip(8 * 60 + 10, true, 1), Trip(9 * 60, true, 2))
        val seconds = times(
            intArrayOf(900, 300),
            intArrayOf(0, 900),
            intArrayOf(900, 0),
        )
        // Via trip 1 first, trip 0 would be reached 8:22 (12 min late); straight to it, 8:15 (5 late).
        val best = OrderPlanner.best(trips, seconds, eight)!!
        assertEquals(listOf(0, 1), best.order)
        assertEquals(5, best.lateMinutes)
    }

    /** A passenger is never dropped off before they are picked up. */
    @Test
    fun aPickUpStaysBeforeItsDropOff() {
        val trips = listOf(Trip(9 * 60, true, 1), Trip(9 * 60 + 30, false, 1))
        val seconds = times(
            intArrayOf(1000, 100),
            intArrayOf(0, 1000),
            intArrayOf(100, 0),
        )
        assertFalse(OrderPlanner.allowed(listOf(1, 0), trips))
        assertTrue(OrderPlanner.allowed(listOf(0, 1), trips))
        assertEquals(listOf(0, 1), OrderPlanner.best(trips, seconds, eight)!!.order)
        // A drop-off whose pick-up is done already may go anywhere.
        assertTrue(OrderPlanner.allowed(listOf(0), listOf(Trip(null, false, 7))))
    }

    /** The given order stays when no other is better, and a missing way cannot be timed. */
    @Test
    fun theDriversOrderStaysWhenNoneIsBetter() {
        val trips = listOf(Trip(null, null, null), Trip(null, null, null))
        val even = times(intArrayOf(100, 100), intArrayOf(0, 100), intArrayOf(100, 0))
        assertEquals(listOf(1, 0), OrderPlanner.best(trips, even, eight, current = listOf(1, 0))!!.order)
        val broken = times(intArrayOf(RoutesApi.NO_WAY, 100), intArrayOf(0, 100), intArrayOf(100, 0))
        assertNull(OrderPlanner.plan(listOf(0, 1), trips, broken, eight))
        assertEquals(listOf(1, 0), OrderPlanner.best(trips, broken, eight, current = listOf(1, 0))!!.order)
    }

    @Test
    fun minutesLateAcrossMidnight() {
        assertEquals(0, OrderPlanner.lateMinutes(8 * 60, eight))
        assertEquals(1, OrderPlanner.lateMinutes(8 * 60, eight + 1))
        assertEquals(10, OrderPlanner.lateMinutes(23 * 60 + 55, 5 * 60))
        assertEquals(0, OrderPlanner.lateMinutes(0, 23 * 3600 + 50 * 60))
        assertEquals(0, OrderPlanner.lateMinutes(null, eight))
    }
}
