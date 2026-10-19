package se.eldebosh.nastastopp.route.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** A stop's street address ([Stop.streetText]) never carries its passenger's name (invented data, testdata/README.md). */
class StopTextTest {
    @Test
    fun aSurnameBeforeAPlaceIsLeftOut() {
        val stop = Stop(
            id = 1,
            displayText = "Testsson Provgården, Karlstad",
            candidates = listOf("Testsson Provgården, Karlstad"),
            name = "Anna Testsson",
        )
        assertEquals("Provgården, Karlstad", stop.streetText)
        // The driver's own screens keep the line as read.
        assertEquals("Testsson Provgården, Karlstad", stop.shownAddress)
    }

    @Test
    fun aSurnameBeforeAStreetIsLeftOut() {
        val stop = Stop(
            id = 1,
            displayText = "Testsson Storgatan 14, 65224 Karlstad",
            candidates = listOf("Testsson Storgatan 14, 65224 Karlstad", "Storgatan 14, 65224 Karlstad"),
            name = "Anna Testsson",
        )
        assertEquals("Storgatan 14, 65224 Karlstad", stop.streetText)
    }

    @Test
    fun aStreetNamedAfterAPersonKeepsItsFirstWord() {
        // The passenger's first name begins the street's own name: no glued name, kept whole.
        val stop = Stop(
            id = 1,
            displayText = "Anna Provares väg 3, 652 24 Karlstad",
            candidates = listOf("Anna Provares väg 3, 652 24 Karlstad"),
            name = "Anna Testsson",
        )
        assertEquals("Anna Provares väg 3, 652 24 Karlstad", stop.streetText)
        assertEquals(stop.copy(name = null).entranceKey, stop.entranceKey)
    }

    @Test
    fun anAddressThatIsOnlyTheNameIsKept() {
        // A village address that is the passenger's surname: nothing with letters would be left.
        val stop = Stop(id = 1, displayText = "Testsson 12, Karlstad", candidates = listOf("Testsson 12, Karlstad"), name = "Anna Testsson")
        assertEquals("Testsson 12, Karlstad", stop.streetText)
    }

    @Test
    fun withoutANameNothingChanges() {
        val stop = Stop(id = 1, displayText = "Provby Vårdcentral, Strandvägen 3, 665 30 Kil", candidates = listOf("Strandvägen 3, 665 30 Kil"))
        assertEquals("Strandvägen 3, 665 30 Kil", stop.streetText)
    }
}
