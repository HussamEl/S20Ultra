package se.eldebosh.nastastopp.route

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.eldebosh.nastastopp.util.DebugLog
import java.io.File

/**
 * Where the car really stops for an address, as the driver set it: its point (an entrance on the
 * right side of the building, a yard) and a note on how to get in. It is kept apart from the
 * address's own point (the phone's geocoder, on the stop), never replaces it, and goes with the
 * address the next time it comes: Google Maps navigates to it and the tablet's map shows it.
 */
@Serializable
data class Entrance(
    /** The address as the trip wrote it when the driver saved this. */
    val address: String,
    /** The stopping point, or null for a note alone. */
    val lat: Double? = null,
    val lng: Double? = null,
    val note: String? = null,
    /** When the driver first saved it, and when he last saved it again (changed or confirmed). */
    val savedAtMs: Long,
    val updatedAtMs: Long = savedAtMs,
) {
    val hasPoint: Boolean get() = lat != null && lng != null
}

/**
 * The driver's entrances by address ([se.eldebosh.nastastopp.route.model.Stop.entranceKey]: street,
 * number and postal code or town; never a name).
 */
interface Entrances {
    val all: StateFlow<Map<String, Entrance>>

    fun set(key: String, entrance: Entrance)

    fun remove(key: String)

    fun clear()

    /** Keeps nothing (tests). */
    class None : Entrances {
        private val state = MutableStateFlow<Map<String, Entrance>>(emptyMap())
        override val all: StateFlow<Map<String, Entrance>> = state.asStateFlow()

        override fun set(key: String, entrance: Entrance) {
            state.value = state.value + (key to entrance)
        }

        override fun remove(key: String) {
            state.value = state.value - key
        }

        override fun clear() {
            state.value = emptyMap()
        }
    }
}

/**
 * [Entrances] in one small file in the app's own storage that is never backed up, until the
 * driver deletes them (Settings 259); never logged.
 */
class StoredEntrances(context: Context) : Entrances {
    private val file = File(context.applicationContext.noBackupFilesDir, FILE)
    private val state = MutableStateFlow(load())
    override val all: StateFlow<Map<String, Entrance>> = state.asStateFlow()

    override fun set(key: String, entrance: Entrance) = save(state.value + (key to entrance))

    override fun remove(key: String) = save(state.value - key)

    override fun clear() = save(emptyMap())

    private fun load(): Map<String, Entrance> = runCatching {
        if (!file.exists()) emptyMap() else json.decodeFromString<Map<String, Entrance>>(file.readText())
    }.getOrElse {
        DebugLog.w(it) { "entrances unreadable" }
        emptyMap()
    }

    @Synchronized
    private fun save(value: Map<String, Entrance>) {
        state.value = value
        runCatching {
            val text = json.encodeToString(value)
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }.onFailure { DebugLog.w(it) { "entrances not saved" } }
    }

    private companion object {
        const val FILE = "entrances.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}
