package se.eldebosh.nastastopp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import se.eldebosh.nastastopp.MainActivity
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.youdrive.TripChange
import se.eldebosh.nastastopp.youdrive.YouDriveActivity

/** Notification channels and the persistent route notification. */
object Notifications {
    const val CHANNEL_ROUTE = "route"
    const val CHANNEL_ALERTS = "alerts"
    const val CHANNEL_TRIPS = "trip_changes"
    const val CHANNEL_WATCH = "youdrive_watch"
    const val ID_ROUTE = 1001
    const val ID_OPEN_MAPS = 1002
    const val ID_OVERLAY_HIDDEN = 1003
    const val ID_YOUDRIVE = 1004
    const val ID_TRIP_SUMMARY = 1005
    private const val ID_TRIP_BASE = 2000
    private const val MAX_TRIP_ALERTS = 5
    const val EXTRA_OPEN = "se.eldebosh.nastastopp.OPEN"
    const val OPEN_YOUDRIVE = "youdrive"
    const val OPEN_REVIEW = "review"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ROUTE, context.getString(R.string.channel_route), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH),
        )
        // Added / cancelled trips: heads-up with the default sound and a clear vibration.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TRIPS, context.getString(R.string.channel_trips), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCH, context.getString(R.string.channel_watch), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
    }

    private fun openYouDrive(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 3,
        Intent(context, YouDriveActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Ongoing notification while the YouDrive page is watched (required for the foreground service). */
    fun buildYouDriveWatch(context: Context, text: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_stat_route)
            .setContentTitle(context.getString(R.string.youdrive_watching_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setContentIntent(openYouDrive(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /**
     * Alerts for trips added to or cancelled in YouDrive: one notification per change (up to
     * [MAX_TRIP_ALERTS]), then one summary. On the lock screen only "trip added / cancelled".
     */
    fun postTripChanges(context: Context, changes: List<Pair<Long, TripChange>>) {
        changes.take(MAX_TRIP_ALERTS).forEach { (id, change) ->
            val time = change.trip.time ?: "--:--"
            val title = context.getString(if (change.added) R.string.trip_added_title else R.string.trip_cancelled_title, time)
            val publicVersion = NotificationCompat.Builder(context, CHANNEL_TRIPS)
                .setSmallIcon(R.drawable.ic_stat_route)
                .setContentTitle(context.getString(if (change.added) R.string.trip_added_short else R.string.trip_cancelled_short))
                .build()
            val n = NotificationCompat.Builder(context, CHANNEL_TRIPS)
                .setSmallIcon(R.drawable.ic_stat_route)
                .setContentTitle(title)
                .setContentText(change.trip.address)
                .setStyle(NotificationCompat.BigTextStyle().bigText(change.trip.address))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                .setAutoCancel(true)
                .setContentIntent(openYouDrive(context))
                .build()
            post(context, ID_TRIP_BASE + (id % 500).toInt(), n)
        }
        if (changes.size > MAX_TRIP_ALERTS) {
            post(
                context, ID_TRIP_SUMMARY,
                NotificationCompat.Builder(context, CHANNEL_TRIPS)
                    .setSmallIcon(R.drawable.ic_stat_route)
                    .setContentTitle(context.getString(R.string.trip_changes_many, changes.size))
                    .setAutoCancel(true)
                    .setContentIntent(openYouDrive(context))
                    .build(),
            )
        }
    }

    /**
     * Persistent notification: next stop's spoken name + address, actions Nästa / Upprepa / Avsluta.
     * On the lock screen only the area name is shown (public version).
     */
    fun buildRoute(context: Context, spokenName: String, displayText: String, remaining: Int): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = context.getString(R.string.notif_next_stop, spokenName)
        val subText = context.getString(R.string.notif_remaining, remaining)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ROUTE)
            .setSmallIcon(R.drawable.ic_stat_route)
            .setContentTitle(title)
            .setSubText(subText)
            .build()
        return NotificationCompat.Builder(context, CHANNEL_ROUTE)
            .setSmallIcon(R.drawable.ic_stat_route)
            .setContentTitle(title)
            .setContentText(displayText)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.action_next_sv), RouteActionReceiver.pendingIntent(context, RouteActionReceiver.ACTION_NEXT))
            .addAction(0, context.getString(R.string.action_repeat_sv), RouteActionReceiver.pendingIntent(context, RouteActionReceiver.ACTION_REPEAT))
            .addAction(0, context.getString(R.string.action_end_sv), RouteActionReceiver.pendingIntent(context, RouteActionReceiver.ACTION_END))
            .build()
    }

    /**
     * Shown while the driver has closed the floating button during a route: one tap brings it
     * back. Silent; removed as soon as the button is shown again or the route ends.
     */
    fun buildOverlayHidden(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_ROUTE)
            .setSmallIcon(R.drawable.ic_stat_route)
            .setContentTitle(context.getString(R.string.notif_overlay_hidden_title))
            .setContentText(context.getString(R.string.notif_overlay_hidden_text))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(RouteActionReceiver.pendingIntent(context, RouteActionReceiver.ACTION_SHOW_OVERLAY))
            .addAction(0, context.getString(R.string.overlay_show), RouteActionReceiver.pendingIntent(context, RouteActionReceiver.ACTION_SHOW_OVERLAY))
            .build()

    /** Minimal notification used only if the route state is not available yet. */
    fun buildPlaceholder(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_ROUTE)
            .setSmallIcon(R.drawable.ic_stat_route)
            .setContentTitle(context.getString(R.string.app_name))
            .setOngoing(true)
            .build()

    fun post(context: Context, id: Int, notification: android.app.Notification) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        try {
            nm.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }
}
