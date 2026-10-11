package se.eldebosh.nastastopp.ui.screens

import android.appwidget.AppWidgetHostView
import android.view.View
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import se.eldebosh.nastastopp.weather.WeatherWidgets

/** A weather app's widget ([id]) at this place's size, drawn and kept up to date by its app. */
@Composable
fun HostedWidget(widgets: WeatherWidgets, id: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val w = maxWidth.value
        val h = maxHeight.value
        AndroidView(
            factory = { context -> widgets.view(context, id, w, h) ?: View(context) },
            update = { view -> (view as? AppWidgetHostView)?.let { widgets.resize(it, w, h) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
