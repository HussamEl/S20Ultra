package se.eldebosh.nastastopp.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/** A large address is written street first, its number under it, so it can grow as large as it can. */
class AddressLinesTest {
    @Test
    fun theNumberGoesUnderTheStreet() {
        assertEquals(listOf("Storgatan", "14"), addressLines("Storgatan 14"))
        assertEquals(listOf("Lindvägen", "9 A"), addressLines("Lindvägen 9 A"))
        assertEquals(listOf("Järnvägsgatan", "3B"), addressLines("Järnvägsgatan 3B"))
        assertEquals(listOf("Södra Kyrkogatan", "152"), addressLines("Södra Kyrkogatan 152"))
    }

    @Test
    fun withoutANumberTheFirstWordThenTheRest() {
        assertEquals(listOf("C-Sjukhuset", "Dialysentrén"), addressLines("C-Sjukhuset Dialysentrén"))
        assertEquals(listOf("Norra", "allén"), addressLines("Norra allén"))
        assertEquals(listOf("Storgatan"), addressLines("Storgatan"))
    }
}
