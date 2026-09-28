package se.eldebosh.nastastopp.core.parse

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the phone must show after importing the PNGs in testdata/screenshots (names and phones dropped). */
class DeviceFixturesTest {
    private val extractor = AddressExtractor(TestLocalities.instance)

    private fun read(lines: List<String>) = extractor.extract(lines).map { "${it.time} ${it.displayText}" }

    @Test
    fun timeAboveEachAddress() = assertEquals(
        listOf(
            "07:30 Storgatan 14, 652 24 Karlstad",
            "08:05 Järnvägsgatan 3B, 688 30 Storfors",
            "08:40 Lindvägen 9, 664 30 Grums",
            "09:15 Kyrkogatan 2, 652 24 Karlstad",
        ),
        read(DeviceFixtures.timeAbove),
    )

    @Test
    fun timeOnTheSameLine() = assertEquals(
        listOf(
            "10:20 Skolgatan 5, 664 30 Grums",
            "10:45 Västra Torggatan 12, 652 24 Karlstad",
            "11:10 Hamngatan 7, 663 30 Skoghall",
            "11:35 Kungsgatan 22, 681 31 Kristinehamn",
        ),
        read(DeviceFixtures.sameLine),
    )

    /** Found on the S20 Ultra: ML Kit split "10:45  Västra Torggatan 12, …" after "Västra". */
    @Test
    fun streetPrefixSplitOffByOcrIsPutBack() {
        assertEquals(
            listOf("10:45 Västra Torggatan 12, 652 24 Karlstad", "11:10 Hamngatan 7, 663 30 Skoghall"),
            read(listOf("10:45 Västra", "Torggatan 12, 652 24 Karlstad", "11:10 Hamngatan 7, 663 30 Skoghall")),
        )
        assertEquals(listOf("09:00 Östra Storgatan 3, 652 24 Karlstad"), read(listOf("Resor idag", "09:00", "Östra", "Storgatan 3, 652 24 Karlstad")))
        // Ordinary words are not carried over.
        assertEquals(listOf("null Storgatan 14, 652 24 Karlstad"), read(listOf("Visa nya", "Storgatan 14, 652 24 Karlstad")))
    }

    @Test
    fun streetPrefixIsNeverDroppedAsIfItWereASurname() {
        for (address in listOf(
            "Västra Torggatan 12, 652 24 Karlstad",
            "Gamla Kilsvägen 12, 653 43 Karlstad",
            "Norra Allén 4, 652 24 Karlstad",
            "Sankt Eriks gata 5, 652 24 Karlstad",
        )) {
            val stop = extractor.extract(listOf(address)).single()
            val prefix = address.substringBefore(' ')
            assertEquals(address, prefix, stop.displayText.substringBefore(' '))
            assertEquals("no candidate without $prefix", true, stop.candidates.all { it.startsWith(prefix) })
        }
        // A surname in front is still dropped for the geocoder: "Andersson Västra Torggatan 12".
        val named = extractor.extract(listOf("ANDERSSON VÄSTRA TORGGATAN 12, 65224 KARLSTAD")).single()
        assertEquals(true, named.candidates.contains("Västra Torggatan 12, 652 24 Karlstad"))
    }
}

