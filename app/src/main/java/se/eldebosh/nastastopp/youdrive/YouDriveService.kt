package se.eldebosh.nastastopp.youdrive

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.service.Notifications
import se.eldebosh.nastastopp.util.DebugLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Foreground service (type "specialUse") that keeps the app alive while the driver's YouDrive
 * page is watched, so added / cancelled trips are noticed while Google Maps is in front.
 * Started from the app (foreground) when watching is switched on; stops when it is switched off.
 */
class YouDriveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observers: List<Job> = emptyList()
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val graph = App.from(this).graph
        try {
            ServiceCompat.startForeground(
                this, Notifications.ID_YOUDRIVE, Notifications.buildYouDriveWatch(this, statusText()),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (e: Exception) {
            DebugLog.w(e) { "youdrive startForeground failed" }
            stopSelf()
            return START_NOT_STICKY
        }
        if (!graph.settings.current.youDriveWatch) {
            stopEverything()
            return START_NOT_STICKY
        }
        graph.youDrive.sync()
        if (observers.isEmpty()) {
            observers = listOf(
                scope.launch {
                    graph.settings.state.map { it.youDriveWatch }.distinctUntilChanged().collect { on -> if (!on) stopEverything() }
                },
                // Only the status text changes (silent, no pop-up; the same line as in YouDrive's window).
                scope.launch {
                    graph.youDrive.state.map { statusText() }.distinctUntilChanged().collect { text ->
                        Notifications.post(this@YouDriveService, Notifications.ID_YOUDRIVE, Notifications.buildYouDriveWatch(this@YouDriveService, text))
                    }
                },
            )
        }
        return START_NOT_STICKY
    }

    private fun statusText(): String {
        val s = App.from(this).graph.youDrive.state.value
        val checked = s.lastReadMs?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(timeFormat) } ?: "--:--"
        return when (s.status) {
            YouDriveWatcher.Status.LOGGED_OUT -> getString(R.string.youdrive_status_logged_out)
            YouDriveWatcher.Status.WATCHING -> getString(R.string.youdrive_status_watching, s.trips.size, checked)
            YouDriveWatcher.Status.NO_TRIPS -> getString(R.string.youdrive_status_no_trips, checked)
            else -> getString(R.string.youdrive_status_loading)
        }
    }

    /** Stops updating first, so the notification cannot be posted again after it was removed. */
    private fun stopEverything() {
        observers.forEach { it.cancel() }
        observers = emptyList()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Notifications.cancel(this, Notifications.ID_YOUDRIVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        Notifications.cancel(this, Notifications.ID_YOUDRIVE)
        super.onDestroy()
    }

    companion object {
        /** Must be called while the app is in the foreground. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, YouDriveService::class.java))
            } catch (e: Exception) {
                DebugLog.w(e) { "youdrive service start failed" }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, YouDriveService::class.java))
        }
    }
}
