package se.eldebosh.nastastopp.robo

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.AppGraph
import se.eldebosh.nastastopp.route.model.GeoPoint
import se.eldebosh.nastastopp.route.model.GeoStatus
import se.eldebosh.nastastopp.service.StreetService
import java.time.Duration

/** Location only names the street the vehicle is on: asked for, never in the background, never moving the route on. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class StreetServiceRoboTest {

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

    private fun startLocatedRoute() {
        graph.controller.addManual("Storgatan 14, 65224 Karlstad")
        graph.controller.addManual("Kungsgatan 5, 65225 Karlstad")
        var waited = 0
        while (graph.controller.route.value!!.stops.any { it.geoStatus == GeoStatus.PENDING } && waited++ < 2_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)) // Robolectric's geocoder never answers
        }
        val located = graph.controller.route.value!!.stops.mapIndexed { i, s ->
            s.copy(geoStatus = GeoStatus.LOCATED, geo = GeoPoint(59.38 + i * 0.01, 13.50, "addr $i", locality = "Karlstad"))
        }
        graph.controller.restoreStops(located)
        graph.controller.start()
        idle()
    }

    @Test
    fun theManifestAsksForLocationButNeverInTheBackground() {
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        val asked = info.requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in asked)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE_LOCATION in asked)
        assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in asked)
    }

    @Test
    fun withoutPermissionNothingStarts() {
        startLocatedRoute()
        assertNull(shadowOf(app).nextStartedService)
        graph.controller.end()
    }

    @Test
    fun positionsNameTheStreetButNeverMoveTheRouteOn() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        startLocatedRoute()
        val started = shadowOf(app).nextStartedService
        assertEquals(StreetService::class.java.name, started.component?.className)

        val service = Robolectric.buildService(StreetService::class.java, started).create().startCommand(0, 1)
        val locations = app.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER).first { it in locations.allProviders }
        assertEquals("the street service listens", 1, shadowOf(locations).getLocationRequests(provider).size)
        val first = graph.controller.route.value!!.stops.first().id

        // Drive to the first stop, wait there and drive off: the route stays where the driver left it.
        fun at(t: Long, northM: Double, speed: Float) = Location(provider).apply {
            time = t * 1000
            latitude = 59.38 + northM / 111_195.0
            longitude = 13.50
            this.speed = speed
            accuracy = 5f
        }
        for ((t, m, v) in listOf(Triple(0L, 400.0, 12f), Triple(2L, 10.0, 0f), Triple(20L, 10.0, 0f), Triple(40L, 150.0, 8f))) {
            shadowOf(locations).simulateLocation(at(t, m, v))
            idle()
        }
        assertEquals(first, graph.controller.route.value!!.stops.first().id)
        assertEquals(0, graph.controller.route.value!!.completedCount)

        // The route ends: the service stops listening.
        graph.controller.end()
        idle()
        assertTrue(shadowOf(service.get()).isStoppedBySelf)
        assertTrue(shadowOf(locations).getLocationRequests(provider).isEmpty())
    }
}
