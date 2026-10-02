package se.eldebosh.nastastopp.core.youdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import se.eldebosh.nastastopp.core.youdrive.TripCardText.Row

/** A YouDrive card's text laid out as YouDrive's details window shows it (invented data). */
class TripCardTextTest {

    @Test
    fun aPickUpReadsLikeYouDrivesDetails() {
        val card = TripCardText.of(
            "09:58\n10:00\nPick-up\nPerformed\nErik Kalle Påhittad\nSTRANDVÄGEN 3 LGH 1001, 66530 KIL\n0700000006\n" +
                "HLI, SP1, FRA1, ROL1\nClient fee 0 KR\nCompensation 13.73 KR\nFTJ\nBehöver stöd i trappan 070 000 00 06 /portkod 1234\n27 min\nArrive",
        )
        assertEquals("Pick-up 09:58", card.title)
        assertEquals("Erik Kalle Påhittad", card.name)
        assertEquals("09:58", card.estimated)
        assertEquals("10:00", card.negotiated)
        assertEquals("Pick-up", card.kind)
        assertEquals("Performed", card.status)
        assertEquals(
            listOf(
                Row("Address", "STRANDVÄGEN 3 LGH 1001, 66530 KIL"),
                Row("Phone number", "0700000006"),
                Row("Space Type(s)", "Sittande passagerare, Fram, Rollator fällbar"),
                Row("Mobility Aids", "Hämtas/Lämnas inne"),
                Row("Fare amount", "0 KR"),
                Row("Compensation", "13.73 KR"),
                Row("Eligibility", "FTJ"),
                Row("Instructions", "Behöver stöd i trappan 070 000 00 06 /portkod 1234"),
            ),
            card.rows,
        )
    }

    /** Codes written out as YouDrive writes them; a code not known yet stays as it is. */
    @Test
    fun theCodesAreWrittenOutAndAnUnknownOneKept() {
        val card = TripCardText.of(
            "08:43\n08:45\nPick-up\nFrida Uppdiktad\nCentralsjukhuset Karlstad, Karlstad\nHLI, AVD, RU1, TRP1, BEN1\n" +
                "Client fee 0 KR\nCompensation 178.19 KR\nSJU\nHämtas avd 9 med transportrullstol",
        )
        assertEquals("Rullstol, Transportrullstol, BEN1", card.rows.first { it.label == TripCardText.SPACE }.value)
        assertEquals("Hämtas/Lämnas inne, Hämtning på avdelning", card.rows.first { it.label == TripCardText.AIDS }.value)
        assertEquals("Hämtas avd 9 med transportrullstol", card.rows.last().value)
        assertNull(card.status)
    }

    /** An address on two lines stays one field; a line of its own without a label keeps its place. */
    @Test
    fun anAddressOnTwoLinesIsOneField() {
        val card = TripCardText.of(
            "07:05\nDrop-off\nAnna Maria Testsson\nProvby Vårdcentral Strandvägen 3, Karlstad\nVC Provby\nSP1, ROL1\nCompensation 15.51 KR\nSJU\ninne 1500 // 0700000001",
        )
        assertEquals("Drop-off 07:05", card.title)
        assertEquals(Row("Address", "Provby Vårdcentral Strandvägen 3, Karlstad\nVC Provby"), card.rows.first())
        assertEquals(Row("Instructions", "inne 1500 // 0700000001"), card.rows.last())
        assertNull(card.negotiated)
    }

    /** The depot's card has no passenger: its address, its compensation and its summary. */
    @Test
    fun theDepotsCardHasNoName() {
        val card = TripCardText.of("18:09\nPull-in\nDepågatan Depågatan 1, 65340 Karlstad\nCompensation 0 KR\nSummary:\nTotal provider cost: 1 KR")
        assertNull(card.name)
        assertEquals(
            listOf(Row("Address", "Depågatan Depågatan 1, 65340 Karlstad"), Row("Compensation", "0 KR"), Row("Summary", ""), Row("Total provider cost", "1 KR")),
            card.rows,
        )
    }
}
