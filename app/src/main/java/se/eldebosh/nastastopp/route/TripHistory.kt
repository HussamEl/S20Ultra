package se.eldebosh.nastastopp.route

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.util.DebugLog
import java.io.File

/**
 * A trip that was part of a route: kept on the Home screen after the route ends. Only what is
 * shown is stored — time, area and address — never names or other text.
 */
@Serializable
data class HistoryEntry(
    val id: Long,
    /** Scheduled time from the screenshot, or null. */
    val time: String? = null,
    /** Spoken area name at the time of the trip. */
    val area: String,
    val displayText: String,
    /** When the trip was completed (or the route ended). */
    val atMs: Long,
    /** False for trips still open when the driver pressed "Avsluta". */
    val done: Boolean = true,
)

@Serializable
private data class HistoryFile(val entries: List<HistoryEntry> = emptyList(), val nextId: Long = 1)

/**
 * Trip history shown on the Home screen. Stored in app-private, no-backup storage; each trip is
 * deleted automatically after the retention chosen in Settings (12 h by default), and all of it
 * with "Clear history".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripHistory(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val io = Dispatchers.IO.limitedParallelism(1)

    private var data: HistoryFile = load()
    private val _entries = MutableStateFlow(data.entries)

    /** Oldest first. */
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    init {
        prune()
        scope.launch {
            settings.state.map { it.historyRetentionHours }.distinctUntilChanged().collect { prune() }
        }
    }

    private fun retentionMs(): Long = settings.current.historyRetentionHours.coerceAtLeast(1) * 3_600_000L

    fun add(time: String?, area: String, displayText: String, done: Boolean, now: Long = System.currentTimeMillis()) {
        val entry = HistoryEntry(data.nextId, time, area, displayText, now, done)
        commit(HistoryFile(data.entries + entry, data.nextId + 1))
    }

    fun clear() = commit(HistoryFile(emptyList(), data.nextId))

    /** Drops trips older than the retention period. */
    fun prune(now: Long = System.currentTimeMillis()) {
        val keep = data.entries.filter { now - it.atMs <= retentionMs() && it.atMs <= now + 60_000 }
        if (keep.size != data.entries.size) commit(HistoryFile(keep, data.nextId)) else scheduleExpiry()
    }

    private fun commit(next: HistoryFile) {
        data = next
        _entries.value = next.entries
        val snapshot = next
        scope.launch(io) {
            try {
                if (snapshot.entries.isEmpty()) {
                    file.delete()
                } else {
                    val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                    tmp.writeText(json.encodeToString(HistoryFile.serializer(), snapshot))
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        tmp.renameTo(file)
                    }
                }
            } catch (e: Exception) {
                DebugLog.w(e) { "history write failed" }
            }
        }
        scheduleExpiry()
    }

    private fun load(): HistoryFile {
        if (!file.exists()) return HistoryFile()
        return try {
            json.decodeFromString(HistoryFile.serializer(), file.readText())
        } catch (e: Exception) {
            DebugLog.w(e) { "history unreadable, deleting" }
            file.delete()
            HistoryFile()
        }
    }

    /** Wakes the app when the oldest trip expires (inexact alarm; no special permission). */
    private fun scheduleExpiry() {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, REQ_HISTORY_EXPIRY, Intent(context, ExpiryReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val oldest = data.entries.minOfOrNull { it.atMs }
        if (oldest == null) am.cancel(pi) else am.set(AlarmManager.RTC, oldest + retentionMs() + 1_000, pi)
    }

    companion object {
        const val FILE_NAME = "history.json"
        private const val REQ_HISTORY_EXPIRY = 13
    }
}
