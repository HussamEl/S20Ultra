package se.eldebosh.nastastopp.route

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import se.eldebosh.nastastopp.App

/** Fired by AlarmManager 12 h after a route was created: deletes the route data. */
class ExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        App.from(context).graph.controller.clearIfExpired()
    }
}
