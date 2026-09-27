package se.eldebosh.nastastopp.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.core.route.Fix
import se.eldebosh.nastastopp.util.DebugLog

/**
 * Foreground service (type "location") that runs while a route is active. It is started while
 * the app is in the foreground, so no background-location permission is needed. It feeds
 * high-accuracy fixes (2 s) to the RouteController, which detects arrival / departure.
 */
class RouteService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var tracking = false
    private var observing = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val controller = App.from(this@RouteService).graph.controller
            for (loc in result.locations) controller.onLocation(loc.toFix())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val graph = App.from(this).graph
        // Always satisfy startForegroundService() first, then decide whether to keep running.
        val notification = graph.notifier.currentNotification() ?: Notifications.buildPlaceholder(this)
        val hasLocation = hasLocationPermission(this)
        try {
            ServiceCompat.startForeground(
                this, Notifications.ID_ROUTE, notification,
                if (hasLocation) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException after a restart from the background.
            DebugLog.w(e) { "startForeground failed" }
            stopSelf()
            graph.notifier.refresh()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP || graph.controller.route.value?.active != true) {
            stopEverything()
            return START_NOT_STICKY
        }
        if (hasLocation) startTracking()
        if (!observing) {
            observing = true
            scope.launch {
                graph.controller.route.collectLatest { r -> if (r?.active != true) stopEverything() }
            }
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (tracking) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            tracking = true
        } catch (e: SecurityException) {
            DebugLog.w(e) { "no location permission" }
        }
    }

    private fun stopEverything() {
        if (tracking) {
            fused.removeLocationUpdates(callback)
            tracking = false
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (tracking) fused.removeLocationUpdates(callback)
        tracking = false
        scope.cancel()
        super.onDestroy()
    }

    private fun Location.toFix() = Fix(
        timeMs = time,
        lat = latitude,
        lng = longitude,
        speedMps = if (hasSpeed()) speed else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
    )

    companion object {
        private const val INTERVAL_MS = 2_000L
        private const val ACTION_STOP = "se.eldebosh.nastastopp.service.STOP"

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Must be called while the app is in the foreground. Returns false if not started. */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, RouteService::class.java))
            true
        } catch (e: Exception) {
            DebugLog.w(e) { "service start failed" }
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RouteService::class.java))
        }
    }
}
