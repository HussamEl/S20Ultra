package se.eldebosh.nastastopp.weather

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF

/**
 * A weather app's own widget on the tablet's passenger display (chosen on the tablet, 207). The
 * weather app draws it and keeps it up to date; this app only hosts it: it never reads what the
 * widget shows and sends nothing anywhere.
 */
class WeatherWidgets(private val context: Context) {
    private val manager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, HOST_ID)

    /** A widget the driver can choose, with its name and its app's name. */
    data class Choice(val info: AppWidgetProviderInfo, val label: String, val app: String)

    /** The installed widgets, weather apps' first. */
    fun choices(): List<Choice> {
        val pm = context.packageManager
        return manager.installedProviders.map { info ->
            val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrDefault(info.provider.packageName)
            Choice(info, info.loadLabel(pm), app)
        }.sortedWith(compareBy({ !isWeather(it) }, { it.app.lowercase() }, { it.label.lowercase() }))
    }

    /** A new widget slot for [info]: bound at once when the system allows it, else it asks the driver ([bindIntent]). */
    fun add(info: AppWidgetProviderInfo): Pair<Int, Boolean> {
        val id = host.allocateAppWidgetId()
        return id to manager.bindAppWidgetIdIfAllowed(id, info.provider)
    }

    /** The system's question "Allow Nästa Stopp to create widgets?" for the slot [id]. */
    fun bindIntent(id: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)

    /** The widget still asks to be set up (its city, its look) before it shows anything. */
    fun needsSetup(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL == 0
    }

    /** Opens the weather app's own setup for the slot [id] (its result comes back to [activity]). */
    fun setUp(activity: android.app.Activity, id: Int) {
        runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, SETUP_REQUEST, null) }
    }

    fun remove(id: Int) {
        if (id >= 0) runCatching { host.deleteAppWidgetId(id) }
    }

    /** The widget's name, or null when its slot is gone (the app was removed). */
    fun label(id: Int): String? = manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager)

    /** The widget's view, drawn by its app, at [widthDp] × [heightDp]; null when its slot is gone. */
    fun view(viewContext: Context, id: Int, widthDp: Float, heightDp: Float): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return host.createView(viewContext, id, info).apply { resize(this, widthDp, heightDp) }
    }

    fun resize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
            } else {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
            }
        }
    }

    /** Widgets update only while the host listens: from the display showing until it goes. */
    fun listen(on: Boolean) {
        runCatching { if (on) host.startListening() else host.stopListening() }
    }

    private fun isWeather(c: Choice): Boolean {
        val text = "${c.label} ${c.app} ${c.info.provider.packageName}".lowercase()
        return WEATHER_WORDS.any { it in text }
    }

    private companion object {
        const val HOST_ID = 4721
        const val SETUP_REQUEST = 4722
        val WEATHER_WORDS = listOf("weather", "väder", "wetter", "météo", "meteo", "clima", "tiempo", "طقس", "daemonapp")
    }
}
