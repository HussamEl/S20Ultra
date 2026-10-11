package se.eldebosh.nastastopp.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import se.eldebosh.nastastopp.App

/** Handles the notification actions Nästa / Upprepa / Avsluta and "show the floating button" (not exported). */
class RouteActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = App.from(context).graph.controller
        when (intent.action) {
            ACTION_NEXT -> controller.next()
            ACTION_REPEAT -> controller.repeat()
            ACTION_END -> controller.end()
            ACTION_SHOW_OVERLAY -> App.from(context).graph.settings.update { it.copy(overlayHidden = false, overlayMinimized = false) }
        }
    }

    companion object {
        const val ACTION_NEXT = "se.eldebosh.nastastopp.action.NEXT"
        const val ACTION_REPEAT = "se.eldebosh.nastastopp.action.REPEAT"
        const val ACTION_END = "se.eldebosh.nastastopp.action.END"
        const val ACTION_SHOW_OVERLAY = "se.eldebosh.nastastopp.action.SHOW_OVERLAY"

        fun pendingIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, RouteActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
