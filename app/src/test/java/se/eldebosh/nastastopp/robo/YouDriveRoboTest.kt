package se.eldebosh.nastastopp.robo

import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboWebSettings
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.AppGraph
import se.eldebosh.nastastopp.route.model.GeoStatus
import se.eldebosh.nastastopp.service.Notifications
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.youdrive.YouDriveWatcher
import java.time.Duration
import java.time.LocalTime

/** YouDrive page watching: alerts, applying changes to the route, and the 1.4.0 settings switch. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class YouDriveRoboTest {

    private lateinit var app: App
    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        graph = app.graph
        graph.controller.clear()
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun settle() {
        repeat(2_000) {
            if (graph.controller.route.value?.stops?.none { it.geoStatus == GeoStatus.PENDING } != false) return
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        }
    }

    private val noon = LocalTime.of(12, 0)

    private fun page(vararg trips: Pair<String, String>) =
        (listOf("YouDrive", "Idag") + trips.flatMap { (time, address) -> listOf("$time Hämtning", address, "SP1") }).joinToString("\n")

    private val karlstad = "12:30" to "ANDERSSON STORGATAN 14, 65224 KARLSTAD"
    private val storfors = "12:45" to "Järnvägsgatan 3B, 68830 Storfors"
    private val grums = "13:40" to "Lindvägen 9, 66430 Grums"
    private val hammaro = "12:40" to "Björkvägen 7, 66341 Hammarö"

    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun theYouDrivePageNeverGetsTheLocation() {
        // The app may hold location (to name the street), but YouDrive's page never gets it:
        // geolocation is off in its WebView and any request from the page is refused.
        val web = graph.youDrive.webView()
        assertFalse((web.settings as RoboWebSettings).geolocationEnabled)
        var answer: Pair<Boolean, Boolean>? = null
        shadowOf(web).webChromeClient!!.onGeolocationPermissionsShowPrompt("https://youdrive.example") { _, allow, retain ->
            answer = allow to retain
        }
        assertEquals(false to false, answer)
    }

    @Test
    fun addedAndCancelledTripsAlertAndCanBeApplied() {
        graph.settings.update { it.copy(youDriveWatch = true) }
        val w = graph.youDrive
        w.onPageText(page(karlstad, storfors, grums), nowTime = noon)
        assertEquals(YouDriveWatcher.Status.WATCHING, w.state.value.status)
        assertEquals(3, w.state.value.trips.size)
        assertTrue(w.state.value.changes.isEmpty())

        // Import all trips into a new list (time order, no names).
        assertEquals(3, graph.controller.importTrips(w.state.value.trips.mapNotNull { it.stop }))
        settle()
        assertEquals(listOf("12:30", "12:45", "13:40"), graph.controller.route.value!!.stops.map { it.time })
        assertEquals("already in the list", 0, graph.controller.importTrips(w.state.value.trips.mapNotNull { it.stop }))

        // Storfors cancelled, Hammarö added (seen twice → reported).
        w.onPageText(page(karlstad, hammaro, grums), nowTime = noon)
        assertTrue(w.state.value.changes.isEmpty())
        w.onPageText(page(karlstad, hammaro, grums), nowTime = noon)
        val changes = w.state.value.changes
        assertEquals(listOf(true to "12:40", false to "12:45"), changes.map { it.change.added to it.change.trip.time })
        assertEquals(2, notifications().count { it.channelId == Notifications.CHANNEL_TRIPS })

        // Apply both: the new trip goes in time order, the cancelled one is removed.
        changes.forEach { ch ->
            val stop = ch.change.trip.stop!!
            assertTrue(if (ch.change.added) graph.controller.insertTrip(stop) else graph.controller.removeTrip(stop))
            w.dismiss(ch.id)
        }
        settle()
        assertEquals(listOf("12:30", "12:40", "13:40"), graph.controller.route.value!!.stops.map { it.time })
        assertTrue(w.state.value.changes.isEmpty())
        graph.settings.update { it.copy(youDriveWatch = false) }
    }

    @Test
    fun activeRouteKeepsItsCurrentTripAndAnnouncesOnlyWhenTheNextOnesChange() {
        graph.controller.addManual("Storgatan 14, 65224 Karlstad", "12:30")
        graph.controller.addManual("Lindvägen 9, 66430 Grums", "13:40")
        settle()
        graph.controller.start()
        idle()
        val early = graph.extractor.fromManualText("Björkvägen 7, 66341 Hammarö")!!.copy(time = "12:00")
        assertTrue(graph.controller.insertTrip(early))
        assertEquals("current trip stays first", listOf("12:30", "12:00", "13:40"), graph.controller.route.value!!.stops.map { it.time })
        assertFalse("not twice", graph.controller.insertTrip(early))
        graph.controller.end()
    }

    @Test
    fun noAlertsWhileNotWatchingAndLoginPageIsRecognised() {
        val w = graph.youDrive
        w.onPageText(page(karlstad), nowTime = noon)
        w.onPageText(page(karlstad, grums), nowTime = noon)
        w.onPageText(page(karlstad, grums), nowTime = noon)
        assertEquals(1, w.state.value.changes.size)
        assertTrue("no notification when watching is off", notifications().none { it.channelId == Notifications.CHANNEL_TRIPS })
        // A reloading page shows its login for a moment: the status only changes when it stays.
        val t0 = 1_000_000L
        w.onPageText("Logga in\nBankID", nowMs = t0, nowTime = noon)
        assertEquals(YouDriveWatcher.Status.WATCHING, w.state.value.status)
        w.onPageText("Logga in\nBankID", nowMs = t0 + 30_000, nowTime = noon)
        assertEquals(YouDriveWatcher.Status.LOGGED_OUT, w.state.value.status)
        assertEquals("trips kept while logged out", 2, w.state.value.trips.size)
        w.dismissAll()
    }

    @Test
    fun anotherViewOrDayRaisesNoAlerts() {
        graph.settings.update { it.copy(youDriveWatch = true) }
        val w = graph.youDrive
        w.onPageText(page(karlstad, storfors, grums), nowTime = noon)
        // The driver opens tomorrow's list (or one trip's details): none of today's trips is left.
        val tomorrow = page("08:15" to "Kyrkogatan 2, 65224 Karlstad", "09:30" to "Skolgatan 5, 66430 Grums")
        w.onPageText(tomorrow, nowTime = noon)
        w.onPageText(tomorrow, nowTime = noon)
        assertTrue(w.state.value.changes.isEmpty())
        assertEquals(2, w.state.value.trips.size)
        // Back to today: again no alerts.
        w.onPageText(page(karlstad, storfors, grums), nowTime = noon)
        w.onPageText(page(karlstad, storfors, grums), nowTime = noon)
        assertTrue(w.state.value.changes.isEmpty())
        assertTrue(notifications().none { it.channelId == Notifications.CHANNEL_TRIPS })
        graph.settings.update { it.copy(youDriveWatch = false) }
    }

    @Test
    fun upgradeSwitchesTheUiToEnglishAndShowsTheAddressOnTheDisplay() {
        val prefs = app.getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().putString(SettingsStore.K_LANG, "ar").putBoolean("display_full_address", false).commit()
        val s = SettingsStore(app).current
        assertEquals("en", s.uiLanguage)
        assertTrue(s.displayFullAddress)
        assertTrue(s.explanationsArabic)
        assertTrue(s.showRefNumbers)
        // Only once: a later choice of Arabic is kept.
        SettingsStore(app).update { it.copy(uiLanguage = "ar") }
        assertEquals("ar", SettingsStore(app).current.uiLanguage)
        assertNotNull(SettingsStore.readLanguage(app))
    }
}
