package se.eldebosh.nastastopp.robo

import android.content.Intent
import android.os.Looper
import android.speech.tts.TextToSpeech
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
import org.robolectric.shadows.ShadowTextToSpeech
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.AppGraph
import se.eldebosh.nastastopp.core.route.Fix
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
        graph = app.graph
        graph.controller.clear()
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Advances virtual time (Robolectric's Geocoder never answers, so each lookup times out). */
    private fun idleUntil(condition: () -> Boolean) {
        repeat(2_000) {
            if (condition()) return
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
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
        assertEquals("Nästa stopp: Karlstad. Därefter: Storfors.", tts.lastSpokenText)

        val maps = shadowOf(app).nextStartedActivity
        assertNotNull(maps)
        assertEquals(Intent.ACTION_VIEW, maps.action)
        assertEquals("com.google.android.apps.maps", maps.`package`)
        val url = maps.dataString!!
        assertTrue(url, url.startsWith("https://www.google.com/maps/dir/?api=1&destination="))
        assertTrue(url, url.contains("&waypoints="))
        assertTrue(url, url.endsWith("&travelmode=driving&dir_action=navigate"))

        graph.controller.next()
        assertEquals("Nästa stopp: Storfors. Därefter: Hammarö.", tts.lastSpokenText)
        graph.controller.repeat()
        assertEquals("Nästa stopp: Storfors. Därefter: Hammarö.", tts.lastSpokenText)
        graph.controller.next()
        assertEquals("Nästa stopp: Hammarö. Det är sista stoppet.", tts.lastSpokenText)
        graph.controller.next()
        assertEquals("Rutten är klar.", tts.lastSpokenText)
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
        assertEquals("Next stop: Karlstad. Then: Kil.", tts.lastSpokenText)
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
    fun departureAdvancesAutomaticallyAndSamePlaceIsManual() {
        val tts = readyTts()
        graph.controller.addManual("Storgatan 14, 65224 Karlstad")
        graph.controller.addManual("Kungsgatan 5, 65225 Karlstad")
        graph.controller.addManual("Kungsgatan 5, 65225 Karlstad")
        idleUntil { graph.controller.route.value!!.stops.none { it.geoStatus == GeoStatus.PENDING } }
        // Pretend the geocoder located the stops (Robolectric has no geocoding backend).
        val r = graph.controller.route.value!!
        val located = r.stops.mapIndexed { i, s ->
            s.copy(geoStatus = GeoStatus.LOCATED, geo = GeoPoint(59.38 + (if (i == 0) 0.0 else 0.01), 13.50, "addr $i", locality = "Karlstad", subLocality = if (i == 0) "Herrhagen" else "Kronoparken"))
        }
        graph.controller.restoreStops(located)
        graph.controller.start()
        idle()
        assertEquals("Nästa stopp: Herrhagen. Därefter: Kronoparken.", tts.lastSpokenText)
        assertTrue(graph.controller.tracking.value.autoEnabled)

        // Drive in from 400 m, stop at the address, drive away.
        val lat0 = 59.38
        fun fix(t: Long, m: Double, v: Float) = Fix(t * 1000, lat0 + m / 111_195.0, 13.50, v, 5f)
        graph.controller.onLocation(fix(0, 400.0, 12f))
        graph.controller.onLocation(fix(2, 10.0, 0f))
        assertEquals(se.eldebosh.nastastopp.core.route.DetectorPhase.ARRIVED, graph.controller.tracking.value.phase)
        graph.controller.onLocation(fix(40, 150.0, 8f))
        idle()
        assertEquals("Nästa stopp: Kronoparken. Därefter: Kronoparken.", tts.lastSpokenText)
        // Next two stops are at the same place → automatic detection off (manual only).
        assertFalse(graph.controller.tracking.value.autoEnabled)
        graph.controller.end()
        idle()
        assertNull(graph.controller.route.value)
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
