package se.eldebosh.nastastopp.route

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.util.DebugLog
import java.io.File
import java.time.LocalDate

/** An address the route went to: as written (place, street and number, town; never a name), on how many days, and the last. */
@Serializable
data class LoggedAddress(val text: String, val days: Int = 1, val lastDay: String)

/**
 * The addresses the routes went to, one each, so the driver can share them to improve how long
 * addresses are said; never a passenger's name, never the trip's card. In one small file in the
 * app's own storage that is never backed up, until the driver deletes it (Settings 303); never
 * logged.
 */
class AddressLog(context: Context) {
    private val file = File(context.applicationContext.noBackupFilesDir, FILE)
    private val state = MutableStateFlow(load())
    val all: StateFlow<Map<String, LoggedAddress>> = state.asStateFlow()

    /** Keeps [texts], each counted once a day. */
    @Synchronized
    fun record(texts: List<String>, today: LocalDate = LocalDate.now()) {
        val day = today.toString()
        var value = state.value
        for (text in texts) {
            val key = TextNorm.key(text).ifEmpty { continue }
            val old = value[key]
            if (old != null && old.lastDay == day) continue
            value = value + (key to LoggedAddress(text, (old?.days ?: 0) + 1, day))
        }
        if (value != state.value) save(value)
    }

    /** The addresses as lines to share: the address, then on how many days and the last day. */
    fun asText(): String = state.value.values.sortedBy { it.text.lowercase() }.joinToString("\n") { "${it.text}\t${it.days}\t${it.lastDay}" }

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
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }.onFailure { DebugLog.w(it) { "address log not saved" } }
    }

    private companion object {
        const val FILE = "address_log.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}
