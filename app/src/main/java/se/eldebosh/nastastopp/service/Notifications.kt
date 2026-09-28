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

/** Notification channels and the persistent route notification. */
object Notifications {
    const val CHANNEL_ROUTE = "route"
    const val CHANNEL_ALERTS = "alerts"
    const val ID_ROUTE = 1001
    const val ID_OPEN_MAPS = 1002
    const val ID_OVERLAY_HIDDEN = 1003

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
