package se.eldebosh.nastastopp.robo

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowTextToSpeech
import org.robolectric.shadows.ShadowWindowManagerImpl
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.AppGraph
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.geo.GeoResult
import se.eldebosh.nastastopp.core.geo.StreetInfo
import se.eldebosh.nastastopp.core.geo.StreetMapBuilder
import se.eldebosh.nastastopp.core.geo.Fix
import se.eldebosh.nastastopp.geo.CurrentStreet
import se.eldebosh.nastastopp.geo.StreetCaller
import se.eldebosh.nastastopp.route.model.GeoStatus
import se.eldebosh.nastastopp.service.Notifications
import se.eldebosh.nastastopp.service.RouteActionReceiver
import se.eldebosh.nastastopp.util.LocaleHelper
import se.eldebosh.nastastopp.util.TimeLabels
import java.time.Duration
import java.util.Locale

/** Back (undo "Nästa"), the current street, the waiting timer and the floating panel (Android 13). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class FloatingPanelRoboTest {

    private lateinit var app: App
    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        graph = app.graph
        graph.controller.clear()
        graph.history.clear()
        graph.settings.update { it.copy(overlayHidden = false, overlayMinimized = false) }
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun settle() {
        repeat(2_000) {
            if (graph.controller.route.value?.stops?.none { it.geoStatus == GeoStatus.PENDING } != false) return
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        }
    }

    private fun readyTts(): ShadowTextToSpeech {
        ShadowTextToSpeech.addLanguageAvailability(Locale.forLanguageTag("sv-SE"))
        graph.announcer.recheck()
        val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS)
        idle()
        return shadow
    }

    private fun threeStops() {
        graph.controller.addManual("Storgatan 14, 65224 Karlstad", "12:30")
        graph.controller.addManual("Järnvägsgatan 3B, 68830 Storfors", "12:45")
        graph.controller.addManual("Lindvägen 9, 66430 Grums", "13:40")
        settle()
    }

    @Test
    fun backUndoesNextAndItsHistoryEntry() {
        val tts = readyTts()
        threeStops()
        val c = graph.controller
        c.start()
        idle()
        assertFalse("nothing to go back to", c.back())
        shadowOf(app).nextStartedActivity // Maps from start
        c.next()
        idle()
        assertEquals(1, c.route.value!!.completed.size)
        assertEquals(1, graph.history.entries.value.size)

        assertTrue(c.back())
        idle()
        val r = c.route.value!!
        assertTrue(r.completed.isEmpty())
        assertEquals(listOf("12:30", "12:45", "13:40"), r.stops.map { it.time })
        assertTrue("history entry removed", graph.history.entries.value.isEmpty())
        assertEquals("Nästa stopp: Storgatan 14, Karlstad. Därefter: Järnvägsgatan 3B, Storfors.", tts.lastSpokenText)
        assertNull("Maps already has this stop (mid-batch)", shadowOf(app).nextStartedActivity)
        c.end()
    }

    @Test
    fun backAfterBatchRelaunchesMapsFromTheRestoredStop() {
        repeat(12) { graph.controller.addManual("Gata ${it + 1}, Karlstad") }
        settle()
        val c = graph.controller
        c.start()
        idle()
        shadowOf(app).nextStartedActivity
        repeat(10) { c.next() } // completes stop 10 → Maps gets 11..12
        idle()
        assertTrue(shadowOf(app).nextStartedActivity.dataString!!.contains("destination=Gata%2012%2C%20Karlstad"))
        assertTrue(c.back())
        idle()
        val maps = shadowOf(app).nextStartedActivity.dataString!!
        assertTrue(maps, maps.contains("waypoints=Gata%2010%2C%20Karlstad"))
        assertEquals("Gata 10, Karlstad", c.route.value!!.stops.first().displayText)
        // Maps now starts at stop 10, so going back to stop 9 launches it again from there.
        assertTrue(c.back())
        idle()
        val again = shadowOf(app).nextStartedActivity.dataString!!
        assertTrue(again, again.contains("waypoints=Gata%209%2C%20Karlstad"))
        // Forward again inside that batch: no relaunch, and back stays inside it too.
        c.next()
        c.back()
        idle()
        assertNull(shadowOf(app).nextStartedActivity)
        c.end()
    }

    @Test
    fun speakStreetOnlyOnRequestAndNotToDisplays() {
        val tts = readyTts()
        threeStops()
        graph.controller.start()
        idle()
        val sent = mutableListOf<String>()
        val job = graph.scope.launch { graph.controller.announcements.collect { sent += it.swedish } }
        assertFalse(graph.controller.speakStreet(null))
        assertTrue(graph.controller.speakStreet(StreetInfo("Drottninggatan", "Centrum")))
        idle()
        assertEquals("Drottninggatan, Centrum", tts.lastSpokenText)
        assertTrue("not forwarded to passenger displays", sent.isEmpty())
        job.cancel()
        graph.controller.end()
    }

    /** 1.5: tapping the next stop's street on the panel says the street and number, nothing more. */
    @Test
    fun tappingTheStopsStreetSaysStreetAndNumberOnly() {
        val tts = readyTts()
        ShadowSettings.setCanDrawOverlays(true)
        graph.controller.addExtracted(graph.extractor.extract(listOf("2026-09-29", "07:36", "Pick-up", "Bengt Provare", "Brattgårdsgatan 4, 66452 Vålberg")))
        settle()
        assertEquals("Bengt Provare", graph.controller.route.value!!.stops.single().name)
        assertFalse("no route yet", graph.controller.speakStopStreet())
        graph.controller.start()
        idle()
        val spokenBefore = tts.lastSpokenText
        val sent = mutableListOf<String>()
        val job = graph.scope.launch { graph.controller.announcements.collect { sent += it.swedish } }
        overlayView(R.string.overlay_stop_street_desc).performClick()
        idle()
        assertEquals("Brattgårdsgatan 4", tts.lastSpokenText) // never the passenger's name
        assertTrue(spokenBefore != tts.lastSpokenText)
        assertTrue("not forwarded to passenger displays", sent.isEmpty())
        job.cancel()
        graph.controller.end()
    }

    /** 1.7: the street's name is said when it changes, after any announcement, and only when switched on. */
    @Test
    fun theStreetIsSaidWhenItChangesUnlessSwitchedOff() {
        val tts = readyTts()
        threeStops()
        graph.controller.start()
        idle()
        var answer = "Drottninggatan"
        // The geocoder finds an address right where the vehicle is.
        val street = CurrentStreet(graph.scope) { lat, lng -> listOf(GeoResult(lat, lng, null, null, "Karlstad", "Centrum", answer)) }
        StreetCaller(street, graph.controller, graph.scope)
        street.want("test", true)
        fun fix(s: Long, m: Double) = Fix(s * 1000, 59.38 + m / 111_195.0, 13.5, 10f, 5f)

        street.onFix(fix(0, 0.0))
        idle()
        assertTrue("one reading is not enough (1.8)", tts.lastSpokenText!!.startsWith("Nästa stopp"))
        street.onFix(fix(10, 0.0)) // the confirming reading, also when standing still
        idle()
        assertEquals("Drottninggatan", tts.lastSpokenText)
        assertEquals("queued after an announcement, never over it", TextToSpeech.QUEUE_ADD, tts.queueMode)
        graph.controller.repeat()
        street.onFix(fix(20, 100.0)) // the same street again: not said again
        idle()
        assertTrue(tts.lastSpokenText!!.startsWith("Nästa stopp"))
        answer = "Kungsgatan"
        street.onFix(fix(40, 200.0))
        idle()
        street.onFix(fix(50, 200.0))
        idle()
        assertEquals("Kungsgatan", tts.lastSpokenText)

        graph.settings.update { it.copy(sayStreetChanges = false) }
        graph.controller.repeat()
        answer = "Västra Torggatan"
        street.onFix(fix(60, 300.0))
        idle()
        street.onFix(fix(70, 300.0))
        idle()
        assertTrue("switched off", tts.lastSpokenText!!.startsWith("Nästa stopp"))
        graph.settings.update { it.copy(sayStreetChanges = true) }
        graph.controller.end()
    }

    /** 1.7: the speed from the positions, shown on the panel, gone when the positions stop. */
    @Test
    fun theSpeedIsShownWhilePositionsComeIn() {
        var now = 1_000_000L
        val street = CurrentStreet(graph.scope, clockMs = { now }) { _, _ -> null }
        assertNull(street.speedNow())
        street.onFix(Fix(0, 59.38, 13.5, 12.5f, 5f))
        assertEquals(45, street.speedNow())
        street.onFix(Fix(2_000, 59.38, 13.5, null, 5f)) // no speed in this fix: worked out, it did not move
        assertEquals(0, street.speedNow())
        now += 11_000
        assertNull("no recent position", street.speedNow())

        // Without a speed in the fixes (some phones): worked out from two exact positions 2 s apart.
        val derived = CurrentStreet(graph.scope, clockMs = { now }) { _, _ -> null }
        derived.onFix(Fix(0, 59.38, 13.5, null, 5f))
        derived.onFix(Fix(2_000, 59.38 + 25.0 / 111_195.0, 13.5, null, 5f))
        assertEquals(45, derived.speedNow())

        // On the panel (1.8): the number alone in its own circle; "–" until a position has a speed.
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        ShadowSettings.setCanDrawOverlays(true)
        threeStops()
        graph.controller.start()
        idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val speed = wm.views.single().findViewById<TextView>(R.id.ref_15)
        assertEquals(View.VISIBLE, speed.visibility)
        assertEquals("–", speed.text.toString())
        graph.street.onFix(Fix(System.currentTimeMillis(), 59.38, 13.5, 12.5f, 5f))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals("45", speed.text.toString())
        graph.controller.end()
    }

    /** 1.7: the speaker on the street bar switches the street speech on and off. */
    @Test
    fun theStreetBarsSpeakerSwitchesStreetSpeech() {
        ShadowSettings.setCanDrawOverlays(true)
        threeStops()
        graph.controller.start()
        idle()
        assertTrue(graph.settings.current.sayStreetChanges)
        overlayView(R.string.overlay_say_street_on).performClick()
        idle()
        assertFalse(graph.settings.current.sayStreetChanges)
        overlayView(R.string.overlay_say_street_off).performClick()
        idle()
        assertTrue(graph.settings.current.sayStreetChanges)
        graph.controller.end()
    }

    /** 1.8: with the offline street map the road comes from the map, on every position, and the geocoder gives only the area. */
    @Test
    fun withTheStreetMapTheRoadComesFromTheMap() {
        val lat0 = 59.38
        val east = { m: Double -> 13.5 + m / (111_195.0 * Math.cos(Math.toRadians(lat0))) }
        val map = StreetMapBuilder().apply {
            add(1, "Testgatan", listOf(lat0 to east(-300.0), lat0 to east(300.0)))
        }.build(0)
        // The geocoder's nearest address is on another street: with the map it is not used for the street.
        val street = CurrentStreet(graph.scope) { lat, lng -> listOf(GeoResult(lat, lng, null, null, "Karlstad", "Centrum", "Sidogatan")) }
        street.map = map
        street.want("test", true)
        street.onFix(Fix(0, lat0 + 4 / 111_195.0, east(-100.0), 10f, 5f, 90f))
        idle()
        assertEquals(StreetInfo(null, "Centrum"), street.state.value)
        street.onFix(Fix(2_000, lat0 + 3 / 111_195.0, east(-80.0), 10f, 5f, 90f))
        idle()
        assertEquals(StreetInfo("Testgatan", "Centrum"), street.state.value)
        // A position far from every road of the map: no name is invented (after a short hold).
        street.onFix(Fix(4_000, lat0 + 200 / 111_195.0, east(-80.0), 10f, 5f, 90f))
        idle()
        assertEquals("Testgatan", street.state.value?.street)
        street.reset()
    }

    @Test
    fun currentStreetLooksUpOnlyWhenWantedAndThrottles() {
        val calls = mutableListOf<Pair<Double, Double>>()
        var answer: List<GeoResult>? = listOf(GeoResult(59.38, 13.5, null, null, "Karlstad", "Centrum", "Drottninggatan"))
        // The geocoder finds its address right where the vehicle is.
        val street = CurrentStreet(graph.scope) { lat, lng -> calls += lat to lng; answer?.map { it.copy(lat = lat, lng = lng) } }
        fun fix(s: Long, m: Double) = Fix(s * 1000, 59.38 + m / 111_195.0, 13.5, null, 5f)

        street.onFix(fix(0, 0.0))
        idle()
        assertTrue("nobody shows the street", calls.isEmpty())
        street.want("test", true)
        street.onFix(fix(1, 0.0))
        idle()
        assertEquals(1, calls.size)
        assertEquals("a street needs a second reading (1.8)", StreetInfo(null, "Centrum"), street.state.value)
        street.onFix(fix(5, 0.0))
        idle()
        assertEquals("throttled", 1, calls.size)
        street.onFix(fix(10, 0.0)) // the confirming reading, standing still
        idle()
        assertEquals(2, calls.size)
        assertEquals(StreetInfo("Drottninggatan", "Centrum"), street.state.value)
        street.onFix(fix(12, 100.0))
        street.onFix(fix(20, 5.0))
        idle()
        assertEquals("throttled", 2, calls.size)
        answer = null // geocoder failed: the last street stays
        street.onFix(fix(30, 200.0))
        idle()
        assertEquals(3, calls.size)
        assertEquals("Drottninggatan", street.state.value!!.street)
        street.reset()
        assertNull(street.state.value)
        street.want("test", false)
    }

    @Test
    fun timeLabels() {
        assertEquals(app.getString(R.string.time_in_min, "7"), TimeLabels.until(app, 7))
        assertEquals(app.getString(R.string.time_late_hm, "1", "5"), TimeLabels.until(app, -65))
        assertEquals(app.getString(R.string.time_now), TimeLabels.until(app, 0))
    }

    // ------------------------------------------------------------------------------------------
    // Floating panel

    private val wm get() = Shadow.extract<ShadowWindowManagerImpl>(app.getSystemService(WindowManager::class.java))

    /** The overlay builds its views with the in-app language, like this. */
    private fun ui(id: Int) = LocaleHelper.wrap(app, graph.settings.current.uiLanguage).getString(id)

    private fun find(root: View, description: String): View? {
        if (root.contentDescription == description) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), description)?.let { return it }
        return null
    }

    private fun overlayView(description: Int): View {
        assertEquals(1, wm.views.size)
        val view = find(wm.views.single(), ui(description))
        assertNotNull("view '${ui(description)}'", view)
        return view!!
    }

    private fun reminder() = shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(Notifications.ID_OVERLAY_HIDDEN)

    /** Found on the S20 Ultra (B2): the panel covered the passenger display. */
    @Test
    fun panelStaysAwayWhileThePassengerDisplayIsShown() {
        ShadowSettings.setCanDrawOverlays(true)
        threeStops()
        graph.controller.start()
        idle()
        assertEquals(1, wm.views.size)
        graph.overlay.suppress("passenger_display", true)
        idle()
        assertTrue("no panel over the passenger display", wm.views.isEmpty())
        assertNull("not the \"closed\" reminder: the driver did not close it", reminder())
        graph.overlay.suppress("passenger_display", false)
        idle()
        assertEquals(1, wm.views.size)
        graph.controller.end()
    }

    /** Found on the S20 Ultra (V5): the faded Back at stop 1 let the map show through it. */
    @Test
    fun backAtFirstStopDimsOnlyItsContent() {
        ShadowSettings.setCanDrawOverlays(true)
        threeStops()
        val c = graph.controller
        c.start()
        idle()
        fun content(v: View) = (v as ViewGroup).let { b -> (0 until b.childCount).map { b.getChildAt(it).alpha } }
        val back = overlayView(R.string.overlay_back)
        assertEquals("the button itself stays opaque", 1f, back.alpha)
        assertTrue("icon and caption dimmed", content(back).all { it < 1f })
        overlayView(R.string.overlay_next).performClick()
        idle()
        assertTrue("full again once there is something to go back to", content(overlayView(R.string.overlay_back)).all { it == 1f })
        c.end()
    }

    @Test
    fun panelBackNextMinimiseCloseAndQuickRestore() {
        ShadowSettings.setCanDrawOverlays(true)
        threeStops()
        val c = graph.controller
        c.start()
        idle()

        overlayView(R.string.overlay_next).performClick()
        idle()
        assertEquals(1, c.route.value!!.completedCount)
        overlayView(R.string.overlay_back).performClick()
        idle()
        assertEquals(0, c.route.value!!.completedCount)
        overlayView(R.string.overlay_street_desc)
        overlayView(R.string.overlay_stop_street_desc)
        assertTrue("panel shows the street", graph.street.isWanted)

        // "–" → small bubble; tap → full panel again.
        overlayView(R.string.overlay_minimize_desc).performClick()
        idle()
        assertTrue(graph.settings.current.overlayMinimized)
        assertFalse("no street lookups while minimised", graph.street.isWanted)
        overlayView(R.string.overlay_expand_desc).performClick()
        idle()
        assertFalse(graph.settings.current.overlayMinimized)
        overlayView(R.string.overlay_next)

        // "×" → closed, a notification brings it back with one tap.
        overlayView(R.string.overlay_close_desc).performClick()
        idle()
        assertTrue(graph.settings.current.overlayHidden)
        assertTrue(wm.views.isEmpty())
        assertNotNull(reminder())
        RouteActionReceiver().onReceive(app, Intent(RouteActionReceiver.ACTION_SHOW_OVERLAY))
        idle()
        assertFalse(graph.settings.current.overlayHidden)
        assertEquals(1, wm.views.size)
        assertNull(reminder())

        c.end()
        idle()
        assertTrue(wm.views.isEmpty())
        assertNull(reminder())
    }
}
