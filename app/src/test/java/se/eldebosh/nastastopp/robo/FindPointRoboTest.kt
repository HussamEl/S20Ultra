package se.eldebosh.nastastopp.robo

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowGeocoder
import org.robolectric.shadows.ShadowToast
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.core.display.DisplayItem
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.geo.GeocoderFailed
import se.eldebosh.nastastopp.geo.Geocoding
import se.eldebosh.nastastopp.geo.PointFinder
import se.eldebosh.nastastopp.route.AddressLog
import se.eldebosh.nastastopp.route.PlaceMemory
import se.eldebosh.nastastopp.route.TripHistory
import se.eldebosh.nastastopp.route.model.GeoPoint
import se.eldebosh.nastastopp.route.model.GeoStatus
import se.eldebosh.nastastopp.route.model.RouteData
import se.eldebosh.nastastopp.route.model.Stop
import se.eldebosh.nastastopp.settings.Appearance
import se.eldebosh.nastastopp.settings.WindowPlaces
import se.eldebosh.nastastopp.ui.PointText
import se.eldebosh.nastastopp.ui.screens.PassengerDisplayScreen
import se.eldebosh.nastastopp.ui.screens.ReviewScreen
import se.eldebosh.nastastopp.ui.theme.NastaTheme
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * The points on the driver's screens (322: a tap copies them) and the find tool (323–333): which
 * point shows where, what the clipboard gets, what is kept, and that a town is never guessed
 * (invented addresses and points from testdata/README.md; the register's own addresses).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class FindPointRoboTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var app: App

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Geocoding.register(app)
    }

    private val point = "59.381234, 13.501234"

    private fun clip() = app.getSystemService(ClipboardManager::class.java).primaryClip

    /** A context whose own files are in [dir], so a test reads and writes them alone. */
    private fun filesIn(dir: File): Context = object : ContextWrapper(app) {
        override fun getNoBackupFilesDir(): File = dir
        override fun getApplicationContext(): Context = this
    }

    private fun freshDir(name: String) = File(app.cacheDir, name).apply { deleteRecursively(); mkdirs() }

    @Test
    fun aTapCopiesThePointAsItIsShownAndMarkedSensitive() {
        compose.setContent { NastaTheme(Appearance.DAY) { PointText(59.381234, 13.501234) } }
        compose.onNodeWithText(point).assertExists()
        compose.onNodeWithTag("ref_322").performClick()
        compose.waitForIdle()
        val clip = clip()
        assertNotNull(clip)
        assertEquals(point, clip!!.getItemAt(0).text.toString())
        assertTrue("the system never previews it", clip.description.extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
        // Android 13 confirms a copy itself: nothing of ours appears.
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun theReviewRowShowsALocatedStopsPointAndNoneWhilePending() {
        val located = Stop(
            1, "Storgatan 14, 652 24 Karlstad", listOf("Storgatan 14, 652 24 Karlstad"),
            geoStatus = GeoStatus.LOCATED, geo = GeoPoint(59.381234, 13.501234),
        )
        val pending = Stop(2, "Lindvägen 9, 664 30 Grums", listOf("Lindvägen 9, 664 30 Grums"))
        val lost = Stop(3, "Kyrkogatan 2, 652 24 Karlstad", listOf("Kyrkogatan 2, 652 24 Karlstad"), geoStatus = GeoStatus.NOT_LOCATED)
        val route = RouteData(createdAtMs = 0, stops = listOf(located, pending, lost))
        compose.setContent {
            NastaTheme(Appearance.DAY) {
                ReviewScreen(
                    route = route,
                    spokenName = { "Karlstad" },
                    importing = false,
                    onBack = {},
                    onMove = { _, _ -> },
                    onDelete = {},
                    onDeleteAbove = {},
                    onEdit = { _, _, _ -> true },
                    onRetry = {},
                    onAddManual = { _, _ -> true },
                    onSortByTime = {},
                    onAddScreenshots = {},
                    onStart = {},
                    onBackToRoute = {},
                )
            }
        }
        compose.onAllNodesWithTag("ref_322").assertCountEquals(1)
        compose.onNodeWithText(point).assertExists()
        // The edit dialog shows the address's own point under the address.
        // (A tap on the address itself: the point beside it is a control of its own.)
        compose.onNodeWithText("Storgatan 14, 652 24 Karlstad", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("ref_53").assertExists()
        compose.onNode(hasTestTag("ref_322") and hasAnyAncestor(isDialog())).assertExists()
    }

    @Test
    fun anOldHistoryLoadsAndANewTripKeepsItsPoint() {
        val dir = freshDir("history")
        File(dir, TripHistory.FILE_NAME).writeText(
            """{"entries":[{"id":1,"time":"12:00","area":"Grums","displayText":"Lindvägen 9, 664 30 Grums","atMs":${System.currentTimeMillis()},"done":true}],"nextId":2}""",
        )
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        val old = TripHistory(filesIn(dir), app.graph.settings, scope)
        val entry = old.entries.value.single()
        assertEquals("Lindvägen 9, 664 30 Grums", entry.displayText)
        assertNull(entry.lat)
        old.add("12:30", "Karlstad", "Storgatan 14, 652 24 Karlstad", done = true, point = 59.381234 to 13.501234)
        val file = File(dir, TripHistory.FILE_NAME)
        repeat(100) { if (!file.readText().contains("13.501234")) Thread.sleep(20) }
        val again = TripHistory(filesIn(dir), app.graph.settings, scope).entries.value
        assertEquals(listOf(null, 59.381234), again.map { it.lat })
        assertEquals(13.501234, again.last().lng!!, 1e-9)
    }

    @Test
    fun anOldAddressLogLoadsAndThePointIsAddedOnceFound() {
        val dir = freshDir("address_log")
        val text = "Storgatan 14, 652 24 Karlstad"
        File(dir, AddressLog.FILE).writeText("""{"${TextNorm.key(text)}":{"text":"$text","days":2,"lastDay":"2026-10-17"}}""")
        val log = AddressLog(filesIn(dir))
        assertNull(log.all.value.values.single().lat)
        val today = LocalDate.parse("2026-10-18")
        log.record(listOf(AddressLog.Line(text)), today)
        assertEquals("$text\t\t3\t2026-10-18", log.asText())
        // Found later the same day: the point is added, the day not counted again.
        log.record(listOf(AddressLog.Line(text, 59.381234, 13.501234)), today)
        assertEquals("$text\t$point\t3\t2026-10-18", log.asText())
        assertEquals(59.381234, AddressLog(filesIn(dir)).all.value.values.single().lat!!, 1e-9)
    }

    /** The register's own addresses and points (Lantmäteriet's, bundled): found only where the written town is. */
    @Test
    fun theFinderFindsTheRegistersOnePlaceAndNeverGuessesATown() = runBlocking {
        val finder = app.graph.pointFinder
        val register = Geocoding.register(app)
        val torggatan = finder.find("Västra Torggatan 12, Karlstad") as PointFinder.Answer.Found
        assertEquals(register.find("Västra Torggatan 12", null, "Karlstad")!!.lat, torggatan.lat, 1e-9)
        assertTrue(torggatan.address, torggatan.address.startsWith("Västra Torggatan 12"))
        val storfors = finder.find("Järnvägsgatan 6, Storfors") as PointFinder.Answer.Found
        assertEquals(59.53086, storfors.lat, 1e-9)
        // Without the geocoder, only the register: a street in two towns, written without one, is
        // not found (never one of them); a postcode the register does not have for it neither.
        ShadowGeocoder.setIsPresent(false)
        try {
            assertEquals(PointFinder.Answer.NotFound, finder.find("Järnvägsgatan 6"))
            assertEquals(PointFinder.Answer.NotFound, finder.find("Västra Torggatan 12, 652 24 Karlstad"))
            assertEquals(PointFinder.Answer.NotFound, finder.find("x"))
        } finally {
            ShadowGeocoder.setIsPresent(true)
        }
        // Nothing of the route is touched.
        assertNull(app.graph.controller.route.value)
    }

    /** A well-known place and a place the driver once gave a town are asked for in that town; a failed geocoder says so. */
    @Test
    fun theFinderAsksInTheKnownTownAndSaysWhenTheGeocoderCannotAnswer() = runBlocking {
        val asked = ArrayList<Triple<List<String>, String?, String?>>()
        val memory = object : PlaceMemory {
            override fun recall(place: String): String? = if (TextNorm.key(place) == TextNorm.key("Provby Vårdcentral")) "Provby Vårdcentral, 665 30 Kil" else null
            override fun remember(place: String, address: String) = Unit
        }
        val finder = PointFinder(app.graph.extractor, memory) { candidates, postal, town ->
            asked += Triple(candidates, postal, town)
            null
        }
        assertEquals(PointFinder.Answer.NotFound, finder.find("Centralsjukhuset Huvudentrén"))
        assertEquals("Centralsjukhuset, Karlstad", asked.last().first.first())
        assertEquals("Karlstad", asked.last().third)
        finder.find("Provby Vårdcentral")
        assertEquals("665 30", asked.last().second)
        val offline = PointFinder(app.graph.extractor, PlaceMemory.None) { _, _, _ -> throw GeocoderFailed() }
        assertEquals(PointFinder.Answer.NoAnswer, offline.find("Storgatan 14, 652 24 Karlstad"))
    }

    /**
     * The passenger display never writes a point (not even the next stop's); the driver's sign
     * (325, among the top line's signs) opens the find window (326), whose answer is copied with a
     * tap; the trip card he opens has the trip's point.
     */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun theDisplaysSignOpensTheFindWindow() {
        val places = WindowPlaces.InMemory()
        val card = "07:36\nPick-up\nAnna Testsson\nStorgatan 14, 65224 Karlstad\n0700000001\nSP1\nFTJ"
        val snapshot = DisplaySnapshot(
            active = true,
            current = DisplayItem("07:36", "Storgatan 14", "Karlstad", lat = 59.381234, lng = 13.501234, lastName = "Testsson", card = card),
            upcoming = listOf(DisplayItem("08:00", "Hamngatan 7", "Skoghall", lat = 59.391234, lng = 13.491234)),
        )
        val typed = ArrayList<String>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NastaTheme(Appearance.DAY) {
                PassengerDisplayScreen(
                    snapshot, status = "Galaxy S20", connected = true, onSpeak = {}, onExit = {},
                    time = { LocalTime.of(7, 20, 5) }, places = places,
                    onFind = { text ->
                        typed += text
                        PointFinder.Answer.Found("Storgatan 14, 652 24 Karlstad", 59.381234, 13.501234)
                    },
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue("no point for the passengers", compose.onAllNodesWithText(point).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("ref_325").assertDoesNotExist()
        compose.onNodeWithTag("ref_298").performTouchInput { click(Offset(width / 2f, height / 2f)) }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("ref_325").performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("ref_326").assertExists()
        compose.onNodeWithTag("ref_327").assertExists()
        compose.onNodeWithTag("ref_329").assertIsNotEnabled()
        compose.onNodeWithTag("ref_330").assertDoesNotExist()
        compose.onNodeWithTag("ref_328").performTextReplacement("storgatan 14 karlstad")
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("ref_329").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(listOf("storgatan 14 karlstad"), typed)
        compose.onNodeWithTag("ref_330").assertExists()
        compose.onNodeWithText("Storgatan 14, 652 24 Karlstad").assertExists()
        save("display_find_point", compose.onAllNodes(isDialog())[0].captureToImage().asAndroidBitmap())
        compose.onNode(hasTestTag("ref_322") and hasAnyAncestor(hasTestTag("ref_330"))).performClick()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(point, clip()!!.getItemAt(0).text.toString())
        // Its bar moves it, and it opens there next time.
        compose.onNodeWithTag("ref_332").performTouchInput { swipe(center, center + Offset(0f, 80f), 300) }
        compose.mainClock.advanceTimeBy(500)
        assertNotNull(places.place("find_point"))
        compose.onNodeWithTag("ref_331").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("ref_326").assertDoesNotExist()
        // The trip card the driver opens (235) has the trip's point under its address.
        compose.onNodeWithTag("ref_231", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNode(hasTestTag("ref_322") and hasAnyAncestor(hasTestTag("ref_235"))).assertExists()
        compose.onNodeWithText(point).assertExists()
        save("display_trip_card_point", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.mainClock.autoAdvance = true
    }

    /** Drawn for a look on the computer only (app/build/screenshots), never sent anywhere. */
    private fun save(name: String, bitmap: Bitmap) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
