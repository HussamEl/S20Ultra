package se.eldebosh.nastastopp.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.core.link.LinkCandidate
import se.eldebosh.nastastopp.core.link.LinkMessage
import se.eldebosh.nastastopp.core.link.LinkProtocol
import se.eldebosh.nastastopp.core.link.LinkSession
import se.eldebosh.nastastopp.core.link.LinkTargets
import se.eldebosh.nastastopp.core.route.Announcement
import se.eldebosh.nastastopp.util.DebugLog

/**
 * Passenger display side: finds the driver's device among the paired Bluetooth devices (the
 * chosen / last used one first), connects to its Nästa Stopp service and keeps the latest
 * [DisplaySnapshot]. Tries the secure channel, then the fallback channel; reconnects
 * automatically. Nothing is stored on this device except the last used device's address.
 *
 * @param onConnected called with the address of a device that accepted the connection.
 */
class DisplayLinkClient(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onConnected: (address: String) -> Unit = {},
) {

    enum class Status { IDLE, NO_PERMISSION, NO_BLUETOOTH, BLUETOOTH_OFF, NO_DEVICES, CONNECTING, CONNECTED }

    /**
     * @property deviceName device currently tried / connected.
     * @property lastError last failed attempt ("device: reason") for on-screen diagnostics.
     */
    data class State(
        val status: Status = Status.IDLE,
        val deviceName: String? = null,
        val lastError: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _snapshot = MutableStateFlow<DisplaySnapshot?>(null)
    val snapshot: StateFlow<DisplaySnapshot?> = _snapshot.asStateFlow()

    private val _announcements = MutableSharedFlow<Announcement>(extraBufferCapacity = 8)
    val announcements: SharedFlow<Announcement> = _announcements.asSharedFlow()

    private val _remotes = MutableSharedFlow<LinkMessage.Remote>(extraBufferCapacity = 16)

    /** The controls the driver used on the phone's floating panel, for this display to carry out. */
    val remotes: SharedFlow<LinkMessage.Remote> = _remotes.asSharedFlow()

    private var job: Job? = null
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var session: LinkSession? = null
    @Volatile private var lastReceived = 0L
    private var preferred: String? = null
    private var started = false

    /** The phone the shown trips came from, and the clearing of them a while after its link broke. */
    private var snapshotFrom: String? = null
    private var staleJob: Job? = null

    /**
     * Starts searching/connecting (idempotent). [preferredAddress] is tried first; null means
     * fully automatic.
     */
    fun start(preferredAddress: String?) {
        if (job?.isActive == true && started) {
            // Already running: a new preference is used from the next search round on, so a
            // working link is never dropped (e.g. when its own address is saved as preferred).
            preferred = preferredAddress
            return
        }
        restart(preferredAddress)
    }

    /** The user picked a device (or "automatic"): search again now, starting with it. */
    fun choose(preferredAddress: String?) = restart(preferredAddress)

    private fun restart(preferredAddress: String?) {
        stop()
        started = true
        preferred = preferredAddress
        job = scope.launch(Dispatchers.IO) {
            var lastError: String? = null
            while (isActive) {
                when (Bluetooth.availability(context)) {
                    LinkAvailability.NO_BLUETOOTH -> _state.value = State(Status.NO_BLUETOOTH)
                    LinkAvailability.NO_PERMISSION -> _state.value = State(Status.NO_PERMISSION)
                    LinkAvailability.BLUETOOTH_OFF -> _state.value = State(Status.BLUETOOTH_OFF)
                    LinkAvailability.OK -> {
                        val targets = LinkTargets.order(Bluetooth.linkCandidates(context), preferred)
                        if (targets.isEmpty()) _state.value = State(Status.NO_DEVICES, lastError = lastError)
                        for (target in targets) {
                            if (!isActive) break
                            _state.value = State(Status.CONNECTING, target.name, lastError)
                            val error = connect(target)
                            if (error == null) {
                                // Was connected (and the link has now ended): search again right away,
                                // starting with this device.
                                preferred = target.address
                                lastError = null
                                break
                            }
                            lastError = "${target.name}: $error"
                        }
                    }
                }
                if (isActive) delay(RETRY_MS)
            }
        }
    }

    /** Stops the link; the trips it brought are cleared at once (the display is left, or another phone is chosen). */
    fun stop() {
        started = false
        job?.cancel()
        job = null
        session = null
        closeSocket()
        staleJob?.cancel()
        _snapshot.value = null
        snapshotFrom = null
        _state.value = State(Status.IDLE)
    }

    /** Tries the secure channel, then the fallback channel. Returns null if a link was made, else the error. */
    @SuppressLint("MissingPermission") // availability() checked the permission
    private suspend fun connect(target: LinkCandidate): String? {
        val device = try {
            Bluetooth.adapter(context)?.getRemoteDevice(target.address)
        } catch (e: Exception) {
            return e.javaClass.simpleName
        } ?: return "no adapter"
        var error: String? = null
        for (secure in listOf(true, false)) {
            // The link was stopped (or another device chosen) while the last try was waiting.
            if (!currentCoroutineContext().isActive) return STOPPED
            val s = try {
                if (secure) {
                    device.createRfcommSocketToServiceRecord(LinkProtocol.SERVICE_UUID)
                } else {
                    device.createInsecureRfcommSocketToServiceRecord(LinkProtocol.SERVICE_UUID_INSECURE)
                }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                continue
            }
            socket = s
            try {
                s.connect() // blocking; fails if the driver's device does not run the link
            } catch (e: Exception) {
                error = (e.message ?: e.javaClass.simpleName).take(80)
                closeSocket()
                continue
            }
            error = runSession(s, target) ?: return null
        }
        return error
    }

    /**
     * One link with the phone at [target]: this tablet says first that it is a passenger display;
     * the phone answers that it is a controller of the same protocol, within [HELLO_MS], before the
     * link counts as made. Null once a link was made and has ended; else why none was.
     */
    private suspend fun runSession(s: BluetoothSocket, target: LinkCandidate): String? {
        var watchdog: Job? = null
        var linked = false
        try {
            val session = LinkSession(s.inputStream, s.outputStream)
            session.send(LinkMessage.Hello(LinkProtocol.VERSION, LinkProtocol.ROLE_DISPLAY))
            val giveUp = scope.launch(Dispatchers.IO) {
                delay(HELLO_MS)
                closeSocket()
            }
            val hello = runCatching { session.receive() }.getOrNull()
            giveUp.cancel()
            if (!currentCoroutineContext().isActive) return STOPPED
            if (!LinkProtocol.isControllerHello(hello)) return "no Nästa Stopp phone"
            linked = true
            staleJob?.cancel()
            // Another phone: nothing of the last one's trips stays.
            if (target.address != snapshotFrom) _snapshot.value = null
            this.session = session
            _state.value = State(Status.CONNECTED, target.name)
            onConnected(target.address)
            // The controller pings every 10 s; silence for 30 s means the link is dead.
            lastReceived = System.currentTimeMillis()
            watchdog = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(5_000)
                    if (System.currentTimeMillis() - lastReceived > SILENCE_MS) {
                        closeSocket()
                        break
                    }
                }
            }
            while (true) {
                val msg = session.receive() ?: break
                lastReceived = System.currentTimeMillis()
                // A message read as the link was stopped is not taken.
                if (this.session !== session) break
                when (msg) {
                    is LinkMessage.State -> {
                        _snapshot.value = msg.snapshot
                        snapshotFrom = target.address
                    }
                    is LinkMessage.Announce -> _announcements.tryEmit(Announcement(msg.sv, msg.en))
                    is LinkMessage.Remote -> _remotes.tryEmit(msg)
                    is LinkMessage.Hello, LinkMessage.Ping, is LinkMessage.Command, is LinkMessage.Order, is LinkMessage.MapView -> Unit
                }
            }
        } catch (e: Exception) {
            DebugLog.d { "link closed: ${e.javaClass.simpleName}" }
        } finally {
            session = null
            watchdog?.cancel()
            closeSocket()
            if (linked && started) {
                _state.value = State(Status.CONNECTING, target.name)
                // A short break keeps the last trips on the display (its dot turns red); a longer one clears them.
                staleJob?.cancel()
                staleJob = scope.launch {
                    delay(STALE_MS)
                    _snapshot.value = null
                }
            }
        }
        return if (linked) null else "no answer"
    }

    /** Sends a button pressed on this tablet's floating panel to the driver's phone (while connected). */
    fun send(action: LinkMessage.Command.Action) {
        val to = session ?: return
        scope.launch(Dispatchers.IO) { runCatching { to.send(LinkMessage.Command(action)) } }
    }

    /** Sends the trips' order the driver set on this tablet's map to the phone (while connected). */
    fun order(ids: List<Long>) {
        val to = session ?: return
        scope.launch(Dispatchers.IO) { runCatching { to.send(LinkMessage.Order(ids)) } }
    }

    /** Tells the phone what this display's map shows (while connected; sent only when it changed). */
    fun mapView(view: LinkMessage.MapView) {
        val to = session ?: return
        if (view == lastMapView && to === mapViewTo) return
        lastMapView = view
        mapViewTo = to
        scope.launch(Dispatchers.IO) { runCatching { to.send(view) } }
    }

    @Volatile private var lastMapView: LinkMessage.MapView? = null
    @Volatile private var mapViewTo: LinkSession? = null

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    companion object {
        private const val RETRY_MS = 3_000L
        private const val SILENCE_MS = 30_000L

        private const val STOPPED = "stopped"

        /** A phone that has not said it is a controller after this long is let go. */
        private const val HELLO_MS = 10_000L

        /** The last trips stay this long after the link broke, while it is found again. */
        private const val STALE_MS = 2 * 60_000L
    }
}
