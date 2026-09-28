package se.eldebosh.nastastopp.route

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import se.eldebosh.nastastopp.App

/** Fired by AlarmManager when route data or history trips expire: deletes them. */
class ExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = App.from(context).graph
        graph.controller.clearIfExpired()
        graph.history.prune()
    }
}
