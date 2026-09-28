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
}
