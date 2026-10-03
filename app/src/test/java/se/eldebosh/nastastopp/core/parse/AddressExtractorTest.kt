package se.eldebosh.nastastopp.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** All inputs are synthetic (invented) test data. */
class AddressExtractorTest {

    private val extractor = AddressExtractor(TestLocalities.instance)

    private fun single(line: String): ExtractedStop {
        val stops = extractor.extract(listOf(line))
        assertEquals("expected exactly one stop for '$line' but got $stops", 1, stops.size)
        return stops.single()
    }

    private fun assertRejected(line: String) {
        val stops = extractor.extract(listOf(line))
        assertTrue("expected '$line' to be rejected but got $stops", stops.isEmpty())
    }

    @Test
    fun leadingSurnameStrongAddress() {
        val stop = single("ANDERSSON STORGATAN 14, 65224 KARLSTAD")
        assertEquals("652 24", stop.parsedPostalCode)
        assertEquals("Karlstad", stop.parsedTown)
        assertTrue(stop.candidates.toString(), stop.candidates.contains("Storgatan 14, 652 24 Karlstad"))
        // Full line first, then progressively dropped leading tokens.
        assertEquals("Andersson Storgatan 14, 652 24 Karlstad", stop.candidates.first())
        assertTrue(stop.candidates.indexOf("Storgatan 14, 652 24 Karlstad") > 0)
    }

    @Test
    fun streetWithLetterHouseNumber() {
        val stop = single("Järnvägsgatan 3B, 68830 Storfors")
        assertEquals("Järnvägsgatan 3B, 688 30 Storfors", stop.displayText)
        assertEquals("688 30", stop.parsedPostalCode)
        assertEquals("Storfors", stop.parsedTown)
    }

    @Test
    fun apartmentAndTruncationRemoved() {
        val stop = single("BJÖRKVÄGEN 7 LGH 1102, 66341 HAMMARÖ…")
        assertEquals("Björkvägen 7, 663 41 Hammarö", stop.displayText)
        assertEquals("663 41", stop.parsedPostalCode)
        assertEquals("Hammarö", stop.parsedTown)
    }

    @Test
    fun truncatedTownDropped() {
        val stop = single("LINDVÄGEN 9, 66430 GRU…")
        assertEquals("664 30", stop.parsedPostalCode)
        assertNull(stop.parsedTown)
        assertEquals("Lindvägen 9, 664 30", stop.displayText)
        assertTrue(stop.candidates.none { it.contains("Gru", ignoreCase = true) })
    }

    @Test
    fun threeDotTruncationAlsoHandled() {
        val stop = single("LINDVÄGEN 9, 66430 GRU...")
        assertEquals("Lindvägen 9, 664 30", stop.displayText)
    }

    @Test
    fun noStreetSuffixButPostalCodeAndTown() {
        val stop = single("Ekhagen 212, 65593 Karlstad")
        assertEquals("Ekhagen 212, 655 93 Karlstad", stop.displayText)
        assertEquals("655 93", stop.parsedPostalCode)
        assertEquals("Karlstad", stop.parsedTown)
    }

    @Test
    fun placeNameWithKnownTown() {
        val stop = single("Stadsbiblioteket, Karlstad")
        assertEquals("Stadsbiblioteket, Karlstad", stop.displayText)
        assertNull(stop.parsedPostalCode)
        assertEquals("Karlstad", stop.parsedTown)
    }

    @Test
    fun ocrFixInPostalCode() {
        val stop = single("Storgatan 14, 652 2O Karlstad")
        assertEquals("652 20", stop.parsedPostalCode)
        assertEquals("Storgatan 14, 652 20 Karlstad", stop.displayText)
    }

    @Test
    fun ocrFixesOnlyInsidePostalPositions() {
        // "S" and "O" in words must not be touched.
        val stop = single("SOLVÄGEN 5, 6S2 1O SUNNE")
        assertEquals("652 10", stop.parsedPostalCode)
        assertEquals("Solvägen 5, 652 10 Sunne", stop.displayText)
    }

    @Test
    fun rejectedLines() {
        listOf(
            "0701234567",
            "076-1234567",
            "Compensation 52.5 KR",
            "Client fee 0 KR",
            "12:48",
            "SP1, RO1",
            "SP1, HLI",
            "Performed",
            "Kalle Karlsson",
            "Lä baksidan",
            "+46 70 123 45 67",
            "010-123 45 67",
            "Status: Departed",
            "Pick-up",
            "Drop-off 13:05",
        ).forEach { assertRejected(it) }
    }

    @Test
    fun uiWordLineAcceptedWhenStrong() {
        val stop = single("Drop-off: Storgatan 14, 65224 Karlstad")
        assertEquals("652 24", stop.parsedPostalCode)
        assertTrue(stop.candidates.contains("Storgatan 14, 652 24 Karlstad"))
    }

    @Test
    fun uiWordLineRejectedWithoutPostalCode() {
        assertRejected("Pick-up: Storgatan 14, Karlstad")
    }

    @Test
    fun consecutiveDuplicatesMerged() {
        val stops = extractor.extract(
            listOf(
                "Storgatan 14, 65224 Karlstad",
                "Storgatan 14, 65224 Karlstad",
            ),
        )
        assertEquals(1, stops.size)
        assertEquals("Storgatan 14, 652 24 Karlstad", stops.single().displayText)
        assertEquals(0, stops.single().sourceOrder)
    }

    @Test
    fun duplicateWithLeadingNameMerged() {
        val stops = extractor.extract(
            listOf(
                "ANDERSSON STORGATAN 14, 65224 KARLSTAD",
                "Storgatan 14, 65224 Karlstad",
            ),
        )
        assertEquals(1, stops.size)
    }

    /**
     * The same address twice in a row is one trip read twice only when nothing known differs: two
     * times, two passengers or a drop-off and a pick-up are two trips (invented data).
     */
    @Test
    fun theSameAddressIsTwoTripsWhenTheyDiffer() {
        val at = single("Storgatan 14, 65224 Karlstad")
        val first = at.copy(time = "07:30", name = "Anna Testsson", kind = TripKind.PICK_UP)
        assertTrue(extractor.isSameTrip(first, at))
        assertTrue(extractor.isSameTrip(first, first.copy(time = "7:30", name = "ANNA TESTSSON")))
        assertFalse(extractor.isSameTrip(first, first.copy(time = "08:05")))
        assertFalse(extractor.isSameTrip(first, first.copy(name = "Bengt Provare")))
        assertFalse(extractor.isSameTrip(first, first.copy(kind = TripKind.DROP_OFF)))
        assertFalse(extractor.isSameTrip(first, single("Lindvägen 9, 66430 Grums").copy(time = "07:30")))
    }

    @Test
    fun nonConsecutiveDuplicatesKept() {
        val stops = extractor.extract(
            listOf(
                "Storgatan 14, 65224 Karlstad",
                "Lindvägen 9, 66430 Grums",
                "Storgatan 14, 65224 Karlstad",
            ),
        )
        assertEquals(3, stops.size)
    }

    @Test
    fun mixedScreenshotKeepsOnlyAddressesInOrder() {
        val lines = listOf(
            "12:48 Pick-up",
            "ANDERSSON STORGATAN 14, 65224 KARLSTAD",
            "SP1, HLI",
            "Performed",
            "0701234567",
            "13:05 Drop-off",
            "LINDVÄGEN 9, 66430 GRU…",
            "Compensation 52.5 KR",
            "Client fee 0 KR",
            "Kalle Karlsson",
            "Järnvägsgatan 3B, 68830 Storfors",
        )
        val stops = extractor.extract(lines)
        assertEquals(3, stops.size)
        assertEquals("664 30", stops[1].parsedPostalCode)
        assertEquals("Storfors", stops[2].parsedTown)
        assertEquals(listOf(1, 6, 10), stops.map { it.sourceOrder })
    }

    @Test
    fun joinsStreetLineWithFollowingPostalLine() {
        val stops = extractor.extract(listOf("Kungsgatan 5", "65224 Karlstad"))
        assertEquals(1, stops.size)
        assertEquals("Kungsgatan 5, 652 24 Karlstad", stops.single().displayText)
    }

    @Test
    fun joinsStreetLineWithFollowingTownLine() {
        val stops = extractor.extract(listOf("Kungsgatan 5", "Kil"))
        assertEquals(1, stops.size)
        assertEquals("Kungsgatan 5, Kil", stops.single().displayText)
    }

    @Test
    fun doesNotJoinWithLineThatOnlyStartsWithATownWord() {
        // "Bara" is a town but also means "only".
        val stops = extractor.extract(listOf("Kungsgatan 5", "Bara hämtning"))
        assertEquals(1, stops.size)
        assertEquals("Kungsgatan 5", stops.single().displayText)
    }

    @Test
    fun joinsStreetLineWithPostalLineAfterNameLine() {
        val stops = extractor.extract(listOf("Kalle Karlsson", "Storgatan 14", "65224 Karlstad", "Sverige"))
        assertEquals(1, stops.size)
        assertEquals("Storgatan 14, 652 24 Karlstad", stops.single().displayText)
        assertEquals(1, stops.single().sourceOrder)
    }

    @Test
    fun doesNotJoinNameWithTown() {
        assertTrue(extractor.extract(listOf("Kalle Karlsson", "Karlstad")).isEmpty())
    }

    @Test
    fun houseNumberWithSpaceLetter() {
        val stop = single("Storgatan 12 A, 65224 Karlstad")
        assertEquals("Storgatan 12 A, 652 24 Karlstad", stop.displayText)
    }

    @Test
    fun standaloneSuffixWordStaysLowerCase() {
        val stop = single("KARL JOHANS GATA 5, 65224 KARLSTAD")
        assertEquals("Karl Johans gata 5, 652 24 Karlstad", stop.displayText)
    }

    @Test
    fun careOfAndFloorRemoved() {
        val stop = single("c/o Svensson Storgatan 14 vån 3, 65224 Karlstad")
        assertEquals("Storgatan 14, 652 24 Karlstad", stop.displayText)
        val stop2 = single("Storgatan 14, 2 tr, port B, 65224 Karlstad")
        assertEquals("Storgatan 14, 652 24 Karlstad", stop2.displayText)
    }

    @Test
    fun uppgangRemoved() {
        val stop = single("Storgatan 14 uppg B, 65224 Karlstad")
        assertEquals("Storgatan 14, 652 24 Karlstad", stop.displayText)
    }

    @Test
    fun streetWithoutPlaceAccepted() {
        val stop = single("Hagalundsvägen 22")
        assertEquals("Hagalundsvägen 22", stop.displayText)
        assertNull(stop.parsedPostalCode)
        assertNull(stop.parsedTown)
    }

    @Test
    fun streetFollowedByKnownTownWithoutComma() {
        val stop = single("STORGATAN 14 KARLSTAD")
        assertEquals("Storgatan 14, Karlstad", stop.displayText)
        assertEquals("Karlstad", stop.parsedTown)
    }

    @Test
    fun phoneNumberStrippedFromAddressLine() {
        val stop = single("Storgatan 14, 65224 Karlstad 070-123 45 67")
        assertEquals("Storgatan 14, 652 24 Karlstad", stop.displayText)
    }

    @Test
    fun manualTextNeverRejected() {
        val stop = extractor.fromManualText("Centralstationen Karlstad")
        assertNotNull(stop)
        assertEquals("Centralstationen Karlstad", stop!!.displayText)
        val stop2 = extractor.fromManualText("storgatan 14 65224 karlstad")
        assertEquals("Storgatan 14, 652 24 Karlstad", stop2!!.displayText)
    }

    @Test
    fun sourceOrderOffsetApplied() {
        val stops = extractor.extract(listOf("Storgatan 14, 65224 Karlstad"), startOrder = 40)
        assertEquals(40, stops.single().sourceOrder)
    }

    /** A note written as a sentence is not an address, even when it names a street (invented). */
    @Test
    fun notesAreNotAddresses() {
        assertRejected("070-000 00 01=mobil inne 0000, följes in till plan 3")
        assertRejected("Storgatan 4, Karlstad så är det vid huset som kund ska hä/lä")
        // Swedish street names with a lower-case word stay addresses.
        single("Norra allén 4, 65225 Karlstad")
        single("Karl Johans gata 5, 65224 Karlstad")
    }
}
