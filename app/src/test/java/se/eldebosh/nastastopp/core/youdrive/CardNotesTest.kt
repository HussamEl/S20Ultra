package se.eldebosh.nastastopp.core.youdrive

import org.junit.Assert.assertEquals
import org.junit.Test
import se.eldebosh.nastastopp.core.youdrive.CardNotes.Line.Phone
import se.eldebosh.nastastopp.core.youdrive.CardNotes.Line.Words

/** A trip card's instructions as lines, each number on its own (invented text and numbers). */
class CardNotesTest {

    /** Cut where the dispatcher cut it; whose a number is after it when one or two words say so. */
    @Test
    fun eachThingWrittenIsALineAndEachNumberToo() {
        val lines = CardNotes.of(
            "Hämtas på avd 4 med rullstol / sitter kvar i den/ / Är ingen personal på plats ring sonen / " +
                "Personalen hjälper till 0700000004 alt 070-000 00 05/ / Son 0700000006",
        )
        assertEquals(
            listOf(
                Words("Hämtas på avd 4 med rullstol"),
                Words("sitter kvar i den"),
                Words("Är ingen personal på plats ring sonen"),
                Words("Personalen hjälper till"),
                Phone("0700000004"),
                Phone("070-000 00 05", "alt"),
                Phone("0700000006", "Son"),
            ),
            lines,
        )
    }

    /** Words after a number stay a line of their own; so do the words of a line without a number. */
    @Test
    fun wordsAroundANumberKeepTheirPlace() {
        assertEquals(
            listOf(Words("Behöver stöd i trappan"), Phone("070 000 00 06"), Words("portkod 1234")),
            CardNotes.of("Behöver stöd i trappan 070 000 00 06 /portkod 1234"),
        )
        assertEquals(listOf(Words("inne 1500"), Phone("0700000001")), CardNotes.of("inne 1500 // 0700000001"))
        assertEquals(listOf(Words("Ring på porttelefonen"), Words("Väntar i entrén")), CardNotes.of("Ring på porttelefonen\nVäntar i entrén"))
    }

    /** A line that starts with a number: whose it is comes after it. */
    @Test
    fun aNumberFirstHasItsWordsAfterIt() {
        assertEquals(
            listOf(Phone("0700000001", "dotter"), Phone("0700000002"), Words("ring innan ni kommer")),
            CardNotes.of("0700000001 dotter, 0700000002 ring innan ni kommer"),
        )
        assertEquals(listOf(Phone("+46 70 000 00 02", "Dotter")), CardNotes.of("Dotter: +46 70 000 00 02"))
    }

    /** A "/" between two words or figures stays, a sentence ends at its full stop, and a short code is no number. */
    @Test
    fun onlyTheDispatchersCutsCut() {
        assertEquals(
            listOf(Words("Hämtas/lämnas vid entrén."), Words("Ring 1/2 timme innan")),
            CardNotes.of("Hämtas/lämnas vid entrén. Ring 1/2 timme innan"),
        )
        assertEquals(listOf(Words("portkod 0123, lgh 0901")), CardNotes.of("portkod 0123, lgh 0901"))
        assertEquals(listOf(Words("Avd 9"), Phone("0700000005")), CardNotes.of("Avd 9 0700000005"))
    }
}
