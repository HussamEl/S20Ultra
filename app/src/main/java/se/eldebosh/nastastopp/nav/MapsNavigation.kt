package se.eldebosh.nastastopp.nav

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.eldebosh.nastastopp.core.nav.DisplayEta
import se.eldebosh.nastastopp.core.nav.MapsEta
import java.time.LocalTime

/** Google Maps' remaining travel time while it navigates, for the passenger display. */
object MapsNavigation {
    private val _eta = MutableStateFlow<DisplayEta?>(null)
    val eta: StateFlow<DisplayEta?> = _eta.asStateFlow()

    internal fun set(eta: DisplayEta?) {
        _eta.value = eta
    }
}

/**
 * Reads the remaining travel time from Google Maps' own navigation notification, with the access
 * the driver gives in Settings (203). Only Google Maps' notifications are looked at, and only
 * their numbers are taken: nothing is kept, sent on (but the minutes and distance) or logged.
 */
class MapsNavigationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        runCatching { activeNotifications }.getOrNull()?.forEach { read(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = read(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS && sbn.isOngoing) MapsNavigation.set(null)
    }

    override fun onListenerDisconnected() = MapsNavigation.set(null)

    private fun read(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS || !sbn.isOngoing) return
        val now = LocalTime.now()
        val eta = MapsEta.parse(texts(sbn.notification), now.hour * 60 + now.minute) ?: return
        MapsNavigation.set(eta)
    }

    /** The notification's text lines: its extras and the text in its own layouts. */
    private fun texts(n: Notification): List<String> {
        val out = ArrayList<String>()
        val extras = n.extras
        listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT)
            .forEach { key -> extras.getCharSequence(key)?.toString()?.let(out::add) }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { out += it.toString() }
        @Suppress("DEPRECATION")
        listOfNotNull(n.contentView, n.bigContentView).forEach { out += textsIn(it) }
        return out
    }

    private fun textsIn(views: RemoteViews): List<String> = runCatching {
        val parent = FrameLayout(this)
        val root = views.apply(this, parent)
        val out = ArrayList<String>()
        fun walk(v: View) {
            if (v is TextView) v.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        out
    }.getOrDefault(emptyList())

    private companion object {
        const val MAPS = "com.google.android.apps.maps"
    }
}
