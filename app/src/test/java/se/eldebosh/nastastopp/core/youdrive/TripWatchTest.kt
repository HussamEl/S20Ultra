package se.eldebosh.nastastopp.core.youdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.TestLocalities

/** Added / cancelled trips between readings of the YouDrive page (all data invented). */
class TripWatchTest {

    private fun t(time: String?, address: String) = WatchedTrip(time, address)
    private val a = t("12:30", "Storgatan 14, 652 24 Karlstad")
    private val b = t("12:45", "Järnvägsgatan 3B, 688 30 Storfors")
    private val c = t("13:40", "Lindvägen 9, 664 30 Grums")
    private val noon = 12 * 60

    @Test
    fun firstReadingIsTheBaseline() {
        val w = TripWatch()
        assertTrue(w.onReading(listOf(a, b), noon).isEmpty())
        assertEquals(listOf(a, b), w.baseline)
    }

    @Test
    fun aNewTripIsReportedAfterTwoReadings() {
        val w = TripWatch()
        w.onReading(listOf(a, b), noon)
        assertTrue("not confirmed yet", w.onReading(listOf(a, b, c), noon).isEmpty())
        assertEquals(listOf(TripChange(c, added = true)), w.onReading(listOf(a, b, c), noon))
        assertTrue("reported once", w.onReading(listOf(a, b, c), noon).isEmpty())
    }

    @Test
    fun aCancelledTripIsReportedButADoneTripIsNot() {
        val w = TripWatch()
        w.onReading(listOf(a, b, c), noon)
        w.onReading(listOf(a, c), noon)
        assertEquals(listOf(TripChange(b, added = false)), w.onReading(listOf(a, c), noon))
        // 12:30 disappears at 12:50: finished, not cancelled.
        val later = 12 * 60 + 50
        w.onReading(listOf(c), later)
        assertTrue(w.onReading(listOf(c), later).isEmpty())
    }

    @Test
    fun flickeringAndEmptyReadingsRaiseNoAlarm() {
        val w = TripWatch()
        w.onReading(listOf(a, b), noon)
        assertTrue(w.onReading(emptyList(), noon).isEmpty()) // page loading / logged out
        assertTrue(w.onReading(listOf(a), noon).isEmpty()) // half-rendered once
        assertTrue(w.onReading(listOf(a, b), noon).isEmpty()) // back to normal
        assertTrue(w.onReading(listOf(a, b), noon).isEmpty())
        assertEquals(listOf(a, b), w.baseline)
    }

    @Test
    fun sameAddressTwiceIsCountedTwice() {
        val twice = TripWatch.diff(listOf(a), listOf(a, a), noon)
        assertEquals(listOf(TripChange(a, added = true)), twice)
        assertTrue(TripWatch.diff(listOf(a, b), listOf(b, a), noon).isEmpty())
    }

    @Test
    fun readsTimesAndAddressesFromThePageTextOnly() {
        val page = """
            YouDrive
            Idag
            12:48 Hämtning
            ANDERSSON STORGATAN 14, 65224 KARLSTAD
            SP1, HLI
            13:05 Lämning
            Lindvägen 9, 66430 Grums
            Ersättning 52.50 KR
        """.trimIndent()
        val trips = TripWatch.tripsIn(page, AddressExtractor(TestLocalities.instance))
        assertEquals(listOf("12:48", "13:05"), trips.map { it.time })
        assertTrue(trips[0].address, trips[0].address.startsWith("Storgatan 14"))
        assertFalse("no names", trips.any { it.address.contains("ANDERSSON", ignoreCase = true) })
        assertTrue(TripWatch.tripsIn("  \n ", AddressExtractor(TestLocalities.instance)).isEmpty())
    }

    @Test
    fun recognisesTheLoginPage() {
        assertTrue(TripWatch.looksLoggedOut("Välkommen\nLogga in med BankID"))
        assertTrue(TripWatch.looksLoggedOut("Username\nPassword"))
        assertFalse(TripWatch.looksLoggedOut("12:48 Storgatan 14, Karlstad"))
    }

    @Test
    fun anotherViewOrDayIsANewListNotChanges() {
        val d = t("14:10", "Kyrkogatan 2, 652 24 Karlstad")
        val e = t("15:00", "Skolgatan 5, 664 30 Grums")
        // Single trips added / cancelled are real changes.
        assertFalse(TripWatch.isNewList(listOf(a, b, c), listOf(a, c, d), 2))
        assertFalse("one trip in the list", TripWatch.isNewList(listOf(a), listOf(d), 2))
        // None of the earlier trips left: another view or day.
        assertTrue(TripWatch.isNewList(listOf(a, b), listOf(d), 3))
        assertTrue(TripWatch.isNewList(listOf(a, b, c), listOf(d, e), 5))
        // Many changes at once: more than 3 and more than half the list.
        val many = (0 until 10).map { t("0$it:00".takeLast(5), "Gata $it, 652 24 Karlstad") }
        assertTrue(TripWatch.isNewList(many, many.take(4), 6))
        assertFalse(TripWatch.isNewList(many, many.take(7), 3))
    }
}
