package se.eldebosh.nastastopp.core.parse

/**
 * Invented dispatch-list screenshots for testing on the phone (no real passengers). The same
 * lines are drawn to PNG by ScreenshotsRoboTest (committed in testdata/screenshots/) and parsed in
 * [DeviceFixturesTest], so the device test knows exactly which stops to expect.
 */
object DeviceFixtures {
    /** Time on a line above each address, with names and phone numbers that must be ignored. */
    val timeAbove = listOf(
        "12:03", // status bar clock
        "Mina körningar",
        "07:30  Hämtning",
        "Anna Testsson",
        "Storgatan 14, 652 24 Karlstad",
        "Tel 070-000 00 01",
        "08:05  Lämning",
        "Bengt Provare",
        "Järnvägsgatan 3B, 688 30 Storfors",
        "Tel 070-000 00 02",
        "08:40  Hämtning",
        "Cecilia Exempel",
        "Lindvägen 9, 664 30 Grums",
        "09:15  Lämning",
        "David Demo",
        "Kyrkogatan 2, 652 24 Karlstad",
        "Tel 070-000 00 04",
    )

    /** Time on the same line as the address. */
    val sameLine = listOf(
        "12:03",
        "Resor idag",
        "10:20  Skolgatan 5, 664 30 Grums",
        "10:45  Västra Torggatan 12, 652 24 Karlstad",
        "11:10  Hamngatan 7, 663 30 Skoghall",
        "11:35  Kungsgatan 22, 681 31 Kristinehamn",
    )

    /**
     * Street names with prefixes and several words, two rows split after the prefix (as ML Kit
     * did with "Västra"), plus names and phone numbers that must be dropped.
     */
    val prefixes = listOf(
        "12:03",
        "Resor i morgon",
        "08:10  Östra Storgatan 3, 652 24 Karlstad",
        "08:30  Norra Allén 4, 652 25 Karlstad",
        "Erik Påhittad",
        "Tel 070-000 00 05",
        "08:50  Södra Kyrkogatan 7, 681 30 Kristinehamn",
        "09:10  S:t Olofsgatan 2, 652 24 Karlstad",
        "09:30  Stora Torget 1, 652 25 Karlstad",
        "09:50  Lilla Badhusgatan 5, 652 25 Karlstad",
        "Frida Uppdiktad",
        "10:10  Gamla Kyrkogatan 12, 664 30 Grums",
        "10:30  Övre Torggatan 8, 652 24 Karlstad",
        "10:50  Västra",
        "Skolgatan 9, 663 30 Skoghall",
        "11:10  Norra",
        "Strandvägen 21, 681 31 Kristinehamn",
        "Tel 070-000 00 06",
    )
}

