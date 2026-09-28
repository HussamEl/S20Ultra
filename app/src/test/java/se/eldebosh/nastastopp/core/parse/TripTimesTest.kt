package se.eldebosh.nastastopp.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Synthetic screenshot layouts (all data invented). */
class TripTimesTest {

    private val extractor = AddressExtractor(TestLocalities.instance)

    private fun times(lines: List<String>) = extractor.extract(lines).map { it.time }

    @Test
    fun timeAboveEachAddress() {
        assertEquals(
            listOf("12:48", "13:05"),
            times(
                listOf(
                    "14:02", // status bar clock
                    "12:48 Pick-up",
                    "ANDERSSON STORGATAN 14, 65224 KARLSTAD",
                    "SP1, HLI",
                    "13:05 Drop-off",
                    "LINDVÄGEN 9, 66430 GRU…",
                    "Compensation 52.50 KR",
                ),
            ),
        )
    }

    @Test
    fun timeBelowEachAddress() {
        assertEquals(
            listOf("12:48", "13:05"),
            times(listOf("14:02", "Storgatan 14, 65224 Karlstad", "12:48", "Lindvägen 9, 66430 Grums", "13:05")),
        )
    }

    @Test
    fun singleAddressWithTimeBelowIgnoresStatusBarClock() {
        assertEquals(listOf("09:05"), times(listOf("14:02 ▼ 87%", "Storgatan 14, 65224 Karlstad", "Hämtas 9.05")))
    }

    @Test
    fun timeOnTheSameLine() {
        assertEquals(
            listOf("12:48", "13:10"),
            times(listOf("12:48 Storgatan 14, 65224 Karlstad", "13:10  Kungsgatan 5, 65225 Karlstad")),
        )
    }

    @Test
    fun noTimeFromMoneyOrDistance() {
        assertEquals(listOf<String?>(null), times(listOf("Storgatan 14, 65224 Karlstad", "Client fee 12.50 KR", "Avstånd 12.50 km")))
    }

    @Test
    fun missingTimeForOneTripDoesNotStealTheNext() {
        assertEquals(
            listOf("12:48", null, "13:30"),
            times(listOf("14:02", "12:48", "Storgatan 14, 65224 Karlstad", "Kungsgatan 5, 65225 Karlstad", "13:30", "Lindvägen 9, 66430 Grums")),
        )
    }

    @Test
    fun helpers() {
        assertEquals("09:05", TripTimes.normalizeTyped("9:05"))
        assertEquals("23:59", TripTimes.normalizeTyped(" 23.59 "))
        assertNull(TripTimes.normalizeTyped("24:00"))
        assertNull(TripTimes.normalizeTyped("abc"))
        assertEquals(12 * 60 + 48, TripTimes.minutes("12:48"))
        assertEquals(Int.MAX_VALUE, TripTimes.minutes(null))
    }
}
