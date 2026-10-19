package se.eldebosh.nastastopp.robo

import android.content.Intent
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.AppGraph
import se.eldebosh.nastastopp.core.geo.AnnouncementDetail
import se.eldebosh.nastastopp.core.parse.TripKind
import se.eldebosh.nastastopp.core.youdrive.YouDriveCards
import se.eldebosh.nastastopp.geo.Geocoding
import se.eldebosh.nastastopp.route.RouteController
import se.eldebosh.nastastopp.route.RouteRepository
import se.eldebosh.nastastopp.route.model.GeoPoint
import se.eldebosh.nastastopp.route.model.GeoStatus
import se.eldebosh.nastastopp.route.model.RouteData
import se.eldebosh.nastastopp.route.model.Stop
import se.eldebosh.nastastopp.tts.TtsStatus
import java.time.Duration
import java.util.Locale

/** Runs the real RouteController/Announcer/MapsLauncher on Robolectric (Android 13 like the S20 Ultra). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class RouteControllerRoboTest {

    private lateinit var app: App
    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Geocoding.register(app) // read before the stops are looked for, so they wait only in the test's own time
        graph = app.graph
        graph.controller.clear()
        idle()
        graph.controller.awaitPersisted() // the route file is deleted on a background thread
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /**
     * Advances virtual time (Robolectric's Geocoder never answers, so each lookup times out), and
     * real time too: the address register is asked on a worker thread.
     */
    private fun idleUntil(condition: () -> Boolean) {
        repeat(2_000) {
            if (condition()) return
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            Thread.sleep(2)
        }
        assertTrue("condition not reached", condition())
    }

    private fun readyTts(): org.robolectric.shadows.ShadowTextToSpeech {
        ShadowTextToSpeech.addLanguageAvailability(Locale.forLanguageTag("sv-SE"))
        graph.announcer.recheck()
        val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS)
        idle()
        assertEquals(TtsStatus.READY, graph.announcer.status.value)
        return shadow
    }

    /**
     * One trip read twice (the end of one screenshot and the start of the next) is one stop; the
     * same address at another time is a trip of its own (invented data).
     */
    @Test
    fun theSameAddressAtAnotherTimeStaysATrip() {
        val c = graph.controller
        // A first line that is only a time is the status bar's clock: the list starts with its date.
        fun card(time: String) = graph.extractor.extract(listOf("2026-09-29", time, "Pick-up", "Anna Testsson", "Storgatan 14, 65224 Karlstad"))
        assertEquals(1, c.addExtracted(card("07:30") + card("07:30")))
        assertEquals(1, c.addExtracted(card("08:05")))
        assertEquals(listOf("07:30", "08:05"), c.route.value!!.stops.map { it.time })
    }

    /**
     * The driver corrects a stop's address during the route: the same stop (its number kept), but
     * Maps goes elsewhere now, so it opens again; a review without a change opens nothing.
     */
    @Test
    fun aCorrectedAddressOpensMapsAgain() {
        readyTts()
        val c = graph.controller
        val lines = listOf("Storgatan 14, 65224 Karlstad", "Järnvägsgatan 3B, 68830 Storfors", "Lindvägen 9, 66430 Grums")
        assertEquals(3, c.addExtracted(graph.extractor.extract(lines)))
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        assertTrue(c.start())
        idle()
        assertNotNull(shadowOf(app).nextStartedActivity)

        c.beginEdit()
        c.finishEdit()
        idle()
        assertNull("nothing changed", shadowOf(app).nextStartedActivity)

        val second = c.route.value!!.stops[1]
        c.beginEdit()
        assertTrue(c.editText(second.id, "Järnvägsgatan 5, 68830 Storfors"))
        c.finishEdit()
        idle()
        val url = shadowOf(app).nextStartedActivity?.dataString
        assertTrue("Maps again: $url", url != null && url.contains("J%C3%A4rnv%C3%A4gsgatan%205"))
        assertEquals(second.id, c.route.value!!.stops[1].id)
    }

    private val fourLines = listOf(
        "Storgatan 14, 65224 Karlstad",
        "Järnvägsgatan 3B, 68830 Storfors",
        "Lindvägen 9, 66430 Grums",
        "Kyrkogatan 2, 65224 Karlstad",
    )

    private fun stopIds() = graph.controller.route.value!!.stops.map { it.id }

    /** Undo of one delete puts back that stop only, in its old place; a later delete stays (invented data). */
    @Test
    fun undoPutsBackOnlyWhatWasDeleted() {
        val c = graph.controller
        assertEquals(4, c.addExtracted(graph.extractor.extract(fourLines)))
        val made = c.route.value!!.createdAtMs
        val (a, b, third, fourth) = c.route.value!!.stops
        val beforeA = c.route.value!!.stops
        c.delete(a.id)
        val beforeB = c.route.value!!.stops
        c.delete(b.id)
        c.undoDelete(made, listOf(a), beforeA)
        assertEquals(listOf(a.id, third.id, fourth.id), stopIds())
        // Tapped again, or for another route: nothing changes.
        c.undoDelete(made, listOf(a), beforeA)
        c.undoDelete(made + 1, listOf(b), beforeB)
        assertEquals(listOf(a.id, third.id, fourth.id), stopIds())
        c.undoDelete(made, listOf(b), beforeB)
        assertEquals(listOf(a.id, b.id, third.id, fourth.id), stopIds())
    }

    /** What the driver or the geocoder changed after a delete survives its Undo (invented data). */
    @Test
    fun undoKeepsTheChangesMadeSinceTheDelete() {
        val c = graph.controller
        assertEquals(4, c.addExtracted(graph.extractor.extract(fourLines)))
        val made = c.route.value!!.createdAtMs
        val (a, b, third, fourth) = c.route.value!!.stops
        val before = c.route.value!!.stops
        c.delete(a.id)
        c.move(2, 0)
        assertTrue(c.editText(third.id, third.displayText, "09:15"))
        // Located meanwhile.
        c.restoreStops(c.route.value!!.stops.map { it.copy(geoStatus = GeoStatus.LOCATED, geo = GeoPoint(59.38, 13.5, "addr", locality = "Karlstad")) })
        c.undoDelete(made, listOf(a), before)
        val stops = c.route.value!!.stops
        assertEquals(listOf(fourth.id, a.id, b.id, third.id), stops.map { it.id })
        assertEquals("09:15", stops.last().time)
        assertEquals(listOf(GeoStatus.LOCATED, GeoStatus.LOCATED, GeoStatus.LOCATED), stops.filter { it.id != a.id }.map { it.geoStatus })
    }

    /**
     * On an active route, the current trip deleted in the review and undone after a Next comes back
     * as the current trip: it is said and Maps opens from it, and the trip done since stays done
     * only (invented data).
     */
    @Test
    fun undoOnAnActiveRouteSaysTheTripAndOpensMaps() {
        val tts = readyTts()
        val c = graph.controller
        assertEquals(3, c.addExtracted(graph.extractor.extract(fourLines.take(3))))
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        val made = c.route.value!!.createdAtMs
        val (a, b, third) = c.route.value!!.stops
        assertTrue(c.start())
        idle()
        assertNotNull(shadowOf(app).nextStartedActivity)

        c.beginEdit()
        val before = c.route.value!!.stops
        c.delete(a.id)
        c.finishEdit()
        idle()
        assertTrue(tts.lastAnnouncement.orEmpty(), tts.lastAnnouncement.orEmpty().startsWith("Nästa stopp: Järnvägsgatan 3B"))
        assertNotNull(shadowOf(app).nextStartedActivity)
        c.next()
        idle()

        c.undoDelete(made, listOf(a), before)
        idle()
        val r = c.route.value!!
        assertEquals(listOf(a.id, third.id), r.stops.map { it.id })
        assertEquals(listOf(b.id), r.completed.map { it.id })
        assertTrue(tts.lastAnnouncement.orEmpty(), tts.lastAnnouncement.orEmpty().startsWith("Nästa stopp: Storgatan 14"))
        val url = shadowOf(app).nextStartedActivity?.dataString
        assertTrue("Maps again: $url", url != null && url.contains("Storgatan%2014") && !url.contains("J%C3%A4rnv%C3%A4gsgatan"))
        c.end()
    }

    /**
     * Undo while the review is still open says nothing and opens nothing: leaving the review
     * compares the list with how it was when the review opened (invented data).
     */
    @Test
    fun undoInTheReviewWaitsForTheReviewToClose() {
        val tts = readyTts()
        val c = graph.controller
        assertEquals(3, c.addExtracted(graph.extractor.extract(fourLines.take(3))))
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        val made = c.route.value!!.createdAtMs
        val (a, b, third) = c.route.value!!.stops
        assertTrue(c.start())
        idle()
        assertNotNull(shadowOf(app).nextStartedActivity)
        val said = tts.spokenTextList.size

        c.beginEdit()
        val before = c.route.value!!.stops
        c.delete(a.id)
        c.undoDelete(made, listOf(a), before)
        idle()
        assertEquals(listOf(a.id, b.id, third.id), stopIds())
        assertEquals(said, tts.spokenTextList.size)
        assertNull(shadowOf(app).nextStartedActivity)

        c.move(2, 1)
        c.finishEdit()
        idle()
        assertTrue(tts.lastAnnouncement.orEmpty(), tts.lastAnnouncement.orEmpty().startsWith("Nästa stopp: Storgatan 14, Karlstad. Lindvägen 9"))
        assertNotNull(shadowOf(app).nextStartedActivity)
        c.end()
    }

    /** A YouDrive list: the grey "Pull-out" is the start point, not a stop (invented data). */
    @Test
    fun pullOutBecomesTheStartPointNotAStop() {
        val tts = readyTts()
        val lines = listOf(
            "2026-09-29",
            "06:42", "Pull-out", "Depågatan 1, 65340 Karlstad",
            "06:55", "Pick-up", "Anna Testsson", "Storgatan 14, 65224 Karlstad",
            "07:09", "Drop-off", "Anna Testsson", "Järnvägsgatan 3B, 68830 Storfors",
        )
        val c = graph.controller
        assertEquals(2, c.addExtracted(graph.extractor.extract(lines)))
        val r = c.route.value!!
        assertEquals("Depågatan 1, 653 40 Karlstad", r.depot?.displayText)
        assertEquals(listOf(TripKind.PICK_UP, TripKind.DROP_OFF), r.stops.map { it.kind })
        assertTrue("the depot counts as already in the list", c.hasTrip(graph.extractor.extract(lines).first()))
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }

        assertTrue(c.start())
        idle()
        assertEquals("Nästa stopp: Klockan sex femtiofem. Storgatan 14, Karlstad. Klockan sju noll nio. Järnvägsgatan 3B, Storfors.", tts.lastAnnouncement)
        // The address log keeps the address, never the passenger's name.
        val logged = c.route.value!!.stops.mapNotNull(c::logLine)
        assertTrue(logged.toString(), logged.first().startsWith("Storgatan 14"))
        assertFalse(logged.toString(), logged.any { it.contains("Anna") || it.contains("Testsson") })
        val url = shadowOf(app).nextStartedActivity.dataString!!
        assertTrue(url, url.contains("Storgatan") && url.contains("J%C3%A4rnv%C3%A4gsgatan"))
        assertFalse("Maps is not sent to the depot: $url", url.contains("Dep"))
        c.end()
    }

    /**
     * The start point's point is found as a stop's is, only to be shown to the driver: it keeps its
     * text as written, stays the start point (never a stop, never on the passenger display or in
     * Maps), and keeps its point when YouDrive sends it again with another time (the register's
     * own addresses).
     */
    @Test
    fun theStartPointIsLocatedButStaysTheStartPoint() {
        val c = graph.controller
        fun list(start: String) = graph.extractor.extract(
            listOf("2026-09-29", start, "Pull-out", "Västra Torggatan 12, Karlstad", "06:55", "Pick-up", "Anna Testsson", "Järnvägsgatan 6, Storfors"),
        )
        assertEquals(1, c.addExtracted(list("06:42")))
        val written = c.route.value!!.depot!!.displayText
        idleUntil { c.route.value!!.let { r -> r.depot!!.geoStatus != GeoStatus.PENDING && r.stops.none { it.geoStatus == GeoStatus.PENDING } } }
        val r = c.route.value!!
        val depot = r.depot!!
        assertEquals(GeoStatus.LOCATED, depot.geoStatus)
        assertEquals(written, depot.displayText)
        assertNotNull(c.pointOf(depot))
        assertEquals(listOf(TripKind.PICK_UP), r.stops.map { it.kind })
        val shown = c.display.value.let { listOfNotNull(it.current) + it.upcoming + it.earlier }
        assertTrue("the start point is not on the passenger display", shown.none { it.lat == depot.geo!!.lat && it.lng == depot.geo!!.lng })
        // Sent again by YouDrive with another time: the same start point, its point kept.
        c.syncTrips(list("06:45"))
        assertEquals(depot.geo, c.route.value!!.depot!!.geo)
        assertEquals("06:45", c.route.value!!.depot!!.time)
        assertTrue(c.start())
        idle()
        val url = shadowOf(app).nextStartedActivity.dataString!!
        assertFalse("Maps is not sent to the start point: $url", url.contains("Torggatan"))
        c.end()
    }

    /**
     * "Add all trips" is a sync: a list read from screenshots (booked instead of scheduled times,
     * no names, a copy of the same trip) is brought up to date, never doubled (invented data).
     */
    @Test
    fun addAllTripsRefreshesInsteadOfDoubling() {
        val c = graph.controller
        val old = graph.extractor
        // Read from screenshots: the booked times (16:15) and no names, and one trip read twice.
        c.addManual("Storgatan 14, 65224 Karlstad", "16:15")
        c.addManual("Lindvägen 9, 66430 Grums", "16:43")
        c.addManual("Storgatan 14, 65224 Karlstad", "16:20")
        c.addManual("Kyrkogatan 2, 65224 Karlstad", "17:30") // added by hand, not on YouDrive
        val fresh = YouDriveCards.parse(
            listOf(
                "16:25\n16:15\nPick-up\nAnna Maria Testsson\nSTORGATAN 14 LGH 1402, 65224 KARLSTAD",
                "16:43\nDrop-off\nAnna Maria Testsson\nLindvägen 9, 66430 Grums",
            ),
            old,
        ).mapNotNull { it.stop }
        val result = c.syncTrips(fresh)
        assertEquals(0, result.added)
        assertEquals(2, result.updated)
        val stops = c.route.value!!.stops
        assertEquals(
            listOf("16:25 PICK_UP Anna Testsson", "16:43 DROP_OFF Anna Testsson", "17:30 null null"),
            stops.map { "${it.time} ${it.kind} ${it.name}" },
        )
        // A second sync changes nothing.
        assertEquals(RouteController.SyncResult(0, 0), c.syncTrips(fresh))
    }

    /**
     * A YouDrive trip added while the review is open says the review's edits so far and opens Maps
     * with them; the review's later edits are said when it closes, and a trip added with no review
     * open is said as before (invented data).
     */
    @Test
    fun aYouDriveTripAddedDuringTheReviewKeepsItsEdits() {
        val tts = readyTts()
        val c = graph.controller
        fun trip(card: String) = YouDriveCards.parse(listOf(card), graph.extractor).mapNotNull { it.stop }.single()
        val cards = listOf(
            "08:00\nPick-up\nFrida Maria Uppdiktad\nStrandvägen 3, 66530 Kil",
            "08:20\nPick-up\nErik Påhittad\nStorgatan 14, 65224 Karlstad",
            "08:30\nDrop-off\nFrida Maria Uppdiktad\nSkolgatan 5, 66530 Kil",
        )
        assertEquals(3, c.importTrips(cards.map(::trip)))
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        assertTrue(c.start())
        idle()
        assertNotNull(shadowOf(app).nextStartedActivity)

        c.beginEdit()
        c.move(2, 0)
        assertTrue(c.insertTrip(trip("09:00\nDrop-off\nErik Påhittad\nLindvägen 9, 66430 Grums")))
        idle()
        assertEquals("09:00", c.route.value!!.stops[3].time)
        assertTrue(tts.lastAnnouncement.orEmpty(), tts.lastAnnouncement.orEmpty().startsWith("Nästa stopp: Klockan åtta trettio. Skolgatan 5"))
        val url = shadowOf(app).nextStartedActivity?.dataString
        assertTrue("Maps: $url", url != null && url.contains("waypoints=Skolgatan%205"))

        c.move(1, 2)
        c.finishEdit()
        idle()
        assertTrue(tts.lastAnnouncement.orEmpty(), tts.lastAnnouncement.orEmpty().contains("Skolgatan 5, Kil. Klockan åtta tjugo. Storgatan 14"))
        val again = shadowOf(app).nextStartedActivity?.dataString.orEmpty()
        val way = again.substringAfter("waypoints=")
        assertTrue("Maps: $again", way.startsWith("Skolgatan%205") && way.indexOf("Storgatan%2014") in 0 until way.indexOf("Strandv"))

        // No review open: a trip that becomes the one after the next is said and sent to Maps at once.
        assertTrue(c.insertTrip(trip("08:10\nPick-up\nBengt Provare\nKyrkogatan 2, 65224 Karlstad")))
        idle()
        assertTrue(tts.lastAnnouncement.orEmpty(), tts.lastAnnouncement.orEmpty().contains("Skolgatan 5, Kil. Klockan åtta tio. Kyrkogatan 2"))
        assertNotNull(shadowOf(app).nextStartedActivity)
        c.end()
    }

    /**
     * Trips done in YouDrive are added and marked, and stay in the route until Next passes them;
     * a trip already done here is not added again, only its YouDrive mark follows (invented data).
     */
    @Test
    fun youDriveDoneTripsAreMarkedAndOnlyNextMovesTheRoute() {
        val c = graph.controller
        val read = { a: String, b: String ->
            YouDriveCards.parse(
                listOf("16:25\nPick-up\n$a\nAnna Testsson\nStorgatan 14, 65224 Karlstad", "16:43\nDrop-off\n$b\nAnna Testsson\nLindvägen 9, 66430 Grums"),
                graph.extractor,
            ).mapNotNull { it.stop }
        }
        assertEquals(2, c.syncTrips(read("Performed", "")).added)
        assertEquals(listOf(true, false), c.route.value!!.stops.map { it.youDriveDone })
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        c.start()
        idle()
        c.next()
        idle()
        assertEquals(listOf("16:25"), c.route.value!!.completed.map { it.time })
        // Both done in YouDrive now: the done trip is not added again, the next one is marked.
        val result = c.syncTrips(read("Performed", "Performed"))
        assertEquals(0, result.added)
        val r = c.route.value!!
        assertEquals(listOf(true), r.completed.map { it.youDriveDone })
        assertEquals(listOf("16:43" to true), r.stops.map { it.time to it.youDriveDone })
        c.end()
    }

    /** A passenger's surname glued before a place's name is never read as the place, and never kept with it. */
    @Test
    fun aSurnameBeforeAPlaceIsLeftOut() {
        val lines = listOf(
            "2026-09-29",
            "06:55", "Pick-up", "Anna Testsson", "Testsson Grava kyrka, Karlstad",
        )
        val c = graph.controller
        assertEquals(1, c.addExtracted(graph.extractor.extract(lines)))
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        val stop = c.route.value!!.stops.single()
        assertEquals(GeoStatus.LOCATED, stop.geoStatus)
        assertFalse("shown: ${stop.displayText}", stop.displayText.contains("Testsson"))
    }

    /**
     * The passenger's name (first + last) is for the driver's own screens; the passenger display
     * gets only the next stop's last name. A name is never in an announcement, the route
     * notification or the history (invented data).
     */
    @Test
    fun namesStayOnTheDriversScreens() {
        val tts = readyTts()
        val lines = listOf(
            "2026-09-29",
            "06:55", "Pick-up", "Anna Maria Testsson", "Storgatan 14, 65224 Karlstad",
            "07:09", "Drop-off", "Anna Maria Testsson", "Järnvägsgatan 3B, 68830 Storfors",
        )
        val c = graph.controller
        assertEquals(2, c.addExtracted(graph.extractor.extract(lines)))
        assertEquals(listOf("Anna Testsson", "Anna Testsson"), c.route.value!!.stops.map { it.name })
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        assertTrue(c.start())
        idle()
        c.next()
        idle()
        assertFalse("spoken: ${tts.lastAnnouncement}", tts.lastAnnouncement.orEmpty().contains("Testsson"))
        // The passenger display gets the coming trips' last names only (for the map's pins): no
        // first name, no middle name, none for a trip done.
        val display = c.display.value
        assertEquals("Testsson", display.current?.lastName)
        assertFalse("passenger display", display.toString().contains("Anna"))
        assertFalse("middle name", display.toString().contains("Maria"))
        assertTrue("trips done", display.earlier.all { it.lastName == null } && display.previous?.lastName == null)
        assertFalse("notification", routeNotificationTexts().any { it.contains("Testsson") })
        assertTrue(graph.history.entries.value.isNotEmpty())
        assertFalse("history", graph.history.entries.value.toString().contains("Testsson"))
        c.end()
    }

    /** Every extra of the route notification, whatever its type, as text. */
    private fun routeNotificationTexts(): List<String> {
        val notification = shadowOf(app.getSystemService(android.app.NotificationManager::class.java))
            .getNotification(se.eldebosh.nastastopp.service.Notifications.ID_ROUTE)
        assertNotNull(notification)
        // Only the deprecated Bundle.get reads every extra, whatever its type.
        @Suppress("DEPRECATION")
        return notification.extras.keySet().map { notification.extras.get(it)?.toString().orEmpty() }
    }

    /**
     * A surname written before the street is never said, and never reaches the route
     * notification, Google Maps or the history, also while the stop is not found (Robolectric's
     * geocoder never answers, and the register has no Storgatan in Karlstad).
     */
    @Test
    fun fullRouteFlowAnnouncesOnlyAreaNames() {
        val tts = readyTts()
        val lines = listOf(
            "ANDERSSON STORGATAN 14, 65224 KARLSTAD",
            "Compensation 52.5 KR",
            "Järnvägsgatan 3B, 68830 Storfors",
            "BJÖRKVÄGEN 7 LGH 1102, 66341 HAMMARÖ…",
        )
        assertEquals(3, graph.controller.addExtracted(graph.extractor.extract(lines)))
        idleUntil { graph.controller.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }

        assertTrue(graph.controller.start())
        idle()
        // The next stop in full (the default), but never the surname before the street.
        assertEquals("Nästa stopp: Storgatan 14, Karlstad. Järnvägsgatan 3B, Storfors.", tts.lastAnnouncement)

        val maps = shadowOf(app).nextStartedActivity
        assertNotNull(maps)
        assertEquals(Intent.ACTION_VIEW, maps.action)
        assertEquals("com.google.android.apps.maps", maps.`package`)
        val url = maps.dataString!!
        assertTrue(url, url.startsWith("https://www.google.com/maps/dir/?api=1&destination="))
        assertTrue(url, url.contains("&waypoints="))
        assertTrue(url, url.endsWith("&travelmode=driving&dir_action=navigate"))
        assertEquals(GeoStatus.NOT_LOCATED, graph.controller.route.value!!.stops.first().geoStatus)
        assertFalse("Maps: $url", url.contains("Andersson", ignoreCase = true))
        assertTrue("Maps: $url", url.contains("Storgatan%2014"))
        assertFalse("notification", routeNotificationTexts().any { it.contains("Andersson", ignoreCase = true) })

        graph.controller.next()
        assertEquals("Nästa stopp: Järnvägsgatan 3B, Storfors. Björkvägen 7, Hammarö.", tts.lastAnnouncement)
        val history = graph.history.entries.value
        assertEquals(listOf("Storgatan 14, 652 24 Karlstad"), history.map { it.displayText })
        // Back takes the trip out of the history again; Next writes it there once.
        assertTrue(graph.controller.back())
        assertTrue("history after back: ${graph.history.entries.value}", graph.history.entries.value.isEmpty())
        graph.controller.next()
        assertEquals(1, graph.history.entries.value.size)
        assertFalse("history", graph.history.entries.value.toString().contains("Andersson", ignoreCase = true))
        graph.controller.repeat()
        assertEquals("Nästa stopp: Järnvägsgatan 3B, Storfors. Björkvägen 7, Hammarö.", tts.lastAnnouncement)
        // The driver's other choices: district or town only.
        graph.settings.update { it.copy(detail = AnnouncementDetail.DISTRICT) }
        graph.controller.repeat()
        assertEquals("Nästa stopp: Storfors. Hammarö.", tts.lastAnnouncement)
        graph.settings.update { it.copy(detail = AnnouncementDetail.FULL) }
        graph.controller.next()
        // The apartment number is not said.
        assertEquals("Nästa stopp: Björkvägen 7, Hammarö. Det är sista stoppet.", tts.lastAnnouncement)
        graph.controller.next()
        assertEquals("Rutten är klar.", tts.lastAnnouncement)
        idle()
        assertNull(graph.controller.route.value)
        // Persistence runs on a background thread: wait for the file to be deleted.
        val file = java.io.File(app.noBackupFilesDir, RouteRepository.FILE_NAME)
        repeat(100) { if (file.exists()) Thread.sleep(20) }
        assertFalse(file.exists())
    }

    @Test
    fun englishRepeatIsQueuedAfterSwedish() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.forLanguageTag("en-US"))
        val tts = readyTts()
        graph.settings.update { it.copy(englishRepeat = true) }
        graph.controller.addManual("Storgatan 14, 65224 Karlstad")
        graph.controller.addManual("Kungsgatan 5, Kil")
        idleUntil { graph.controller.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        graph.controller.start()
        idle()
        assertEquals("Next stop: Storgatan 14, Karlstad. Kungsgatan 5, Kil.", tts.lastAnnouncement)
        assertEquals(TextToSpeech.QUEUE_ADD, tts.queueMode)
        graph.settings.update { it.copy(englishRepeat = false) }
        graph.controller.end()
    }

    @Test
    fun mapsBatchesAutomaticallyAfterTenStops() {
        repeat(23) { graph.controller.addManual("Gata ${it + 1}, Karlstad") }
        idleUntil { graph.controller.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        graph.controller.start()
        idle()
        val first = shadowOf(app).nextStartedActivity.dataString!!
        assertTrue(first, first.contains("destination=Gata%2010%2C%20Karlstad"))
        repeat(9) { graph.controller.next() }
        assertNull("no relaunch mid-batch", shadowOf(app).nextStartedActivity)
        graph.controller.next() // completes stop 10 → next batch 11..20
        idle()
        val second = shadowOf(app).nextStartedActivity.dataString!!
        assertTrue(second, second.contains("destination=Gata%2020%2C%20Karlstad"))
        assertTrue(second, second.contains("waypoints=Gata%2011%2C%20Karlstad"))
        repeat(10) { graph.controller.next() }
        idle()
        val third = shadowOf(app).nextStartedActivity.dataString!!
        assertTrue(third, third.contains("destination=Gata%2023%2C%20Karlstad"))
        graph.controller.end()
    }

    @Test
    fun startAndNextAnnounceTheStopsInFull() {
        val tts = readyTts()
        graph.controller.addManual("Storgatan 14, 65224 Karlstad")
        graph.controller.addManual("Kungsgatan 5, 65225 Karlstad")
        graph.controller.addManual("Kungsgatan 5, 65225 Karlstad")
        idleUntil { graph.controller.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        // Pretend the geocoder located the stops (Robolectric has no geocoding backend), in Herrhagen
        // and Kronoparken, with the geocoder's districts wrong: Karlstad's own are said.
        val r = graph.controller.route.value!!
        val located = r.stops.mapIndexed { i, s ->
            val point = if (i == 0) 59.3759 to 13.51737 else 59.40938 to 13.58347
            s.copy(geoStatus = GeoStatus.LOCATED, geo = GeoPoint(point.first, point.second, "addr $i", locality = "Karlstad", subLocality = if (i == 0) "Lamberget" else "Herrhagen"))
        }
        graph.controller.restoreStops(located)
        graph.controller.start()
        idle()
        assertEquals("Nästa stopp: Storgatan 14, Herrhagen, Karlstad. Kungsgatan 5, Kronoparken.", tts.lastAnnouncement)
        // Only the driver's "Nästa" moves the route on (positions never do, see StreetServiceRoboTest).
        graph.controller.next()
        idle()
        assertEquals("Nästa stopp: Kungsgatan 5, Kronoparken, Karlstad. Kungsgatan 5, Kronoparken.", tts.lastAnnouncement)
        graph.controller.end()
        idle()
        assertNull(graph.controller.route.value)
    }

    /**
     * A place of care is shown on the passenger display by its own name and said in full: the
     * hospital written short ("C-Sjukhuset") and said with its entrance and town. A care home's
     * name is never shown or said, only its street (invented places, except the hospital).
     */
    @Test
    fun placesAreShownAndSaidByTheirPublicNameOnly() {
        val tts = readyTts()
        val cards = listOf(
            "08:00\nPick-up\nFrida Uppdiktad\nProvby Äldreboende Strandvägen 3, 66530 Kil\nRS\nCompensation 1 KR",
            "08:30\nDrop-off\nFrida Uppdiktad\nCentralsjukhuset huvudentrén,\nRS\nCompensation 1 KR",
            "09:00\nDrop-off\nErik Påhittad\nProvby Vårdcentral Skolgatan 5, 66530 Kil\nRS\nCompensation 1 KR",
        )
        val c = graph.controller
        assertEquals(3, c.importTrips(YouDriveCards.toAdd(YouDriveCards.parse(cards, graph.extractor))))
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        // The driver's screens show the place with the address.
        assertEquals("Provby Äldreboende · Strandvägen 3, 665 30 Kil", c.route.value!!.stops.first().shownAddress)
        assertTrue(c.start())
        idle()
        assertEquals("Nästa stopp: Klockan åtta. Strandvägen 3, Kil. Klockan åtta trettio. Centralsjukhuset, huvudentrén.", tts.lastAnnouncement)
        val display = c.display.value
        assertEquals("Strandvägen 3", display.current?.title)
        assertEquals(listOf("C-Sjukhuset Huvudentrén", "Provby Vårdcentral"), display.upcoming.map { it.title })
        assertEquals("Centralsjukhuset, huvudentrén", display.upcoming.first().said)
        // Shown and sent: never the home's name (only its YouDrive card, which the driver opens, has it).
        val shown = (listOfNotNull(display.current) + display.earlier + display.upcoming).flatMap { listOfNotNull(it.title, it.subtitle, it.said, it.place) }
        assertFalse("passenger display", (shown + display.announcementSv.orEmpty()).any { it.contains("Äldreboende") })
        c.next()
        idle()
        assertEquals("Nästa stopp: Klockan åtta trettio. Centralsjukhuset, huvudentrén, Karlstad. Klockan nio. Provby Vårdcentral, Skolgatan 5, Kil.", tts.lastAnnouncement)
        c.end()
    }

    /**
     * A YouDrive trip's whole card goes to the passenger display for the driver to open, and nowhere
     * else: never in an announcement, the route notification or the history (invented data).
     */
    @Test
    fun theTripCardGoesOnlyToTheDisplay() {
        val tts = readyTts()
        val cards = listOf(
            "08:00\nPick-up\nFrida Maria Uppdiktad\nStrandvägen 3, 66530 Kil\n0700000006\nportkod 1234\nCompensation 1 KR",
            "08:30\nDrop-off\nFrida Maria Uppdiktad\nSkolgatan 5, 66530 Kil\nRS\nCompensation 1 KR",
        )
        val c = graph.controller
        assertEquals(2, c.importTrips(YouDriveCards.toAdd(YouDriveCards.parse(cards, graph.extractor))))
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        assertTrue(c.start())
        idle()
        assertEquals(cards[0], c.display.value.current?.card)
        assertEquals(cards[1], c.display.value.upcoming.first().card)
        c.next()
        idle()
        for (secret in listOf("0700000006", "1234", "Maria", "Compensation")) {
            assertFalse("spoken: $secret", tts.lastAnnouncement.orEmpty().contains(secret))
            assertFalse("history: $secret", graph.history.entries.value.toString().contains(secret))
            assertFalse("announcement: $secret", c.display.value.announcementSv.orEmpty().contains(secret))
        }
        val notification = shadowOf(app.getSystemService(android.app.NotificationManager::class.java))
            .getNotification(se.eldebosh.nastastopp.service.Notifications.ID_ROUTE)
        @Suppress("DEPRECATION")
        val extras = notification.extras.keySet().map { notification.extras.get(it)?.toString().orEmpty() }
        assertFalse("notification", extras.any { it.contains("0700000006") || it.contains("1234") })
        c.end()
    }

    /**
     * The order the driver sets on the tablet's map: the trips take the places they held, in his
     * order; the new next stop is said. A passenger's pick-up and drop-off share a number, never
     * the name; an order naming a trip that is not there, or dropping a passenger off before they
     * are picked up, is ignored.
     */
    @Test
    fun theTabletsOrderIsTaken() {
        val tts = readyTts()
        val cards = listOf(
            "08:00\nPick-up\nFrida Maria Uppdiktad\nStrandvägen 3, 66530 Kil\nCompensation 1 KR",
            "08:20\nPick-up\nErik Påhittad\nStorgatan 14, 65224 Karlstad\nCompensation 1 KR",
            "08:30\nDrop-off\nFrida Maria Uppdiktad\nSkolgatan 5, 66530 Kil\nCompensation 1 KR",
        )
        val c = graph.controller
        assertEquals(3, c.importTrips(YouDriveCards.toAdd(YouDriveCards.parse(cards, graph.extractor))))
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        assertTrue(c.start())
        idle()
        val shown = listOf(c.display.value.current!!) + c.display.value.upcoming
        assertEquals(shown[0].rider, shown[2].rider)
        assertNotEquals(shown[0].rider, shown[1].rider)
        val ids = c.route.value!!.stops.map { it.id }
        c.reorder(listOf(ids[1], ids[0]))
        idle()
        assertEquals(listOf(ids[1], ids[0], ids[2]), c.route.value!!.stops.map { it.id })
        assertTrue(tts.lastAnnouncement.orEmpty(), tts.lastAnnouncement.orEmpty().startsWith("Nästa stopp: Klockan åtta tjugo. Storgatan 14"))
        c.reorder(listOf(ids[0], 999L))
        assertEquals(listOf(ids[1], ids[0], ids[2]), c.route.value!!.stops.map { it.id })
        // Frida dropped off before she is picked up: not taken.
        c.reorder(listOf(ids[2], ids[0]))
        assertEquals(listOf(ids[1], ids[0], ids[2]), c.route.value!!.stops.map { it.id })
        c.end()
    }

    /**
     * The driver's entrance for an address: Google Maps navigates to it by its point (never a
     * search for the address), the tablet's map shows it, and the same address finds it again on
     * another day; it never replaces the address's own point (invented address and point).
     */
    @Test
    fun theDriversEntranceIsWhereMapsGoes() {
        val c = graph.controller
        assertTrue(c.addManual("Strandvägen 3, 665 30 Kil", "08:00"))
        assertTrue(c.addManual("Storgatan 14, 652 24 Karlstad", "08:30"))
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        val stop = c.route.value!!.stops.first()
        // Not located here (Robolectric's geocoder never answers): Google Maps gets the address.
        c.navigateTo(stop)
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=Strandv%C3%A4gen%203%2C%20665%2030%20Kil&travelmode=driving&dir_action=navigate",
            shadowOf(app).nextStartedActivity.dataString,
        )
        c.setEntrance(stop, 59.381234 to 13.501234, " Från gården ")
        idle()
        assertEquals("Från gården", c.entranceOf(stop)?.note)
        assertEquals("59.381234,13.501234", c.mapsDestination(stop))
        assertNull("the address's own point stays apart", c.route.value!!.stops.first().geo)
        c.navigateTo(stop)
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=59.381234%2C13.501234&travelmode=driving&dir_action=navigate",
            shadowOf(app).nextStartedActivity.dataString,
        )
        c.streetViewAt(stop)
        assertEquals(
            "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=59.381234%2C13.501234",
            shadowOf(app).nextStartedActivity.dataString,
        )
        assertTrue(c.start())
        idle()
        val batch = shadowOf(app).nextStartedActivity.dataString!!
        assertTrue(batch, batch.contains("waypoints=59.381234%2C13.501234") && batch.contains("Storgatan"))
        // The tablet's map shows the entrance too.
        assertEquals(59.381234, c.display.value.current?.lat ?: 0.0, 0.0)
        // Another day, the same address: its entrance comes with it.
        c.end()
        idle()
        assertTrue(c.addManual("STRANDVÄGEN 3, 66530 KIL", "09:00"))
        assertEquals("Från gården", c.entranceOf(c.route.value!!.stops.first())?.note)
        c.setEntrance(c.route.value!!.stops.first(), null, " ")
        assertNull(c.entranceOf(c.route.value!!.stops.first()))
        c.clearEntrances()
        c.clear()
    }

    /** The hospital typed with its town is said once with it, never "Karlstad, Karlstad", and written short. */
    @Test
    fun theHospitalsTownIsSaidOnce() {
        val tts = readyTts()
        val c = graph.controller
        assertTrue(c.addManual("Centralsjukhuset Karlstad", "13:33"))
        assertTrue(c.addManual("Storgatan 14, 65224 Karlstad", "14:00"))
        idleUntil { c.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        assertTrue(c.start())
        idle()
        assertEquals("Nästa stopp: Klockan tretton trettiotre. Centralsjukhuset, Karlstad. Klockan fjorton. Storgatan 14, Karlstad.", tts.lastAnnouncement)
        assertEquals("C-Sjukhuset", c.display.value.current?.title)
        assertEquals("C-Sjukhuset Karlstad", c.route.value!!.stops.first().shownAddress)
        c.end()
    }

    /** The town the driver gives a place written without one comes with it the next time. */
    @Test
    fun theTownGivenToAPlaceIsRemembered() {
        val card = listOf("10:37\nDrop-off\nCecilia Maria Exempel\nSjukhuset huvudentrén,\nHLI\nCompensation 1 KR")
        val c = graph.controller
        assertEquals(1, c.importTrips(YouDriveCards.toAdd(YouDriveCards.parse(card, graph.extractor))))
        val stop = c.route.value!!.stops.single()
        assertTrue(stop.townUnknown)
        assertNull(stop.parsedTown)
        assertTrue(c.editText(stop.id, "Sjukhuset Huvudentrén, Karlstad", stop.time))
        c.clear()
        idle()
        assertEquals(1, c.importTrips(YouDriveCards.toAdd(YouDriveCards.parse(card, graph.extractor))))
        val again = c.route.value!!.stops.single()
        assertEquals("Karlstad", again.parsedTown)
        assertEquals("Sjukhuset Huvudentrén, Karlstad", again.candidates.first())
        assertEquals("Sjukhuset Huvudentrén", again.place)
        c.clear()
    }

    @Test
    fun routeDataExpiresAfter12Hours() {
        val repo = RouteRepository(app)
        val now = System.currentTimeMillis()
        val stop = Stop(1, "Storgatan 14, 652 24 Karlstad", listOf("Storgatan 14, 652 24 Karlstad"))
        repo.save(RouteData(createdAtMs = now - 11 * 3_600_000L, stops = listOf(stop), nextId = 2))
        assertNotNull(repo.load(now))
        repo.save(RouteData(createdAtMs = now - 13 * 3_600_000L, stops = listOf(stop), nextId = 2))
        assertNull(repo.load(now))
        assertFalse(java.io.File(app.noBackupFilesDir, RouteRepository.FILE_NAME).exists())
    }

    @Test
    fun routeSurvivesSaveAndLoad() {
        val repo = RouteRepository(app)
        val stop = Stop(7, "Björkvägen 7, 663 41 Hammarö", listOf("Björkvägen 7, 663 41 Hammarö"), "663 41", "Hammarö", true)
        val data = RouteData(createdAtMs = System.currentTimeMillis(), active = true, stops = listOf(stop), nextId = 8, batchEndStopId = 7)
        repo.save(data)
        assertEquals(data, repo.load())
        repo.clear()
    }
}
