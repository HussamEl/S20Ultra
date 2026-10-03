package se.eldebosh.nastastopp.maps

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.route.MapsUrlBuilder
import se.eldebosh.nastastopp.service.Notifications
import se.eldebosh.nastastopp.util.DebugLog
import se.eldebosh.nastastopp.util.findActivity

/** Opens Google Maps: turn-by-turn navigation for up to 10 stops, or one URL ([open]); and Google Earth ([openEarth]). */
class MapsLauncher(private val context: Context) {

    fun intentFor(stops: List<String>, withPackage: Boolean = true): Intent {
        val url = MapsUrlBuilder.buildUrl(stops)
        return Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            if (withPackage) setPackage(MapsUrlBuilder.MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Launches Maps. From the background (automatic batch change) Android may block activity
     * starts unless our overlay is visible, so in that case a tap-to-open notification is posted too.
     */
    fun launch(stops: List<String>, fromBackground: Boolean = false) {
        if (stops.isEmpty()) return
        if (fromBackground && !isAppInForeground()) postOpenMapsNotification(stops) else cancelOpenMapsNotification()
        startSafely(stops)
    }

    /**
     * Opens one Google Maps URL (navigation to a stop, its street photos): Google's app, else the
     * browser. Opened from a screen ([from]), Back in Google's app comes back to that screen.
     */
    fun open(url: String, from: Context? = null) {
        val (starter, intent) = view(url, from)
        try {
            starter.startActivity(Intent(intent).setPackage(MapsUrlBuilder.MAPS_PACKAGE))
        } catch (_: ActivityNotFoundException) {
            try {
                starter.startActivity(intent)
            } catch (e: Exception) {
                DebugLog.w(e) { "no app can open maps url" }
            }
        } catch (e: Exception) {
            DebugLog.w(e) { "maps start failed" }
        }
    }

    /**
     * Google Earth's 3D view of a point, flown to as it opens; without Google Earth, Google Maps'
     * satellite view of it. Both are Google's own apps: nothing is billed on the driver's key.
     */
    fun openEarth(lat: Double, lng: Double, from: Context? = null) {
        val (starter, earth) = view(MapsUrlBuilder.earthUrl(lat, lng), from)
        try {
            starter.startActivity(earth.setPackage(MapsUrlBuilder.EARTH_PACKAGE))
        } catch (_: ActivityNotFoundException) {
            open(MapsUrlBuilder.satelliteUrl(lat, lng), from)
        } catch (e: Exception) {
            DebugLog.w(e) { "earth start failed" }
        }
    }

    /**
     * A view of [url] and what starts it: [from]'s activity when there is one, so Google's app
     * opens over that screen and Back returns to it; else a task of its own, which Back leaves for
     * the home screen.
     */
    private fun view(url: String, from: Context?): Pair<Context, Intent> {
        val activity = from?.findActivity()
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return (activity ?: context) to intent
    }

    private fun startSafely(stops: List<String>) {
        try {
            context.startActivity(intentFor(stops, withPackage = true))
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(intentFor(stops, withPackage = false))
            } catch (e: Exception) {
                DebugLog.w(e) { "no app can open maps url" }
            }
        } catch (e: Exception) {
            DebugLog.w(e) { "maps start failed" }
        }
    }

    private fun postOpenMapsNotification(stops: List<String>) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val intent = if (isMapsInstalled()) intentFor(stops, true) else intentFor(stops, false)
        val pi = PendingIntent.getActivity(
            context, REQ_OPEN_MAPS, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_route)
            .setContentTitle(context.getString(R.string.notif_open_maps_title))
            .setContentText(context.getString(R.string.notif_open_maps_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(5 * 60_000L)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(Notifications.ID_OPEN_MAPS, n)
        } catch (_: SecurityException) {
        }
    }

    fun cancelOpenMapsNotification() {
        context.getSystemService(NotificationManager::class.java)?.cancel(Notifications.ID_OPEN_MAPS)
    }

    fun isMapsInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(MapsUrlBuilder.MAPS_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    companion object {
        private const val REQ_OPEN_MAPS = 30
    }
}
