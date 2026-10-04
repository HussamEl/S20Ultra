package se.eldebosh.nastastopp.route

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.serialization.json.Json
import se.eldebosh.nastastopp.route.model.RouteData
import se.eldebosh.nastastopp.util.DebugLog
import se.eldebosh.nastastopp.util.writeWhole
import java.io.File

/**
 * Persists the route in app-private, no-backup storage. Data older than [MAX_AGE_MS] is deleted
 * on every load, and an alarm deletes it automatically when it expires.
 */
class RouteRepository(private val context: Context) {

    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Synchronized
    fun load(now: Long = System.currentTimeMillis()): RouteData? {
        if (!file.exists()) return null
        val data = runCatching { json.decodeFromString(RouteData.serializer(), file.readText()) }
            .onFailure { DebugLog.w(it) { "route file unreadable, deleting" } }
            .getOrNull()
        if (data == null || isExpired(data, now)) {
            clear()
            return null
        }
        return data
    }

    @Synchronized
    fun save(data: RouteData) {
        writeWhole(file, json.encodeToString(RouteData.serializer(), data))
    }

    @Synchronized
    fun clear() {
        file.delete()
        File(file.parentFile, "$FILE_NAME.tmp").delete()
        cancelExpiry()
    }

    fun isExpired(data: RouteData, now: Long = System.currentTimeMillis()) =
        now - data.createdAtMs > MAX_AGE_MS || data.createdAtMs > now + 60_000

    /** Schedules automatic deletion at createdAt + 12 h (inexact alarm; no special permission). */
    fun scheduleExpiry(createdAtMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.set(AlarmManager.RTC, createdAtMs + MAX_AGE_MS + 1_000, expiryIntent())
    }

    private fun cancelExpiry() {
        context.getSystemService(AlarmManager::class.java)?.cancel(expiryIntent())
    }

    private fun expiryIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQ_EXPIRY,
        Intent(context, ExpiryReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val FILE_NAME = "route.json"
        const val MAX_AGE_MS = 12L * 60 * 60 * 1000
        private const val REQ_EXPIRY = 12
    }
}
