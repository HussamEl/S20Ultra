package se.eldebosh.nastastopp.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.core.geo.Fix
import se.eldebosh.nastastopp.util.DebugLog
import se.eldebosh.nastastopp.util.SystemIntents

/**
 * Foreground service (type "location") that runs while a route is active, only to name the street
 * the vehicle is on (the floating button's circle and the route screen). Positions go to
 * [se.eldebosh.nastastopp.geo.CurrentStreet] and nowhere else: they never move the route on,
 * are never stored, logged or sent, and the YouDrive page never gets them.
 *
 * It is started while the app is in the foreground, so "while using the app" permission is
 * enough (no background location). It uses the system's LocationManager (no Play services).
 */
class StreetService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val locations by lazy { getSystemService(LocationManager::class.java) }
    private var tracking = false
    private var observing = false

    private val listener = LocationListenerCompat { location ->
        App.from(this).graph.street.onFix(location.toFix())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val graph = App.from(this).graph
        // Always satisfy startForegroundService() first, then decide whether to keep running.
        // The route notification is the service's notification (same id).
        val notification = graph.notifier.currentNotification() ?: Notifications.buildPlaceholder(this)
        try {
            ServiceCompat.startForeground(this, Notifications.ID_ROUTE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (e: Exception) {
            // No permission any more, or started from the background.
            DebugLog.w(e) { "street startForeground failed" }
            stopSelf()
            graph.notifier.refresh()
            return START_NOT_STICKY
        }
        if (graph.controller.route.value?.active != true || !SystemIntents.hasLocation(this)) {
            stopEverything()
            return START_NOT_STICKY
        }
        startTracking()
        if (!observing) {
            observing = true
            scope.launch { graph.controller.route.collect { r -> if (r?.active != true) stopEverything() } }
        }
        return START_NOT_STICKY
    }

    private fun startTracking() {
        if (tracking) return
        val manager = locations ?: return
        val provider = provider(manager) ?: return
        val request = LocationRequestCompat.Builder(INTERVAL_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
            // High accuracy: "balanced" positions come from Wi-Fi and masts, often 30–60 m off and
            // without a speed, which would name the wrong street and leave the speed empty.
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()
        try {
            LocationManagerCompat.requestLocationUpdates(manager, provider, request, listener, Looper.getMainLooper())
            tracking = true
        } catch (e: SecurityException) {
            DebugLog.w(e) { "no location permission" }
        } catch (e: IllegalArgumentException) {
            DebugLog.w(e) { "no location provider" }
        }
    }

    /**
     * GPS itself when precise location is allowed: exact positions with the speed and heading
     * the street matching needs. Otherwise the fused provider, or the network.
     */
    private fun provider(manager: LocationManager): String? {
        val all = manager.allProviders
        val precise = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return when {
            precise && LocationManager.GPS_PROVIDER in all -> LocationManager.GPS_PROVIDER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in all -> LocationManager.FUSED_PROVIDER
            LocationManager.NETWORK_PROVIDER in all -> LocationManager.NETWORK_PROVIDER
            else -> all.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
        }
    }

    private fun stopEverything() {
        stopTracking()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        App.from(this).graph.notifier.refresh()
    }

    private fun stopTracking() {
        if (!tracking) return
        try {
            locations?.let { LocationManagerCompat.removeUpdates(it, listener) }
        } catch (e: SecurityException) {
            DebugLog.w(e) { "location updates not removed" }
        }
        tracking = false
    }

    override fun onDestroy() {
        stopTracking()
        scope.cancel()
        super.onDestroy()
    }

    private fun Location.toFix() = Fix(
        timeMs = time,
        lat = latitude,
        lng = longitude,
        speedMps = if (hasSpeed()) speed else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
        bearingDeg = if (hasBearing()) bearing else null,
    )

    companion object {
        /**
         * A new position every 2 s, also when standing still, so the panel's speed stays current.
         * Street lookups are throttled separately by StreetLookup.
         */
        private const val INTERVAL_MS = 2_000L
        private const val MIN_DISTANCE_M = 0f

        /**
         * Starts the service while a route is active and the driver allowed location. Must be
         * called while the app is in the foreground. Returns false if it was not started.
         */
        fun start(context: Context): Boolean {
            if (!SystemIntents.hasLocation(context)) return false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, StreetService::class.java))
                true
            } catch (e: Exception) {
                DebugLog.w(e) { "street service start failed" }
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StreetService::class.java))
        }
    }
}
