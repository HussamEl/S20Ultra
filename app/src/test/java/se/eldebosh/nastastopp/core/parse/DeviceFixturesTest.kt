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

    @Test
    fun streetPrefixesAndSplitRows() = assertEquals(
        listOf(
            "08:10 Östra Storgatan 3, 652 24 Karlstad",
            "08:30 Norra allén 4, 652 25 Karlstad",
            "08:50 Södra Kyrkogatan 7, 681 30 Kristinehamn",
            "09:10 S:t Olofsgatan 2, 652 24 Karlstad",
            "09:30 Stora torget 1, 652 25 Karlstad",
            "09:50 Lilla Badhusgatan 5, 652 25 Karlstad",
            "10:10 Gamla Kyrkogatan 12, 664 30 Grums",
            "10:30 Övre Torggatan 8, 652 24 Karlstad",
            "10:50 Västra Skolgatan 9, 663 30 Skoghall",
            "11:10 Norra Strandvägen 21, 681 31 Kristinehamn",
        ),
        read(DeviceFixtures.prefixes),
    )

    /** The Swedish labels in the time-above list give the kinds too. */
    @Test
    fun timeAboveLabelsGiveTheKinds() = assertEquals(
        listOf(TripKind.PICK_UP, TripKind.DROP_OFF, TripKind.PICK_UP, TripKind.DROP_OFF),
        extractor.extract(DeviceFixtures.timeAbove).map { it.kind },
    )

    /** YouDrive: the grey Pull-out is read as the start point, then green pick-ups and white drop-offs. */
    @Test
    fun youDriveCardsGiveKindsAndTimes() {
        val stops = extractor.extract(DeviceFixtures.youDrive)
        assertEquals(
            listOf(
                "PULL_OUT Depågatan 1, 653 40 Karlstad",
                "PICK_UP 06:55 Storgatan 14, 652 24 Karlstad",
                "DROP_OFF 07:09 Lindvägen 9, 664 30 Grums",
                "PICK_UP 08:29 Kyrkogatan 2, 652 24 Karlstad",
                "DROP_OFF 08:53 Järnvägsgatan 3B, 688 30 Storfors",
            ),
            stops.mapIndexed { i, s -> listOfNotNull(s.kind?.name, s.time.takeIf { i > 0 }, s.displayText).joinToString(" ") },
        )
    }
}

