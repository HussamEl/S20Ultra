package se.eldebosh.nastastopp.geo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.eldebosh.nastastopp.core.geo.Fix
import se.eldebosh.nastastopp.util.DebugLog
import se.eldebosh.nastastopp.util.SystemIntents

/**
 * Where this tablet is, for its Google map of the way to the next stop (208): the tablet's own GPS
 * through the system's LocationManager (no Play services), at high accuracy every second, only
 * while the passenger display is in sight ([start] / [stop]), never in the background. Positions
 * less exact than [MAX_ACCURACY_M] are left out. Nothing is stored or logged: the positions go to
 * the map only, and from there to Google with the driver's key.
 */
class TabletPosition(private val context: Context) {
    private val manager by lazy { context.getSystemService(LocationManager::class.java) }
    private val _fix = MutableStateFlow<Fix?>(null)

    /** The last exact position while running, or null. */
    val fix: StateFlow<Fix?> = _fix.asStateFlow()
    private var running = false

    private val listener = LocationListenerCompat { location -> if (location.exact) _fix.value = location.toFix() }

    /** Location is allowed on this tablet (while in use). */
    val allowed: Boolean get() = SystemIntents.hasLocation(context)

    fun start() {
        if (running || !allowed) return
        val m = manager ?: return
        val provider = provider(m) ?: return
        val request = LocationRequestCompat.Builder(INTERVAL_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MS)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()
        try {
            LocationManagerCompat.requestLocationUpdates(m, provider, request, listener, Looper.getMainLooper())
            running = true
            // A fresh last position gets the map going at once.
            m.getLastKnownLocation(provider)
                ?.takeIf { it.exact && SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos <= FRESH_NS }
                ?.let { _fix.value = it.toFix() }
        } catch (e: SecurityException) {
            DebugLog.w(e) { "no location permission" }
        } catch (e: IllegalArgumentException) {
            DebugLog.w(e) { "no location provider" }
        }
    }

    fun stop() {
        if (!running) return
        try {
            manager?.let { LocationManagerCompat.removeUpdates(it, listener) }
        } catch (e: SecurityException) {
            DebugLog.w(e) { "location updates not removed" }
        }
        running = false
        _fix.value = null
    }

    /** GPS itself when precise location is allowed; otherwise the fused provider, or the network. */
    private fun provider(manager: LocationManager): String? {
        val all = manager.allProviders
        val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return when {
            precise && LocationManager.GPS_PROVIDER in all -> LocationManager.GPS_PROVIDER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in all -> LocationManager.FUSED_PROVIDER
            LocationManager.NETWORK_PROVIDER in all -> LocationManager.NETWORK_PROVIDER
            else -> all.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
        }
    }

    private val Location.exact: Boolean get() = !hasAccuracy() || accuracy <= MAX_ACCURACY_M

    private fun Location.toFix() = Fix(
        timeMs = time,
        lat = latitude,
        lng = longitude,
        speedMps = if (hasSpeed()) speed else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
        bearingDeg = if (hasBearing()) bearing else null,
    )

    companion object {
        /** A new position every second, for the map to follow the car closely. */
        const val INTERVAL_MS = 1_000L

        /** Positions less exact than this are not used. */
        const val MAX_ACCURACY_M = 30f

        /** A last position older than this does not start the map. */
        private const val FRESH_NS = 60_000_000_000L
    }
}
