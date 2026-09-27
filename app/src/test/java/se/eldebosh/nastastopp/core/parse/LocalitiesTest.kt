package se.eldebosh.nastastopp.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalitiesTest {

    private val loc = TestLocalities.instance

    @Test
    fun containsAll290Municipalities() {
        val file = listOf(File("src/main/assets/localities_se.txt"), File("app/src/main/assets/localities_se.txt")).first { it.exists() }
        val text = file.readText()
        val municipalities = text.substringAfter("== All 290 municipalities").substringAfter("\n").substringBefore("== Tätorter in Värmland")
            .lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        assertEquals(290, municipalities.toSet().size)
        municipalities.forEach { assertTrue(it, loc.contains(it)) }
    }

    @Test
    fun caseAndDiacriticInsensitive() {
        assertEquals("Hammarö", loc.canonical("HAMMARÖ"))
        assertEquals("Hammarö", loc.canonical("hammaro"))
        assertEquals("Upplands Väsby", loc.canonical("UPPLANDS VÄSBY"))
        assertEquals("Upplands-Bro", loc.canonical("Upplands Bro"))
        assertNull(loc.canonical("Gru"))
    }

    @Test
    fun varmlandTatorter() {
        listOf("Skoghall", "Deje", "Molkom", "Charlottenberg", "Ekshärad", "Slottsbron", "Töcksfors", "Lesjöfors")
            .forEach { assertTrue(it, loc.contains(it)) }
    }

    @Test
    fun prefixAndSuffixWords() {
        assertEquals(2, loc.prefixWords("Upplands Väsby 12:00"))
        assertEquals(1, loc.suffixWords("Storgatan 14 Karlstad"))
        assertEquals(0, loc.prefixWords("Storgatan 14"))
    }
}
