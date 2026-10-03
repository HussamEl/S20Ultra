package se.eldebosh.nastastopp.core.weather

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.math.roundToInt

/** The weather shown on the passenger display: the temperature and SMHI's weather symbol (1–27). */
@Serializable
data class DisplayWeather(val tempC: Int, val symbol: Int) {
    val kind: WeatherKind get() = SmhiForecast.kindOf(symbol)

    /** SMHI's words for the symbol ("Halvklart"), for the passengers. */
    val swedish: String get() = SmhiForecast.swedish(symbol)
}

/** What the weather looks like, for its picture. */
enum class WeatherKind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SLEET, SNOW, THUNDER }

/**
 * SMHI's open point forecast (snow1g): the hour nearest now. It is asked for a fixed place, the
 * middle of the driver's area, never the vehicle's position or a passenger's address.
 */
object SmhiForecast {
    /** Karlstad: the middle of Värmland's routes. */
    const val URL = "https://opendata-download-metfcst.smhi.se/api/category/snow1g/version/1/geotype/point/lon/13.5/lat/59.38/data.json"

    private val json = Json { ignoreUnknownKeys = true }

    /** The forecast hour nearest [nowMs], or null when the answer cannot be read. */
    fun parse(text: String, nowMs: Long): DisplayWeather? = runCatching {
        val series = json.parseToJsonElement(text).jsonObject["timeSeries"]?.jsonArray ?: return null
        val nearest = series.map { it.jsonObject }
            .mapNotNull { e -> e["time"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it).toEpochMilli() to e } }
            .minByOrNull { (at, _) -> kotlin.math.abs(at - nowMs) }?.second ?: return null
        val data = nearest["data"] as? JsonObject ?: return null
        val temp = data["air_temperature"]?.jsonPrimitive?.doubleOrNull ?: return null
        val symbol = data["symbol_code"]?.jsonPrimitive?.intOrNull ?: return null
        if (symbol !in 1..27) return null
        DisplayWeather(temp.roundToInt(), symbol)
    }.getOrNull()

    fun kindOf(symbol: Int): WeatherKind = when (symbol) {
        1, 2 -> WeatherKind.CLEAR
        3, 4 -> WeatherKind.PARTLY
        5, 6 -> WeatherKind.CLOUDY
        7 -> WeatherKind.FOG
        8, 9, 10, 18, 19, 20 -> WeatherKind.RAIN
        11, 21 -> WeatherKind.THUNDER
        12, 13, 14, 22, 23, 24 -> WeatherKind.SLEET
        else -> WeatherKind.SNOW
    }

    fun swedish(symbol: Int): String = SWEDISH.getOrElse(symbol - 1) { "" }

    private val SWEDISH = listOf(
        "Klart", "Lätt molnighet", "Halvklart", "Molnigt", "Mycket moln", "Mulet", "Dimma",
        "Lätta regnskurar", "Regnskurar", "Kraftiga regnskurar", "Åskskurar",
        "Lätta byar av snöblandat regn", "Byar av snöblandat regn", "Kraftiga byar av snöblandat regn",
        "Lätta snöbyar", "Snöbyar", "Kraftiga snöbyar",
        "Lätt regn", "Regn", "Kraftigt regn", "Åska",
        "Lätt snöblandat regn", "Snöblandat regn", "Kraftigt snöblandat regn",
        "Lätt snöfall", "Snöfall", "Ymnigt snöfall",
    )
}
