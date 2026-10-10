package se.eldebosh.nastastopp.settings

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.eldebosh.nastastopp.core.nav.CompanyServer
import se.eldebosh.nastastopp.core.nav.DeviceToken
import se.eldebosh.nastastopp.core.nav.HttpAsk
import se.eldebosh.nastastopp.core.nav.RoutesApi
import se.eldebosh.nastastopp.core.nav.ServerTrouble
import se.eldebosh.nastastopp.core.nav.WayAccess
import se.eldebosh.nastastopp.core.nav.WayRequests
import se.eldebosh.nastastopp.core.nav.WaySource
import se.eldebosh.nastastopp.core.nav.WayService
import se.eldebosh.nastastopp.net.Https
import se.eldebosh.nastastopp.net.Reply
import se.eldebosh.nastastopp.net.WayTransport
import se.eldebosh.nastastopp.util.Crypto
import se.eldebosh.nastastopp.util.KeystoreCrypto
import se.eldebosh.nastastopp.util.writeWhole
import java.io.File

/** Where this tablet stands with the company's server (311). */
sealed interface DeviceState {
    /** Never connected, or disconnected: the map uses the driver's own keys, if he typed any. */
    data object NotConnected : DeviceState

    /** A connection is saved but can no longer be opened (its key is gone): a new code is needed. */
    data object Lost : DeviceState

    /**
     * Connected as [label] ("tablet-40274"). [mapKey]: the company's map key (null: the server has
     * none yet); [google] and [mapmap]: the ways the server can ask; [limits]: until when (wall
     * clock) each service has reached today's limit; [down]: the server did not answer last time.
     */
    data class Connected(
        val label: String,
        val mapKey: String?,
        val google: Boolean,
        val mapmap: Boolean,
        val limits: Map<WayService, Long> = emptyMap(),
        val down: Boolean = false,
    ) : DeviceState {
        override fun toString() = "Connected($label, key=${if (mapKey == null) "none" else "***"}, google=$google, mapmap=$mapmap, down=$down)"
    }

    /** The owner stopped this tablet on the admin page: no map and no ways until he starts it again. */
    data class Stopped(val label: String) : DeviceState

    /** The server no longer knows this tablet: a new code is needed. */
    data object Unknown : DeviceState
}

/** What came of Connect (314). */
sealed interface EnrollResult {
    data class Connected(val label: String) : EnrollResult

    /** The server refused the code: wrong, used or older than 7 days. */
    data object WrongCode : EnrollResult

    /** No answer: no internet, or the server is away. */
    data object Offline : EnrollResult

    data class ServerError(val code: Int) : EnrollResult
}

/**
 * This tablet's connection to the company's server, api.nastastopp.se ([CompanyServer]): made once
 * with the code the owner gives (enroll), then the map key and which ways the server can ask
 * ([refresh]), and how its refusals change them ([onTrouble]).
 *
 * The pass ([DeviceToken]) and the company's map key are kept sealed in one file in
 * noBackupFilesDir, with a Keystore key of their own ([KeystoreCrypto.DEVICE]), so YouDrive's
 * Delete never destroys it. The pass stays in this object: it is read only by [access], for the
 * Authorization header of the asks to the server, never put in the screens' state, the settings,
 * the map page, the Bluetooth link or a log. Nothing here is logged.
 *
 * The driver's own keys (208, 307) are never touched here: they stay in the settings, unused while
 * the tablet is connected, and are used again after [disconnect].
 *
 * @param wall the wall clock (the daily limits' times); [now] the monotonic one (when the server
 * was last asked).
 */
class CompanyDevice(
    context: Context,
    private val crypto: Crypto = KeystoreCrypto(KeystoreCrypto.DEVICE),
    private val transport: WayTransport = Https,
    private val wall: () -> Long = System::currentTimeMillis,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    /** What is sealed: the pass, the label, the company's map key, the ways the server can ask, and a stop or unknown. */
    @Serializable
    private class Stored(
        val t: String,
        val l: String,
        val k: String? = null,
        val g: Boolean = false,
        val m: Boolean = false,
        val x: String? = null,
    )

    private val file = File(context.applicationContext.noBackupFilesDir, FILE)
    private val lock = Any()

    /** The open record, or null; its pass never leaves this object except in [access]. */
    private var stored: Stored? = null

    /** When the server's /v1/config was last asked (monotonic), to ask it at most every [CONFIG_MS]. */
    private var configAt: Long? = null

    /** Until when (wall clock) each service has reached today's limit; in memory only. */
    private val limits = mutableMapOf<WayService, Long>()

    private val _state = MutableStateFlow(open())
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    /** How the tablet asks for its ways while connected: through the server with its pass; else [WayAccess.None]. */
    fun access(): WayAccess = synchronized(lock) {
        val s = stored
        val connected = _state.value as? DeviceState.Connected
        if (s == null || connected == null || !DeviceToken.isToken(s.t)) WayAccess.None else WayAccess.Company(DeviceToken(s.t), connected.google, connected.mapmap)
    }

    /** Connects this tablet with the code [typed] on it (312); only the code and the role are sent. */
    suspend fun enroll(typed: String): EnrollResult {
        val code = CompanyServer.code(typed) ?: return EnrollResult.WrongCode
        // One block with no suspension inside: once the server has used the code, its pass is
        // saved even if the screen that asked has gone.
        return withContext(Dispatchers.IO) { enrollNow(code) }
    }

    private fun enrollNow(code: String): EnrollResult {
        val reply = transport.send(HttpAsk(CompanyServer.ENROLL, mapOf("User-Agent" to CompanyServer.USER_AGENT), CompanyServer.enrollBody(code), WayRequests.SERVER_READ_TIMEOUT_MS, viaServer = true))
        return when (reply.code) {
            OK -> {
                val enrolled = reply.text?.let(CompanyServer::parseEnroll) ?: return EnrollResult.ServerError(OK)
                synchronized(lock) {
                    configAt = null
                    limits.clear()
                    seal(Stored(enrolled.token.value, enrolled.label))
                }
                // The map key and the ways at once: the tablet shows as connected with them, so a
                // map is made once, with the right key.
                if (!refreshNow()) {
                    synchronized(lock) {
                        // The server did not say: connected, and asked again by itself ([keepFresh]).
                        if (stored?.t == enrolled.token.value) _state.value = DeviceState.Connected(enrolled.label, null, google = false, mapmap = false, down = true)
                    }
                }
                EnrollResult.Connected(enrolled.label)
            }
            BAD_REQUEST, FORBIDDEN -> EnrollResult.WrongCode
            NONE -> EnrollResult.Offline
            else -> EnrollResult.ServerError(reply.code)
        }
    }

    /**
     * Asks the server for the map key and the ways it can ask: at most every [CONFIG_MS], unless
     * [force] (the driver's Check now, 317).
     */
    suspend fun refresh(force: Boolean) {
        withContext(Dispatchers.IO) {
            val due = synchronized(lock) {
                val last = configAt
                stored != null && (force || last == null || now() - last >= CONFIG_MS)
            }
            if (due) refreshNow()
        }
    }

    /** Asks /v1/config now; whether the server's answer said where the tablet stands. */
    private fun refreshNow(): Boolean {
        val token = synchronized(lock) {
            configAt = now()
            stored?.t?.takeIf { DeviceToken.isToken(it) }?.let(::DeviceToken)
        } ?: return false
        val reply = transport.send(HttpAsk(CompanyServer.CONFIG, CompanyServer.headers(token), null, WayRequests.SERVER_READ_TIMEOUT_MS, viaServer = true))
        synchronized(lock) {
            // Disconnected or connected anew meanwhile: this answer is about a pass no longer kept.
            if (stored?.t != token.value) return false
            return configured(reply)
        }
    }

    /** Reads the answer to /v1/config; whether it said where the tablet stands. */
    private fun configured(reply: Reply): Boolean {
        val s = stored ?: return false
        if (reply.code == OK) {
            val config = reply.text?.let(CompanyServer::parseConfig)
            if (config == null) {
                down()
                return false
            }
            // A key is replaced only by another valid one: a new key is a new (billed) map.
            val key = config.mapsJsKey ?: s.k
            seal(Stored(s.t, config.label ?: s.l, key, config.google, config.mapmap))
            _state.value = DeviceState.Connected(config.label ?: s.l, key, config.google, config.mapmap, limitsNow())
            return true
        }
        when (CompanyServer.classify(reply.code, reply.slug, reply.retryAfterSec, wall()) ?: plain(reply.code)) {
            ServerTrouble.NotEnrolled -> unknown()
            ServerTrouble.Stopped, ServerTrouble.Role -> stopped()
            ServerTrouble.NoKey -> {
                seal(Stored(s.t, s.l, null, s.g, s.m))
                _state.value = DeviceState.Connected(s.l, null, s.g, s.m, limitsNow())
            }
            // Its own limit for today, or an answer that is not about the server: the map key and the ways stay as they were.
            is ServerTrouble.DailyLimit, is ServerTrouble.Bug, ServerTrouble.UpstreamDown -> return false
            ServerTrouble.Down -> {
                down()
                return false
            }
        }
        return true
    }

    /** An answer without the server's own word: none, or one from on the way (a proxy). Only 401 is the server's. */
    private fun plain(code: Int): ServerTrouble = if (code == UNAUTHORIZED) ServerTrouble.NotEnrolled else ServerTrouble.Down

    /**
     * Keeps the connection up to date while the display is in sight (its lifecycle cancels it):
     * once (at most every [CONFIG_MS]), then every [RETRY_MS] while the server has no map key yet
     * or does not answer. Silent: only the rows' words change.
     */
    suspend fun keepFresh() {
        refresh(force = false)
        while (currentCoroutineContext().isActive) {
            delay(RETRY_MS)
            val s = _state.value
            if (s is DeviceState.Connected && (s.mapKey == null || s.down)) refresh(force = true)
        }
    }

    /** The server's refusal of an ask of [service] for the map's ways ([se.eldebosh.nastastopp.ui.screens.RouteMap.onServerTrouble]). */
    fun onTrouble(service: WayService, trouble: ServerTrouble) {
        synchronized(lock) {
            val s = stored ?: return
            val connected = _state.value as? DeviceState.Connected
            when (trouble) {
                ServerTrouble.NotEnrolled -> unknown()
                ServerTrouble.Stopped, ServerTrouble.Role -> stopped()
                is ServerTrouble.DailyLimit -> if (connected != null) {
                    limits[service] = trouble.untilWallMs
                    _state.value = connected.copy(limits = limitsNow())
                }
                ServerTrouble.NoKey -> if (connected != null) {
                    // The server has no key for this way: the other is asked, until /v1/config says otherwise.
                    val google = s.g && service.source != WaySource.GOOGLE
                    val mapmap = s.m && service.source != WaySource.MAPMAP
                    seal(Stored(s.t, s.l, s.k, google, mapmap))
                    _state.value = connected.copy(google = google, mapmap = mapmap)
                }
                ServerTrouble.Down -> if (connected != null) _state.value = connected.copy(down = true)
                // The server answered (Google or mapmap behind it did not), or refused one request: the connection is up.
                ServerTrouble.UpstreamDown, is ServerTrouble.Bug -> Unit
            }
        }
    }

    /**
     * Disconnects this tablet (318 → 320): tells the server it leaves (it is then stopped there),
     * then forgets the pass here and destroys its key. The driver's own keys are used again.
     */
    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            val token = synchronized(lock) { stored?.t?.takeIf { DeviceToken.isToken(it) }?.let(::DeviceToken) }
            // Best effort: without an answer the pass is forgotten here all the same, and the owner can stop it.
            if (token != null) transport.send(HttpAsk(CompanyServer.LEAVE, CompanyServer.headers(token), "{}", WayRequests.SERVER_READ_TIMEOUT_MS, viaServer = true))
            synchronized(lock) {
                stored = null
                configAt = null
                limits.clear()
                file.delete()
                runCatching { crypto.forget() }
                _state.value = DeviceState.NotConnected
            }
        }
    }

    override fun toString() = "CompanyDevice(***)"

    private fun limitsNow(): Map<WayService, Long> {
        val at = wall()
        limits.entries.removeAll { it.value <= at }
        return limits.toMap()
    }

    /** No answer: a connected tablet keeps its map key and says so (a stopped or unknown one stays so). */
    private fun down() {
        val connected = _state.value as? DeviceState.Connected ?: return
        _state.value = connected.copy(down = true)
    }

    /** Stopped by the owner: the map key is dropped, the pass kept (his Start on the admin page brings it back). */
    private fun stopped() {
        val s = stored ?: return
        seal(Stored(s.t, s.l, null, s.g, s.m, STOPPED))
        _state.value = DeviceState.Stopped(s.l)
    }

    /** Unknown to the server: the map key is dropped; a new code connects it again. */
    private fun unknown() {
        val s = stored ?: return
        seal(Stored(s.t, s.l, null, s.g, s.m, UNKNOWN))
        _state.value = DeviceState.Unknown
    }

    /** Opens the saved record, once. */
    private fun open(): DeviceState {
        if (!file.exists()) return DeviceState.NotConnected
        val s = runCatching {
            json.decodeFromString(Stored.serializer(), String(crypto.decrypt(Base64.decode(file.readText(), Base64.NO_WRAP)), Charsets.UTF_8))
        }.getOrNull()?.takeIf { DeviceToken.isToken(it.t) } ?: return DeviceState.Lost
        stored = s
        return when (s.x) {
            STOPPED -> DeviceState.Stopped(s.l)
            UNKNOWN -> DeviceState.Unknown
            // The key goes into the map page: only one as Google issues them.
            else -> DeviceState.Connected(s.l, s.k?.takeIf { RoutesApi.isKey(it) }, s.g, s.m)
        }
    }

    /** Seals [s] and writes it whole; it is then the record. */
    private fun seal(s: Stored) {
        stored = s
        runCatching {
            val sealed = crypto.encrypt(json.encodeToString(Stored.serializer(), s).toByteArray(Charsets.UTF_8))
            writeWhole(file, Base64.encodeToString(sealed, Base64.NO_WRAP))
        }
    }

    companion object {
        /** The sealed record's file, in noBackupFilesDir (never backed up). */
        const val FILE = "company_device"

        /** /v1/config is asked at most this often (the server allows it 200 times a day). */
        const val CONFIG_MS = 10 * 60_000L

        /** While the server has no map key or does not answer, it is asked again after this long. */
        const val RETRY_MS = 5 * 60_000L

        private const val STOPPED = "stopped"
        private const val UNKNOWN = "unknown"
        private const val OK = 200
        private const val BAD_REQUEST = 400
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val NONE = 0

        private val json = Json { ignoreUnknownKeys = true }
    }
}
