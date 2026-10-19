package se.eldebosh.nastastopp.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When each service of the tablet's map may be asked again. */
class WayGateTest {

    private val now = 1_000_000L
    private val wall = 1_700_000_000_000L

    private fun WayGate.mayAskAll(nowMs: Long, wallMs: Long) = WayService.entries.all { mayAsk(it, nowMs, wallMs) }
    private fun WayGate.mayAskNone(nowMs: Long, wallMs: Long) = WayService.entries.none { mayAsk(it, nowMs, wallMs) }

    /** The ways (asked by themselves as the car moves), and the travel times (only on the driver's Suggest). */
    private val ways = WayService.entries.filter { !it.matrix }
    private val travelTimes = WayService.entries.filter { it.matrix }

    private fun WayGate.mayAskNoWay(nowMs: Long, wallMs: Long) = ways.none { mayAsk(it, nowMs, wallMs) }
    private fun WayGate.mayAskEveryTravelTime(nowMs: Long, wallMs: Long) = travelTimes.all { mayAsk(it, nowMs, wallMs) }

    @Test
    fun anAnswerLetsEverythingAsk() {
        val gate = WayGate()
        assertTrue(gate.mayAskAll(now, wall))
        assertEquals(WayGate.Outcome.OK, gate.after(WayService.GOOGLE_ROUTES, 200, null, now, wall))
        assertTrue(gate.mayAskAll(now, wall))
    }

    /** Google's or mapmap's own answers (asked directly, or passed on by the server) are read as they always were. */
    @Test
    fun anUpstreamRefusalRefusesItsStopsAndEveryWayThatDidNotComeWaitsThirtySeconds() {
        for (code in listOf(403, 400)) {
            val gate = WayGate()
            assertEquals("$code", WayGate.Outcome.REFUSE_STOPS, gate.after(WayService.GOOGLE_ROUTES, code, null, now, wall))
            // Other stops wait 30 s, as after any way that did not come; the driver's Suggest does not.
            assertTrue("$code", gate.mayAskNoWay(now + 29_999, wall))
            assertTrue("$code", gate.mayAskEveryTravelTime(now, wall))
            assertTrue("$code", gate.mayAskAll(now + 30_000, wall))
        }

        for (code in listOf(429, 500, 502, 0, 301)) {
            val g = WayGate()
            assertEquals("$code", WayGate.Outcome.WAITING, g.after(WayService.MAPMAP_ROUTE, code, null, now, wall))
            assertTrue("$code", g.mayAskNoWay(now + 29_999, wall))
            assertTrue("$code", g.mayAskEveryTravelTime(now, wall))
            assertTrue("$code", g.mayAskAll(now + 30_000, wall))
        }
    }

    /** Suggest is the driver's tap (248): it never waits for the ways, and its answers never make them wait. */
    @Test
    fun theTravelTimesAreAskedOnTheDriversTapAndNeverMakeTheWaysWait() {
        val gate = WayGate()
        val failures = listOf<Pair<Int, ServerTrouble?>>(
            0 to null, 500 to null, 403 to null, 429 to null,
            0 to ServerTrouble.Down, 502 to ServerTrouble.UpstreamDown, 400 to ServerTrouble.Bug(400),
        )
        for ((code, trouble) in failures) {
            for (service in travelTimes) gate.after(service, code, trouble, now, wall)
            assertTrue("$code $trouble", gate.mayAskAll(now, wall))
        }
        // A way that did not come makes the ways wait, never the travel times.
        gate.after(WayService.GOOGLE_ROUTES, 0, ServerTrouble.Down, now, wall)
        assertTrue(gate.mayAskNoWay(now, wall))
        assertTrue(gate.mayAskEveryTravelTime(now, wall))
        // Nor does an answer of the travel times end the ways' wait.
        assertEquals(WayGate.Outcome.OK, gate.after(WayService.GOOGLE_MATRIX, 200, null, now, wall))
        assertTrue(gate.mayAskNoWay(now, wall))
        assertTrue(gate.mayAskAll(now + 30_000, wall))
    }

    @Test
    fun aTabletStoppedOrUnknownAsksNothingUntilCleared() {
        for (trouble in listOf(ServerTrouble.NotEnrolled, ServerTrouble.Stopped, ServerTrouble.Role)) {
            val gate = WayGate()
            assertEquals(WayGate.Outcome.STOPPED, gate.after(WayService.GOOGLE_MATRIX, 403, trouble, now, wall))
            assertTrue("$trouble", gate.mayAskNone(now + 365 * 86_400_000L, wall + 365 * 86_400_000L))
            // Why, for the driver's tap: every service, the travel times too.
            assertTrue("$trouble", WayService.entries.all { gate.held(it, wall) == trouble })
            gate.clear()
            assertTrue("$trouble", gate.mayAskAll(now, wall))
            assertNull(gate.held(WayService.GOOGLE_MATRIX, wall))
        }
    }

    @Test
    fun aDailyLimitPausesOnlyItsOwnServiceUntilItsTime() {
        val gate = WayGate()
        val until = wall + 3_600_000L
        assertEquals(WayGate.Outcome.WAITING, gate.after(WayService.GOOGLE_MATRIX, 429, ServerTrouble.DailyLimit(until), now, wall))
        assertFalse(gate.mayAsk(WayService.GOOGLE_MATRIX, now, wall))
        assertFalse(gate.mayAsk(WayService.GOOGLE_MATRIX, now + 3_599_999L, until - 1))
        assertEquals(ServerTrouble.DailyLimit(until), gate.held(WayService.GOOGLE_MATRIX, wall))
        assertNull(gate.held(WayService.GOOGLE_ROUTES, wall))
        // The matrix's limit never stops the ways.
        assertTrue(gate.mayAsk(WayService.GOOGLE_ROUTES, now, wall))
        assertTrue(gate.mayAsk(WayService.MAPMAP_ROUTE, now, wall))
        assertTrue(gate.mayAsk(WayService.MAPMAP_MATRIX, now, wall))
        assertEquals(mapOf(WayService.GOOGLE_MATRIX to until), gate.limits())
        // The wall clock says when: the monotonic clock does not.
        assertFalse(gate.mayAsk(WayService.GOOGLE_MATRIX, now + 86_400_000L, until - 1))
        assertTrue(gate.mayAsk(WayService.GOOGLE_MATRIX, now, until))
        gate.after(WayService.GOOGLE_ROUTES, 429, ServerTrouble.DailyLimit(until), now, wall)
        gate.clear()
        assertTrue(gate.limits().isEmpty())
        assertTrue(gate.mayAskAll(now, wall))
    }

    @Test
    fun aServiceWithoutAKeyOnTheServerWaitsTenMinutes() {
        val gate = WayGate()
        assertEquals(WayGate.Outcome.WAITING, gate.after(WayService.MAPMAP_ROUTE, 503, ServerTrouble.NoKey, now, wall))
        assertFalse(gate.mayAsk(WayService.MAPMAP_ROUTE, now, wall + 10 * 60_000L - 1))
        assertEquals(ServerTrouble.NoKey, gate.held(WayService.MAPMAP_ROUTE, wall))
        assertTrue(gate.mayAsk(WayService.GOOGLE_ROUTES, now, wall))
        assertTrue(gate.mayAsk(WayService.MAPMAP_ROUTE, now, wall + 10 * 60_000L))
        // Not a daily limit: the status line does not show it as one.
        assertTrue(gate.limits().isEmpty())
    }

    @Test
    fun aServerThatDoesNotAnswerIsAskedLessAndLessOftenUntilItAnswers() {
        val gate = WayGate()
        var t = now
        val waits = listOf(30_000L, 60_000L, 120_000L, 240_000L, 300_000L, 300_000L)
        for ((i, wait) in waits.withIndex()) {
            // No answer from the server, or from Google or mapmap behind it: each counts the same.
            val trouble = if (i % 2 == 0) ServerTrouble.Down else ServerTrouble.UpstreamDown
            assertEquals(WayGate.Outcome.WAITING, gate.after(WayService.GOOGLE_ROUTES, 502, trouble, t, wall))
            // The wait is shared by the ways: none is asked of a server that does not answer.
            assertTrue("$wait", gate.mayAskNoWay(t + wait - 1, wall))
            assertTrue("$wait", gate.mayAskEveryTravelTime(t, wall))
            assertTrue("$wait", gate.mayAskAll(t + wait, wall))
            t += wait
        }
        // An answer starts again from 30 s.
        assertEquals(WayGate.Outcome.OK, gate.after(WayService.GOOGLE_ROUTES, 200, null, t, wall))
        gate.after(WayService.GOOGLE_ROUTES, 0, ServerTrouble.Down, t, wall)
        assertTrue(gate.mayAskAll(t + 30_000L, wall))
        // Long after, the wait stays at five minutes.
        repeat(100) { gate.after(WayService.GOOGLE_ROUTES, 0, ServerTrouble.Down, t, wall) }
        assertFalse(gate.mayAsk(WayService.GOOGLE_ROUTES, t + 299_999L, wall))
        assertTrue(gate.mayAsk(WayService.GOOGLE_ROUTES, t + 300_000L, wall))
    }

    @Test
    fun aRequestTheServerRefusesIsNotAskedAgainForTheseStops() {
        val gate = WayGate()
        assertEquals(WayGate.Outcome.REFUSE_STOPS, gate.after(WayService.GOOGLE_ROUTES, 400, ServerTrouble.Bug(400), now, wall))
        // As Google's own refusal: other stops wait 30 s, nothing holds it for the driver's tap.
        assertTrue(gate.mayAskNoWay(now + 29_999, wall))
        assertNull(gate.held(WayService.GOOGLE_ROUTES, wall))
        assertTrue(gate.mayAskAll(now + 30_000, wall))
    }
}
