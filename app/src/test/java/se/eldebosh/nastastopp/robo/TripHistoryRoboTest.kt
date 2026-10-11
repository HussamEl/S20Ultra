package se.eldebosh.nastastopp.robo

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.geo.Geocoding
import se.eldebosh.nastastopp.route.TripHistory
import se.eldebosh.nastastopp.route.model.GeoStatus
import java.io.File
import java.time.Duration

/** Finished trips stay on the Home screen after "Avsluta" and expire after the chosen time. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class TripHistoryRoboTest {

    private lateinit var app: App

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Geocoding.register(app) // read before the stops are looked for, so they wait only in the test's own time
        app.graph.controller.clear()
        app.graph.history.clear()
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun settle() {
        repeat(2_000) {
            if (app.graph.controller.route.value?.stops?.none { it.geoStatus == GeoStatus.PENDING } != false) return
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        }
    }

    @Test
    fun tripsAreKeptAfterAvsluta() {
        val c = app.graph.controller
        c.addManual("Storgatan 14, 65224 Karlstad", "12:30")
        c.addManual("Järnvägsgatan 3B, 68830 Storfors", "12:45")
        c.addManual("Björkvägen 7, 66341 Hammarö", "13:05")
        settle()
        c.start()
        c.next() // Karlstad done
        c.next() // Storfors done
        c.end() // "Avsluta" with Hammarö still open
        idle()
        assertNull(c.route.value)
        val h = app.graph.history.entries.value
        assertEquals(listOf("Karlstad", "Storfors", "Hammarö"), h.map { it.area })
        assertEquals(listOf("12:30", "12:45", "13:05"), h.map { it.time })
        assertEquals(listOf(true, true, false), h.map { it.done })
        assertEquals("Storgatan 14, 652 24 Karlstad", h.first().displayText)
    }

    @Test
    fun finishingTheLastStopRecordsItToo() {
        val c = app.graph.controller
        c.addManual("Storgatan 14, 65224 Karlstad", "12:30")
        settle()
        c.start()
        c.next() // last stop → "Rutten är klar."
        idle()
        assertNull(c.route.value)
        assertEquals(listOf(true), app.graph.history.entries.value.map { it.done })
    }

    @Test
    fun historyExpiresAfterRetentionAndCanBeCleared() {
        val history = app.graph.history
        val now = System.currentTimeMillis()
        history.add("08:00", "Kil", "Kungsgatan 5, Kil", done = true, now = now - 13 * 3_600_000L)
        history.add("12:00", "Grums", "Lindvägen 9, 664 30 Grums", done = true, now = now - 1 * 3_600_000L)
        history.prune(now)
        assertEquals(listOf("Grums"), history.entries.value.map { it.area })

        app.graph.settings.update { it.copy(historyRetentionHours = 168) }
        history.add("07:00", "Mora", "Storgatan 1, Mora", done = true, now = now - 48 * 3_600_000L)
        history.prune(now)
        assertEquals(2, history.entries.value.size)
        app.graph.settings.update { it.copy(historyRetentionHours = 12) }
        idle()
        assertEquals(listOf("Grums"), history.entries.value.map { it.area })

        history.clear()
        assertTrue(history.entries.value.isEmpty())
    }

    @Test
    fun historySurvivesAppRestart() {
        app.graph.history.add("12:00", "Grums", "Lindvägen 9, 664 30 Grums", done = true)
        val file = File(app.noBackupFilesDir, TripHistory.FILE_NAME)
        repeat(100) { if (!file.exists()) Thread.sleep(20) }
        assertTrue(file.exists())
        // A new instance (as after process death) reads it back.
        val reloaded = TripHistory(app, app.graph.settings, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate))
        assertEquals(listOf("Grums"), reloaded.entries.value.map { it.area })
        app.graph.history.clear()
        repeat(100) { if (file.exists()) Thread.sleep(20) }
        assertFalse(file.exists())
    }
}
