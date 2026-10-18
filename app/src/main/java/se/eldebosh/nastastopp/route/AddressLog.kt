package se.eldebosh.nastastopp.route

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.eldebosh.nastastopp.core.geo.Coordinates
import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.util.DebugLog
import se.eldebosh.nastastopp.util.writeWhole
import java.io.File
import java.time.LocalDate

/**
 * An address the route went to: as written (place, street and number, town; never a name), on how
 * many days, and the last; with the address's own point once it was found (none in older files).
 */
@Serializable
data class LoggedAddress(val text: String, val days: Int = 1, val lastDay: String, val lat: Double? = null, val lng: Double? = null)

/**
 * The addresses the routes went to, one each, so the driver can share them to improve how long
 * addresses are said; never a passenger's name, never the trip's card, never the driver's entrance.
 * In one small file in the app's own storage that is never backed up, until the driver deletes it
 * (Settings 303); never logged.
 */
class AddressLog(context: Context) {
    private val file = File(context.applicationContext.noBackupFilesDir, FILE)
    private val state = MutableStateFlow(load())
    val all: StateFlow<Map<String, LoggedAddress>> = state.asStateFlow()

    /** One address of a route ([RouteController.logEntry]): its text and the address's point, when found. */
    data class Line(val text: String, val lat: Double? = null, val lng: Double? = null)

    /**
     * Keeps [lines], each counted once a day. A point found after the address was first kept that
     * day is added to it.
     */
    @Synchronized
    fun record(lines: List<Line>, today: LocalDate = LocalDate.now()) {
        val day = today.toString()
        var value = state.value
        for (line in lines) {
            val key = TextNorm.key(line.text).ifEmpty { continue }
            val old = value[key]
            val point = if (line.lat != null && line.lng != null) line.lat to line.lng else old?.lat?.let { lat -> old.lng?.let { lat to it } }
            if (old != null && old.lastDay == day) {
                if (old.lat == null && point != null) value = value + (key to old.copy(lat = point.first, lng = point.second))
                continue
            }
            value = value + (key to LoggedAddress(line.text, (old?.days ?: 0) + 1, day, point?.first, point?.second))
        }
        if (value != state.value) save(value)
    }

    /**
     * The addresses as lines to share: the address, its point ("59.381234, 13.501234", empty when
     * not found), then on how many days and the last day.
     */
    fun asText(): String = state.value.values.sortedBy { it.text.lowercase() }.joinToString("\n") { a ->
        val point = a.lat?.let { lat -> a.lng?.let { Coordinates.format(lat, it) } }.orEmpty()
        "${a.text}\t$point\t${a.days}\t${a.lastDay}"
    }

    @Synchronized
    fun clear() = save(emptyMap())

    private fun load(): Map<String, LoggedAddress> = runCatching {
        if (!file.exists()) emptyMap() else json.decodeFromString<Map<String, LoggedAddress>>(file.readText())
    }.getOrElse {
        DebugLog.w(it) { "address log unreadable" }
        emptyMap()
    }

    private fun save(value: Map<String, LoggedAddress>) {
        state.value = value
        runCatching {
            val text = json.encodeToString(value)
            writeWhole(file, text)
        }.onFailure { DebugLog.w(it) { "address log not saved" } }
    }

    internal companion object {
        const val FILE = "address_log.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}
