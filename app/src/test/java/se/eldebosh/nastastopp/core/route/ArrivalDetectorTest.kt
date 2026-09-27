package se.eldebosh.nastastopp.core.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Simulated location sequences along a straight north–south road. */
class ArrivalDetectorTest {

    private val stopLat = 59.3800
    private val stopLng = 13.5000
    private val metersPerDegLat = 111_195.0

    /** Position [m] metres north of the stop. */
    private fun fix(t: Long, m: Double, speed: Float?, acc: Float? = 5f) =
        Fix(t * 1000, stopLat + m / metersPerDegLat, stopLng, speed, acc)

    private fun run(d: ArrivalDetector, fixes: List<Fix>): List<Pair<Long, DetectorEvent>> =
        fixes.mapNotNull { f -> d.onFix(f)?.let { f.timeMs / 1000 to it } }

    private fun detector() = ArrivalDetector(ArrivalConfig.forRadius(75)).apply { setTarget(stopLat, stopLng) }

    @Test
    fun arrivesAfterDwellAndDepartsWhenDrivingOff() {
        val d = detector()
        val seq = mutableListOf<Fix>()
        var t = 0L
        // Approach from 400 m at 12 m/s (arms > 150 m)
        var m = 400.0
        while (m > 60) { seq += fix(t, m, 12f); t += 2; m -= 24 }
        // Stand still at 50 m for 12 s
        repeat(7) { seq += fix(t, 50.0, 0.3f); t += 2 }
        // Drive off slowly then away beyond 100 m
        seq += fix(t, 70.0, 3f); t += 2
        seq += fix(t, 90.0, 4f); t += 2
        seq += fix(t, 120.0, 4.5f); t += 2
        val events = run(d, seq)
        assertEquals(2, events.size)
        assertEquals(DetectorEvent.Arrived, events[0].second)
        assertEquals(DetectorEvent.Departed, events[1].second)
        assertTrue("dwell ≥ 8 s", events[0].first >= 8)
    }

    @Test
    fun arrivesImmediatelyWithin25m() {
        val d = detector()
        val events = run(d, listOf(fix(0, 300.0, 10f), fix(2, 20.0, 6f)))
        assertEquals(listOf(DetectorEvent.Arrived), events.map { it.second })
    }

    @Test
    fun notArmedWhenStartingNearStop() {
        val d = detector()
        // Starts 40 m away and never goes > 150 m → never arrives.
        val seq = (0L..30L).map { fix(it * 2, 20.0, 0f) }
        assertTrue(run(d, seq).isEmpty())
    }

    @Test
    fun speedAbove5DepartsEvenWithinRadius() {
        val d = detector()
        val events = run(d, listOf(fix(0, 300.0, 10f), fix(2, 10.0, 1f), fix(40, 30.0, 6f)))
        assertEquals(listOf(DetectorEvent.Arrived, DetectorEvent.Departed), events.map { it.second })
    }

    @Test
    fun inaccurateFixesIgnored() {
        val d = detector()
        val events = run(d, listOf(fix(0, 300.0, 10f), fix(2, 5.0, 0f, acc = 80f)))
        assertTrue(events.isEmpty())
        assertNull(d.lastDistanceM?.takeIf { it < 100 })
    }

    @Test
    fun movingSlowlyThroughRadiusWithoutDwellDoesNotArrive() {
        val d = detector()
        // Passes 60–74 m from the stop at 3 m/s: never < 2 m/s, never ≤ 25 m.
        val seq = listOf(fix(0, 400.0, 12f), fix(2, 74.0, 3f), fix(4, 66.0, 3f), fix(6, 60.0, 3f), fix(8, 200.0, 12f))
        assertTrue(run(d, seq).isEmpty())
    }

    @Test
    fun noTwoAutomaticAdvancesWithin30s() {
        val d = detector()
        // Stop 1: arrive and depart at t=4
        val first = run(d, listOf(fix(0, 300.0, 10f), fix(2, 10.0, 0f), fix(4, 150.0, 8f)))
        assertEquals(DetectorEvent.Departed, first.last().second)
        // Next stop 400 m further north; set target and reach it quickly.
        val nextLat = stopLat + 400 / metersPerDegLat
        d.setTarget(nextLat, stopLng)
        fun f2(t: Long, mFromNext: Double, s: Float) = Fix(t * 1000, nextLat + mFromNext / metersPerDegLat, stopLng, s, 5f)
        val events = mutableListOf<Pair<Long, DetectorEvent>>()
        listOf(f2(6, -250.0, 15f), f2(10, 0.0, 0f), f2(14, 150.0, 10f), f2(20, 200.0, 10f), f2(36, 300.0, 10f))
            .forEach { f -> d.onFix(f)?.let { events += f.timeMs / 1000 to it } }
        assertEquals(listOf(10L to DetectorEvent.Arrived, 36L to DetectorEvent.Departed), events)
    }

    @Test
    fun disabledTargetNeverTriggers() {
        val d = ArrivalDetector()
        d.setTarget(null, null)
        assertTrue(run(d, listOf(fix(0, 300.0, 10f), fix(2, 0.0, 0f))).isEmpty())
    }

    @Test
    fun radiusSettingScalesThresholds() {
        val c = ArrivalConfig.forRadius(150)
        assertEquals(150.0, c.arrivalRadiusM, 0.0)
        assertEquals(225.0, c.armDistanceM, 0.0)
        assertEquals(175.0, c.departDistanceM, 0.0)
        val c2 = ArrivalConfig.forRadius(10)
        assertEquals(25.0, c2.arrivalRadiusM, 0.0)
        assertEquals(150.0, c2.armDistanceM, 0.0)
    }
}
