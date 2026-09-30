package se.eldebosh.nastastopp.weather

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.BuildConfig
import se.eldebosh.nastastopp.core.weather.DisplayWeather
import se.eldebosh.nastastopp.core.weather.SmhiForecast
import java.net.HttpURLConnection
import java.net.URI

/**
 * The area's weather for the passenger display: SMHI's forecast for a fixed place (never the
 * vehicle's position), asked every half hour while a passenger display shows a route ([want]).
 */
class WeatherSource(private val scope: CoroutineScope) {
    private val _weather = MutableStateFlow<DisplayWeather?>(null)
    val weather: StateFlow<DisplayWeather?> = _weather.asStateFlow()
    private var job: Job? = null

    fun want(on: Boolean) {
        if (!on) {
            job?.cancel()
            job = null
            return
        }
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                fetch()?.let { _weather.value = it }
                delay(REFRESH_MS)
            }
        }
    }

    private fun fetch(): DisplayWeather? = runCatching {
        val conn = URI(SmhiForecast.URL).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "NastaStopp/${BuildConfig.VERSION_NAME} (Android; weather on a passenger display)")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            SmhiForecast.parse(conn.inputStream.bufferedReader().use { it.readText() }, System.currentTimeMillis())
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val REFRESH_MS = 30 * 60_000L
    }
}
