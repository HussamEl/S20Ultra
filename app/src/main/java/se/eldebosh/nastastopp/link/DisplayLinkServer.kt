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
import kotlinx.coroutines.withContext
import se.eldebosh.nastastopp.core.link.LinkMessage
import se.eldebosh.nastastopp.core.link.LinkProtocol
import se.eldebosh.nastastopp.core.link.LinkSession
import se.eldebosh.nastastopp.geo.CurrentStreet
import se.eldebosh.nastastopp.route.RouteController
import se.eldebosh.nastastopp.settings.DeviceRole
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.util.DebugLog
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Controller side of the passenger display link: Bluetooth RFCOMM servers (no internet) on the
 * secure channel and on a fallback channel. Only devices paired with this one are served. Every
 * connected display gets the current [se.eldebosh.nastastopp.core.display.DisplaySnapshot] on
 * connect and on each change, plus the announcements as they are spoken. A display's floating
 * panel may send back Next, Back and Repeat.
 */
class DisplayLinkServer(
    private val context: Context,
    private val controller: RouteController,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    enum class Status { OFF, NO_PERMISSION, NO_BLUETOOTH, BLUETOOTH_OFF, WAITING, CONNECTED }

    /**
     * @property clients names of connected displays.
     * @property localName this device's Bluetooth name (to pick on the tablet if needed).
     */
    data class State(
        val status: Status = Status.OFF,
        val clients: List<String> = emptyList(),
        val localName: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val acceptJobs = mutableListOf<Job>()
    private val serverSockets = ConcurrentHashMap.newKeySet<BluetoothServerSocket>()
    private val sockets = ConcurrentHashMap<BluetoothSocket, String>()
    private val sessions = ConcurrentHashMap.newKeySet<LinkSession>()

    private val _mapView = MutableStateFlow<LinkMessage.MapView?>(null)

    /** What a connected passenger display's map shows (null without one, or before it says). */
    val mapView: StateFlow<LinkMessage.MapView?> = _mapView.asStateFlow()

    /** A control used on this phone's floating panel, sent to the connected passenger displays. */
    fun remote(message: LinkMessage.Remote) {
        val to = sessions.toList()
        if (to.isEmpty()) return
        scope.launch(Dispatchers.IO) { to.forEach { s -> runCatching { s.send(message) } } }
    }

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
        val name = Bluetooth.localName(context)
        when (Bluetooth.availability(context)) {
            LinkAvailability.NO_BLUETOOTH -> _state.value = State(Status.NO_BLUETOOTH)
            LinkAvailability.NO_PERMISSION -> _state.value = State(Status.NO_PERMISSION)
            LinkAvailability.BLUETOOTH_OFF -> _state.value = State(Status.BLUETOOTH_OFF, localName = name)
            LinkAvailability.OK -> {
                _state.value = State(Status.WAITING, localName = name)
                listen(secure = true, LinkProtocol.SERVICE_UUID)
                listen(secure = false, LinkProtocol.SERVICE_UUID_INSECURE)
            }
        }
    }

    /** Called when the app comes to the foreground: retries if the link is not running. */
    fun refreshIfIdle() {
        val s = _state.value.status
        if (s != Status.WAITING && s != Status.CONNECTED) refresh()
    }

    @SuppressLint("MissingPermission") // checked in refresh()
    private fun listen(secure: Boolean, uuid: UUID) {
        val adapter = Bluetooth.adapter(context) ?: return
        acceptJobs += scope.launch(Dispatchers.IO) {
            val server = try {
                if (secure) {
                    adapter.listenUsingRfcommWithServiceRecord(LinkProtocol.SERVICE_NAME, uuid)
                } else {
                    adapter.listenUsingInsecureRfcommWithServiceRecord(LinkProtocol.SERVICE_NAME, uuid)
                }
            } catch (e: Exception) {
                DebugLog.w(e) { "listen failed (secure=$secure)" }
                return@launch
            }
            serverSockets += server
            while (isActive) {
                val socket = try {
                    server.accept()
                } catch (_: Exception) {
                    break // closed by stop() or Bluetooth turned off
                }
                // Only devices paired with this one (the fallback channel is not authenticated).
                val address = try {
                    socket.remoteDevice?.address
                } catch (_: Exception) {
                    null
                }
                if (address == null || !Bluetooth.isBonded(context, address)) {
                    runCatching { socket.close() }
                    continue
                }
                launch { serve(socket) }
            }
            serverSockets -= server
            // Not stopped by us: Bluetooth was probably turned off.
            if (isActive && serverSockets.isEmpty()) _state.value = State(Status.BLUETOOTH_OFF, localName = _state.value.localName)
        }
    }

    fun stop() {
        acceptJobs.forEach { it.cancel() }
        acceptJobs.clear()
        serverSockets.forEach { s -> runCatching { s.close() } }
        serverSockets.clear()
        sockets.keys.forEach { s -> runCatching { s.close() } }
        sockets.clear()
    }

    @SuppressLint("MissingPermission")
    private suspend fun serve(socket: BluetoothSocket) {
        val name = try {
            socket.remoteDevice?.name?.takeIf { it.isNotBlank() } ?: socket.remoteDevice?.address ?: "?"
        } catch (_: SecurityException) {
            "?"
        }
        val session = LinkSession(socket.inputStream, socket.outputStream)
        // The tablet says first what it is: a passenger display of this protocol, within [HELLO_MS].
        // Until then nothing is sent to it; anything else ends the link.
        val giveUp = scope.launch(Dispatchers.IO) {
            delay(HELLO_MS)
            runCatching { socket.close() }
        }
        val hello = runCatching { session.receive() }.getOrNull()
        giveUp.cancel()
        if (!LinkProtocol.isDisplayHello(hello)) {
            session.close()
            runCatching { socket.close() }
            return
        }
        sockets[socket] = name
        sessions.add(session)
        publishClients()
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
            io {
                session.send(LinkMessage.Hello(LinkProtocol.VERSION, LinkProtocol.ROLE_CONTROLLER))
                controller.display.collect { session.send(LinkMessage.State(it)) }
            },
            io { controller.announcements.collect { session.send(LinkMessage.Announce(it.swedish, it.english)) } },
            io {
                while (true) {
                    delay(PING_MS)
                    session.send(LinkMessage.Ping)
                }
            },
            io {
                while (true) {
                    val message = session.receive() ?: break
                    when (message) {
                        is LinkMessage.Command -> scope.launch(Dispatchers.Main) { carryOut(message.action) }
                        is LinkMessage.Order -> scope.launch(Dispatchers.Main) { controller.reorder(message.ids) }
                        is LinkMessage.MapView -> _mapView.value = message
                        else -> Unit
                    }
                }
            },
        )
        done.await()
        jobs.forEach { it.cancel() }
        runCatching { socket.close() }
        session.close()
        sessions.remove(session)
        sockets.remove(socket)
        if (sessions.isEmpty()) _mapView.value = null
        publishClients()
    }

    /** Next, Back or Repeat pressed on a tablet's floating panel: as if pressed on the phone's. */
    private fun carryOut(action: LinkMessage.Command.Action) {
        when (action) {
            LinkMessage.Command.Action.NEXT -> controller.next()
            LinkMessage.Command.Action.BACK -> controller.back()
            LinkMessage.Command.Action.REPEAT -> controller.repeat()
        }
    }

    private fun publishClients() {
        val names = sockets.values.toList()
        val current = _state.value
        if (current.status == Status.WAITING || current.status == Status.CONNECTED) {
            _state.value = current.copy(status = if (names.isEmpty()) Status.WAITING else Status.CONNECTED, clients = names)
        }
    }

    companion object {
        private const val PING_MS = 10_000L

        /** A tablet that has not said it is a passenger display after this long is let go. */
        private const val HELLO_MS = 10_000L
    }
}
