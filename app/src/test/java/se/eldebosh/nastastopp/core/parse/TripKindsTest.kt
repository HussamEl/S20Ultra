package se.eldebosh.nastastopp.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pick-up / drop-off / depot labels of a dispatch list (YouDrive). All data is invented. */
class TripKindsTest {

    private val extractor = AddressExtractor(TestLocalities.instance)

    @Test
    fun readsWholeLabelsOnly() {
        assertEquals(TripKind.PICK_UP, TripKinds.labelIn("Pick-up"))
        assertEquals(TripKind.PICK_UP, TripKinds.labelIn("12:48 Pick-up"))
        assertEquals(TripKind.PICK_UP, TripKinds.labelIn("PICKUP"))
        assertEquals(TripKind.DROP_OFF, TripKinds.labelIn("Drop off"))
        assertEquals(TripKind.PULL_OUT, TripKinds.labelIn("Pull-out"))
        assertEquals(TripKind.PULL_IN, TripKinds.labelIn("Pull-in"))
        assertEquals(TripKind.PICK_UP, TripKinds.labelIn("Hämtning"))
        assertEquals(TripKind.DROP_OFF, TripKinds.labelIn("Avlämning"))
        assertNull(TripKinds.labelIn("Bara hämtning"))
        assertNull(TripKinds.labelIn("Performed"))
        assertNull(TripKinds.labelIn("Pick-up: Storgatan 14"))
    }

    /** A YouDrive screenshot: the address is at the top of each card, times and label below it. */
    @Test
    fun screenshotLabelsBelowTheAddress() {
        val stops = extractor.extract(
            listOf(
                "14:02",
                "06:42",
                "Depågatan 1, 65340 Karlstad",
                "Pull-out",
                "Compensation 0 KR",
                "Performed",
                "Anna Testsson",
                "STORGATAN 14 LGH 1101, 65224 KARLSTAD",
                "070-000 00 01",
                "06:55",
                "HLI, SP1",
                "06:55",
                "Client fee 0 KR",
                "Pick-up",
                "Compensation 0 KR",
                "Performed",
                "Anna Testsson",
                "Lindvägen 9, 66430 Grums",
                "070-000 00 01",
                "07:09",
                "Drop-off",
                "Compensation 83.51 KR",
                "Departed",
            ),
        )
        assertEquals(listOf(TripKind.PULL_OUT, TripKind.PICK_UP, TripKind.DROP_OFF), stops.map { it.kind })
        assertEquals(listOf("06:55", "07:09"), stops.drop(1).map { it.time })
    }

    /** The YouDrive page text: each card's time and label come before its address. */
    @Test
    fun pageTextLabelsAboveTheAddress() {
        val stops = extractor.extract(
            listOf(
                "2026-09-29", "Trips",
                "06:42", "Pull-out", "Performed", "Depågatan 1, 65340 Karlstad", "Compensation 0 KR",
                "06:55", "06:55", "Pick-up", "Performed", "Anna Testsson", "STORGATAN 14 LGH 1101, 65224 KARLSTAD", "070-000 00 01",
                "07:09", "Drop-off", "Departed", "Anna Testsson", "Lindvägen 9, 66430 Grums", "Compensation 83.51 KR",
                "16:30", "Pull-in", "Depågatan 1, 65340 Karlstad",
            ),
        )
        assertEquals(
            listOf(TripKind.PULL_OUT, TripKind.PICK_UP, TripKind.DROP_OFF, TripKind.PULL_IN),
            stops.map { it.kind },
        )
        assertEquals(listOf("06:42", "06:55", "07:09", "16:30"), stops.map { it.time })
    }

    @Test
    fun dropOffThenPickUpAtTheSamePlaceAreTwoStops() {
        val stops = extractor.extract(
            listOf(
                "09:09 Drop-off", "Kalle Karlsson", "Storgatan 14, 65224 Karlstad",
                "09:15 Pick-up", "Lisa Testsson", "Storgatan 14, 65224 Karlstad",
            ),
        )
        assertEquals(listOf(TripKind.DROP_OFF, TripKind.PICK_UP), stops.map { it.kind })
        assertEquals(listOf("09:09", "09:15"), stops.map { it.time })
    }

    @Test
    fun otherAppsHaveNoKind() {
        val stops = extractor.extract(listOf("12:30", "Storgatan 14, 65224 Karlstad", "12:45", "Lindvägen 9, 66430 Grums"))
        assertEquals(listOf(null, null), stops.map { it.kind })
    }
}
