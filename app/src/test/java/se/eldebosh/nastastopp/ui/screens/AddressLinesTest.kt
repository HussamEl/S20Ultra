package se.eldebosh.nastastopp.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/** A large address is written without its house number (the number is only said), its first word over the rest. */
class AddressLinesTest {
    @Test
    fun theNumberIsNotWritten() {
        assertEquals(listOf("Storgatan"), addressLines("Storgatan 14"))
        assertEquals(listOf("Lindvägen"), addressLines("Lindvägen 9 A"))
        assertEquals(listOf("Järnvägsgatan"), addressLines("Järnvägsgatan 3B"))
        assertEquals(listOf("Södra", "Kyrkogatan"), addressLines("Södra Kyrkogatan 152"))
        assertEquals("Södra Kyrkogatan", shownAddress("Södra Kyrkogatan 152"))
    }

    @Test
    fun theFirstWordThenTheRest() {
        assertEquals(listOf("C-Sjukhuset", "Dialysentrén"), addressLines("C-Sjukhuset Dialysentrén"))
        assertEquals(listOf("Norra", "allén"), addressLines("Norra allén"))
        assertEquals(listOf("Storgatan"), addressLines("Storgatan"))
        // A number alone is kept: there is nothing else to write.
        assertEquals(listOf("14"), addressLines("14"))
    }
}
