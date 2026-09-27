package se.eldebosh.nastastopp.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.route.RouteController
import se.eldebosh.nastastopp.settings.SettingsStore

/**
 * Keeps the persistent route notification in sync with the route. It uses the same id as the
 * foreground service notification, so posting here also updates the service's notification.
 * Without location permission (no service) it is posted as a normal ongoing notification.
 */
class RouteNotifier(
    private val context: Context,
    private val controller: RouteController,
    private val settings: SettingsStore,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            combine(controller.route, settings.state) { r, _ -> r }.collect { refresh() }
        }
    }

    fun currentNotification(): android.app.Notification? {
        val r = controller.route.value ?: return null
        if (!r.active) return null
        val current = r.stops.firstOrNull() ?: return null
        return Notifications.buildRoute(context, controller.spokenName(current), current.displayText, r.stops.size)
    }

    fun refresh() {
        val n = currentNotification()
        if (n == null) Notifications.cancel(context, Notifications.ID_ROUTE)
        else Notifications.post(context, Notifications.ID_ROUTE, n)
    }
}
