package se.eldebosh.nastastopp.core.youdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.TestLocalities
import se.eldebosh.nastastopp.core.parse.TripKind

/**
 * YouDrive's trip cards, one text per card as the page reader returns them. The structure copies
 * the driver's real list (2026-09-29); every name, number and note is invented.
 */
class YouDriveCardsTest {

    private val extractor = AddressExtractor(TestLocalities.instance)

    private val cards = listOf(
        "06:42\nPull-out\nPerformed\nDepågatan Depågatan 1, 65340 Karlstad\nCompensation 0 KR",
        "09:25\nDrop-off\nDeparted\nAnna Maria Testsson\nVårdhuset Parkgatan 3, Karlstad\nKarlstad, Vårdhuset\nRU1\nCompensation 84.92 KR",
        "09:35\n09:30\nPick-up\nPerformed\nBengt Anders Provare\nÖSTRA STORGATAN 39 LGH 1008, 65224 KARLSTAD\n0700000002\nSP1\nClient fee 0 KR",
        "09:48\n10:00\nDrop-off\nBengt Anders Provare\nKyrkogatan 12A, 65224 Karlstad\n0700000002\nCompensation 80.49 KR",
        "10:18\n10:20\nPick-up\nCecilia Maria Exempel\nSKOLGATAN 6F, 65224 KARLSTAD\nHLI, RU1, BEN1\ninne 10.50 tel 0700000003\n27 min\nArrive",
        "10:37\nDrop-off\nCecilia Maria Exempel\nSjukhuset huvudentrén,\nHLI, RU1, BEN1\nCompensation 42.47 KR",
    )

    private fun trips() = YouDriveCards.parse(cards, extractor)

    @Test
    fun everyCardIsOneTripWithItsOwnKindTimeAndName() {
        val t = trips()
        assertEquals(6, t.size)
        assertEquals(
            listOf(TripKind.PULL_OUT, TripKind.DROP_OFF, TripKind.PICK_UP, TripKind.DROP_OFF, TripKind.PICK_UP, TripKind.DROP_OFF),
            t.map { it.stop?.kind },
        )
        // The scheduled (first) time, which YouDrive orders the route by.
        assertEquals(listOf("06:42", "09:25", "09:35", "09:48", "10:18", "10:37"), t.map { it.time })
        // The booked time keeps the trip's identity when the schedule is re-planned.
        assertEquals(listOf(null, null, "09:30", "10:00", "10:20", null), t.map { it.booked })
        // First and last name only; none for the depot.
        assertEquals(
            listOf(null, "Anna Testsson", "Bengt Provare", "Bengt Provare", "Cecilia Exempel", "Cecilia Exempel"),
            t.map { it.stop?.name },
        )
    }

    @Test
    fun aPlaceWithoutAStreetNumberIsNotLost() {
        val hospital = trips().last()
        assertEquals("Sjukhuset Huvudentrén", hospital.stop?.displayText)
        // The list's town is tried first, so the right hospital is found.
        assertEquals("Sjukhuset Huvudentrén, Karlstad", hospital.stop?.candidates?.first())
        assertEquals("Karlstad", hospital.stop?.parsedTown)
    }

    @Test
    fun aRepeatedWordIsShownOnce() {
        assertEquals("Depågatan 1, 653 40 Karlstad", trips().first().stop?.displayText)
    }

    @Test
    fun doneTripsAreNotAddedButTheStartPointIs() {
        val t = trips()
        assertEquals(listOf(true, true, true, false, false, false), t.map { it.done })
        val add = YouDriveCards.toAdd(t)
        assertEquals(listOf(TripKind.PULL_OUT, TripKind.DROP_OFF, TripKind.PICK_UP, TripKind.DROP_OFF), add.map { it.kind })
    }

    @Test
    fun aCardWithoutKindOrAddressIsSkipped() {
        assertNull(YouDriveCards.parseCard("09:00\nAnna Testsson\nStorgatan 14, 65224 Karlstad", 0, extractor))
        assertNull(YouDriveCards.parseCard("09:00\nPick-up\n27 min", 0, extractor))
    }

    @Test
    fun theCardPathIsUsedWhenThePageGivesCards() {
        val viaCards = TripWatch.tripsIn("whatever", extractor, cards)
        assertEquals(6, viaCards.size)
        assertTrue(viaCards.any { it.stop?.displayText?.startsWith("Sjukhuset") == true })
        // Without cards the page text is parsed as before.
        assertFalse(TripWatch.tripsIn(cards.joinToString("\n"), extractor).any { it.stop?.displayText?.startsWith("Sjukhuset") == true })
    }

    /** The card's first line is the name, whatever its case; a status such as "No show" is not. */
    @Test
    fun theFirstLineOfTheCardIsTheName() {
        val lower = YouDriveCards.parseCard("10:00\nPick-up\nanna maria testsson\nStorgatan 14, 65224 Karlstad", 0, extractor)
        assertEquals("Anna Testsson", lower?.stop?.name)
        val noShow = YouDriveCards.parseCard("10:00\nPick-up\nNo show\nStorgatan 14, 65224 Karlstad", 0, extractor)
        assertNull(noShow?.stop?.name)
        // A place written before the street on the address line does not take the name's place.
        val place = YouDriveCards.parseCard("15:51\n15:45\nPick-up\nBritta Exempel\nVårdhuset Öppenvård Parkgatan 4, 65224 Karlstad", 0, extractor)
        assertEquals("Britta Exempel", place?.stop?.name)
        assertEquals("15:51", place?.time)
    }
}
