package se.eldebosh.nastastopp.robo

import android.os.Looper
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.core.route.Announcement
import se.eldebosh.nastastopp.route.model.GeoStatus
import java.time.Duration

/** Trip times, completed trips, the passenger display state and the floating button (Android 13). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class DisplayFeaturesRoboTest {

    private lateinit var app: App

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.graph.controller.clear()
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun settle() {
        repeat(2_000) {
            if (app.graph.controller.route.value?.stops?.none { it.geoStatus == GeoStatus.PENDING } != false) return
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        }
    }

    private fun addFive() {
        val c = app.graph.controller
        c.addManual("Storgatan 14, 65224 Karlstad", "12:30")
        c.addManual("Järnvägsgatan 3B, 68830 Storfors", "12:45")
        c.addManual("Björkvägen 7, 66341 Hammarö", "13:05")
        c.addManual("Kungsgatan 5, Kil", null)
        c.addManual("Lindvägen 9, 66430 Grums", "13:40")
        settle()
    }

    @Test
    fun completedTripsStayWithTheirTimesAndDisplayShowsTheDoneAndUpcomingTrips() {
        addFive()
        val c = app.graph.controller
        c.start()
        idle()
        var d = c.display.value
        assertTrue(d.active)
        assertNull(d.previous)
        assertEquals("12:30", d.current?.time)
        // Street address with the house number (the default), the area under it.
        assertEquals("Storgatan 14", d.current?.title)
        assertEquals("Karlstad", d.current?.subtitle)
        assertEquals(listOf("Järnvägsgatan 3B", "Björkvägen 7", "Kungsgatan 5", "Lindvägen 9"), d.upcoming.map { it.title })
        assertEquals(listOf("Storfors", "Hammarö", "Kil"), d.upcoming.take(3).map { it.subtitle })
        assertEquals("Nästa stopp: Storgatan 14, Karlstad. Därefter: Järnvägsgatan 3B, Storfors.", d.announcementSv)

        c.next()
        idle()
        val r = c.route.value!!
        assertEquals(listOf("12:30"), r.completed.map { it.time })
        d = c.display.value
        assertEquals("Storgatan 14", d.previous?.title)
        assertEquals("12:30", d.previous?.time)
        assertTrue("done here", d.previous!!.doneHere)
        assertEquals(listOf("Storgatan 14"), d.earlier.map { it.title })
        assertEquals("Järnvägsgatan 3B", d.current?.title)
        assertEquals(listOf("Björkvägen 7", "Kungsgatan 5", "Lindvägen 9"), d.upcoming.map { it.title })

        // Area only, when the driver turns the address off.
        app.graph.settings.update { it.copy(displayFullAddress = false) }
        idle()
        assertEquals("Storfors", c.display.value.current?.title)
        assertNull(c.display.value.current?.subtitle)
        c.end()
    }

    @Test
    fun announcementsAreForwardedToDisplays() {
        addFive()
        val c = app.graph.controller
        val got = mutableListOf<Announcement>()
        val job = CoroutineScope(Dispatchers.Main.immediate).launch { c.announcements.collect { got += it } }
        c.start()
        c.next()
        c.repeat()
        idle()
        assertEquals(
            listOf(
                "Nästa stopp: Storgatan 14, Karlstad. Därefter: Järnvägsgatan 3B, Storfors.",
                "Nästa stopp: Järnvägsgatan 3B, Storfors. Därefter: Björkvägen 7, Hammarö.",
                "Nästa stopp: Järnvägsgatan 3B, Storfors. Därefter: Björkvägen 7, Hammarö.",
            ),
            got.map { it.swedish },
        )
        job.cancel()
        c.end()
    }

    @Test
    fun sortByTimeAndEditTime() {
        val c = app.graph.controller
        c.addManual("Storgatan 14, 65224 Karlstad", "13:10")
        c.addManual("Kungsgatan 5, Kil", null)
        c.addManual("Lindvägen 9, 66430 Grums", "09:05")
        settle()
        c.sortByTime()
        assertEquals(listOf("09:05", "13:10", null), c.route.value!!.stops.map { it.time })
        val kil = c.route.value!!.stops.last()
        assertTrue(c.editText(kil.id, kil.displayText, "08:00"))
        assertEquals("08:00", c.route.value!!.stops.last().time)
        assertEquals(GeoStatus.NOT_LOCATED, c.route.value!!.stops.last().geoStatus) // same text → not re-geocoded
        c.clear()
    }

    @Test
    fun floatingButtonCanBeHiddenAndShownAgain() {
        ShadowSettings.setCanDrawOverlays(true)
        addFive()
        app.graph.controller.start()
        idle()
        val wm = org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowWindowManagerImpl>(app.getSystemService(WindowManager::class.java))
        assertEquals(1, wm.views.size)
        app.graph.settings.update { it.copy(overlayHidden = true) }
        idle()
        assertTrue(wm.views.isEmpty())
        app.graph.settings.update { it.copy(overlayHidden = false) }
        idle()
        assertEquals(1, wm.views.size)
        app.graph.controller.end()
        idle()
        assertFalse(wm.views.isNotEmpty())
    }
}
