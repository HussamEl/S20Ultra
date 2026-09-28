package se.eldebosh.nastastopp.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.core.link.LinkMessage
import se.eldebosh.nastastopp.core.link.LinkProtocol
import se.eldebosh.nastastopp.core.link.LinkSession
import se.eldebosh.nastastopp.route.RouteController
import se.eldebosh.nastastopp.settings.DeviceRole
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.util.DebugLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Controller side of the passenger display link: a secure Bluetooth RFCOMM server (paired
 * devices only, encrypted by Bluetooth; no internet). Every connected display gets the current
 * [se.eldebosh.nastastopp.core.display.DisplaySnapshot] on connect and on each change, plus the
 * announcements as they are spoken.
 */
class DisplayLinkServer(
    private val context: Context,
    private val controller: RouteController,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    enum class Status { OFF, NO_PERMISSION, NO_BLUETOOTH, BLUETOOTH_OFF, WAITING, CONNECTED }

    data class State(val status: Status = Status.OFF, val clients: List<String> = emptyList())

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var acceptJob: Job? = null
    @Volatile private var serverSocket: BluetoothServerSocket? = null
    private val sockets = ConcurrentHashMap<BluetoothSocket, String>()

    private val enabled: Boolean
        get() = settings.current.role == DeviceRole.CONTROLLER && settings.current.displayLinkEnabled

    init {
        scope.launch {
            settings.state.map { it.role == DeviceRole.CONTROLLER && it.displayLinkEnabled }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    /** (Re)starts or stops listening according to the setting, permission and Bluetooth state. */
    fun refresh() {
        stop()
        if (!enabled) {
            _state.value = State(Status.OFF)
            return
        }
        when (Bluetooth.availability(context)) {
            LinkAvailability.NO_BLUETOOTH -> _state.value = State(Status.NO_BLUETOOTH)
            LinkAvailability.NO_PERMISSION -> _state.value = State(Status.NO_PERMISSION)
            LinkAvailability.BLUETOOTH_OFF -> _state.value = State(Status.BLUETOOTH_OFF)
            LinkAvailability.OK -> startListening()
        }
    }

    @SuppressLint("MissingPermission") // checked in refresh()
    private fun startListening() {
        val adapter = Bluetooth.adapter(context) ?: return
        _state.value = State(Status.WAITING)
        acceptJob = scope.launch(Dispatchers.IO) {
            val server = try {
                adapter.listenUsingRfcommWithServiceRecord(LinkProtocol.SERVICE_NAME, LinkProtocol.SERVICE_UUID)
            } catch (e: Exception) {
                DebugLog.w(e) { "listen failed" }
                _state.value = State(Status.BLUETOOTH_OFF)
                return@launch
            }
            serverSocket = server
            while (isActive) {
                val socket = try {
                    server.accept()
                } catch (_: Exception) {
                    break // closed by stop() or Bluetooth turned off
                }
                launch { serve(socket) }
            }
            // Not stopped by us: Bluetooth was probably turned off.
            if (isActive) _state.value = State(Status.BLUETOOTH_OFF)
        }
    }

    /** Called when the app comes to the foreground: retries if the link is not running. */
    fun refreshIfIdle() {
        val s = _state.value.status
        if (s != Status.WAITING && s != Status.CONNECTED) refresh()
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        sockets.keys.forEach { s ->
            try {
                s.close()
            } catch (_: Exception) {
            }
        }
        sockets.clear()
    }

    @SuppressLint("MissingPermission")
    private suspend fun serve(socket: BluetoothSocket) {
        val name = try {
            socket.remoteDevice?.name?.takeIf { it.isNotBlank() } ?: socket.remoteDevice?.address ?: "?"
        } catch (_: SecurityException) {
            "?"
        }
        sockets[socket] = name
        publishClients()
        val session = LinkSession(socket.inputStream, socket.outputStream)
        val done = CompletableDeferred<Unit>()
        fun io(block: suspend () -> Unit): Job = scope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (_: Exception) {
            } finally {
                done.complete(Unit)
            }
        }
        val jobs = listOf(
            io { session.send(LinkMessage.Hello(LinkProtocol.VERSION, LinkProtocol.ROLE_CONTROLLER)); controller.display.collect { session.send(LinkMessage.State(it)) } },
            io { controller.announcements.collect { session.send(LinkMessage.Announce(it.swedish, it.english)) } },
            io {
                while (true) {
                    delay(PING_MS)
                    session.send(LinkMessage.Ping)
                }
            },
            io {
                @Suppress("ControlFlowWithEmptyBody")
                while (session.receive() != null) {
                }
            },
        )
        done.await()
        jobs.forEach { it.cancel() }
        try {
            socket.close()
        } catch (_: Exception) {
        }
        session.close()
        sockets.remove(socket)
        publishClients()
    }

    private fun publishClients() {
        val names = sockets.values.toList()
        val current = _state.value.status
        if (current == Status.WAITING || current == Status.CONNECTED) {
            _state.value = State(if (names.isEmpty()) Status.WAITING else Status.CONNECTED, names)
        }
    }

    companion object {
        private const val PING_MS = 10_000L
    }
}
