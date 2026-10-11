package se.eldebosh.nastastopp.robo

import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.core.route.MapsUrlBuilder
import se.eldebosh.nastastopp.maps.MapsLauncher

/**
 * Google's apps opened from the passenger display come back to it: they are started from the
 * display's own activity (also behind a language wrapper), never in a task of their own, which
 * Back would leave for the home screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class MapsLauncherRoboTest {

    @Test
    fun googleEarthAndStreetPhotosOpenOverTheDisplay() {
        val app = ApplicationProvider.getApplicationContext<App>()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val launcher = MapsLauncher(app)

        launcher.openEarth(59.381234, 13.501234, ContextWrapper(activity))
        val earth = shadowOf(activity).nextStartedActivity
        assertEquals(MapsUrlBuilder.EARTH_PACKAGE, earth.`package`)
        assertEquals(0, earth.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertNull(shadowOf(app).nextStartedActivity)

        launcher.open(MapsUrlBuilder.streetViewUrl(59.381234, 13.501234), activity)
        val street = shadowOf(activity).nextStartedActivity
        assertEquals(MapsUrlBuilder.MAPS_PACKAGE, street.`package`)
        assertEquals(0, street.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Without a screen (the route's own starts), Google Maps gets a task of its own. */
    @Test
    fun withoutAScreenMapsHasItsOwnTask() {
        val app = ApplicationProvider.getApplicationContext<App>()
        MapsLauncher(app).open(MapsUrlBuilder.streetViewUrl(59.381234, 13.501234))
        val started = shadowOf(app).nextStartedActivity
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
