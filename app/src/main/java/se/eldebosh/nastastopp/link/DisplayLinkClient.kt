package se.eldebosh.nastastopp.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import se.eldebosh.nastastopp.core.link.LinkMessage
import se.eldebosh.nastastopp.core.link.LinkProtocol
import se.eldebosh.nastastopp.core.link.LinkSession
import se.eldebosh.nastastopp.core.route.Announcement
import se.eldebosh.nastastopp.util.DebugLog

/**
 * Passenger display side: connects to the controller's Bluetooth service and keeps the latest
 * [DisplaySnapshot]. Reconnects automatically. Nothing is stored on this device.
 */
class DisplayLinkClient(private val context: Context, private val scope: CoroutineScope) {

    enum class Status { IDLE, NO_PERMISSION, NO_BLUETOOTH, BLUETOOTH_OFF, CONNECTING, CONNECTED }

    data class State(val status: Status = Status.IDLE, val deviceName: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _snapshot = MutableStateFlow<DisplaySnapshot?>(null)
    val snapshot: StateFlow<DisplaySnapshot?> = _snapshot.asStateFlow()

    private val _announcements = MutableSharedFlow<Announcement>(extraBufferCapacity = 8)
    val announcements: SharedFlow<Announcement> = _announcements.asSharedFlow()

    private var job: Job? = null
    @Volatile private var socket: BluetoothSocket? = null
    private var address: String? = null

    /** Connects (and keeps reconnecting) to the controller with this Bluetooth address. */
    fun start(controllerAddress: String) {
        if (job?.isActive == true && address == controllerAddress) return
        stop()
        address = controllerAddress
        val name = Bluetooth.deviceName(context, controllerAddress)
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                when (Bluetooth.availability(context)) {
                    LinkAvailability.NO_BLUETOOTH -> _state.value = State(Status.NO_BLUETOOTH, name)
                    LinkAvailability.NO_PERMISSION -> _state.value = State(Status.NO_PERMISSION, name)
                    LinkAvailability.BLUETOOTH_OFF -> _state.value = State(Status.BLUETOOTH_OFF, name)
                    LinkAvailability.OK -> {
                        _state.value = State(Status.CONNECTING, name)
                        connectOnce(controllerAddress, name)
                    }
                }
                if (isActive) delay(RETRY_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        closeSocket()
        _state.value = State(Status.IDLE)
    }

    @SuppressLint("MissingPermission") // availability() checked the permission
    private fun connectOnce(address: String, name: String) {
        val s = try {
            Bluetooth.adapter(context)?.getRemoteDevice(address)?.createRfcommSocketToServiceRecord(LinkProtocol.SERVICE_UUID)
        } catch (e: Exception) {
            DebugLog.w(e) { "create socket failed" }
            null
        } ?: return
        socket = s
        var watchdog: Job? = null
        try {
            s.connect() // blocking; fails fast if the controller is not listening
            _state.value = State(Status.CONNECTED, name)
            val session = LinkSession(s.inputStream, s.outputStream)
            session.send(LinkMessage.Hello(LinkProtocol.VERSION, LinkProtocol.ROLE_DISPLAY))
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
                when (msg) {
                    is LinkMessage.State -> _snapshot.value = msg.snapshot
                    is LinkMessage.Announce -> _announcements.tryEmit(Announcement(msg.sv, msg.en))
                    is LinkMessage.Hello, LinkMessage.Ping -> Unit
                }
            }
        } catch (e: Exception) {
            DebugLog.d { "link closed: ${e.javaClass.simpleName}" }
        } finally {
            watchdog?.cancel()
            closeSocket()
            if (_state.value.status == Status.CONNECTED) _state.value = State(Status.CONNECTING, name)
        }
    }

    @Volatile private var lastReceived = 0L

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
    }
}
