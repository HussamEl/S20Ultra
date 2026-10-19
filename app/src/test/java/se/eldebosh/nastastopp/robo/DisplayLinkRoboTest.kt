package se.eldebosh.nastastopp.robo

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothServerSocket
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBluetoothServerSocket
import org.robolectric.shadows.ShadowTextToSpeech
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.core.link.LinkMessage
import se.eldebosh.nastastopp.core.link.LinkProtocol
import se.eldebosh.nastastopp.link.DisplayLinkClient
import se.eldebosh.nastastopp.link.DisplayLinkServer
import se.eldebosh.nastastopp.overlay.RoutePanelSource
import se.eldebosh.nastastopp.settings.DeviceRole
import java.util.Locale
import kotlin.concurrent.thread

/** Bluetooth link on Robolectric's Bluetooth shadows (Android 13). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class DisplayLinkRoboTest {

    private lateinit var app: App

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
    }

    /** BluetoothClass has no public constructor; build one through its Parcelable form. */
    private fun btClass(value: Int): BluetoothClass {
        val parcel = android.os.Parcel.obtain()
        parcel.writeInt(value)
        parcel.setDataPosition(0)
        return BluetoothClass.CREATOR.createFromParcel(parcel).also { parcel.recycle() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun waitFor(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            Thread.sleep(25)
            idle()
        }
        check(condition()) { "condition not reached" }
    }

    /** The socket the tablet opened to the phone (the client keeps it private). */
    private fun socketOf(client: DisplayLinkClient): BluetoothSocket? =
        DisplayLinkClient::class.java.getDeclaredField("socket")
            .apply { isAccessible = true }
            .get(client) as BluetoothSocket?

    /**
     * The tablet links on its own to the paired phone, not to the headset, once the phone answers
     * as a Nästa Stopp controller.
     */
    @Test
    fun displayFindsThePairedPhoneWithoutChoosingIt() {
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        shadowOf(adapter).setEnabled(true)
        val headset = adapter.getRemoteDevice("00:11:22:33:44:01").also {
            shadowOf(it).setName("Buds")
            shadowOf(it).setBluetoothClass(btClass(BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET))
        }
        val phone = adapter.getRemoteDevice("00:11:22:33:44:02").also {
            shadowOf(it).setName("Galaxy S25 Ultra")
            shadowOf(it).setBluetoothClass(btClass(BluetoothClass.Device.PHONE_SMART))
        }
        shadowOf(adapter).setBondedDevices(setOf(headset, phone))

        app.graph.settings.update { it.copy(role = DeviceRole.DISPLAY, displayControllerAddress = null) }
        val client = app.graph.displayClient
        client.start(null) // automatic: nothing chosen
        waitFor { socketOf(client) != null }
        val hello = LinkProtocol.encode(LinkMessage.Hello(LinkProtocol.VERSION, LinkProtocol.ROLE_CONTROLLER))
        shadowOf(socketOf(client)).inputStreamFeeder.apply {
            write("$hello\n".toByteArray())
            flush()
        }
        waitFor { client.state.value.status == DisplayLinkClient.Status.CONNECTED }
        assertEquals("Galaxy S25 Ultra", client.state.value.deviceName)
        waitFor { app.graph.settings.current.displayControllerAddress == "00:11:22:33:44:02" }
        client.stop()
    }

    @Test
    fun controllerListensWhenTheLinkIsTurnedOn() {
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        shadowOf(adapter).setEnabled(true)
        app.graph.settings.update { it.copy(role = DeviceRole.CONTROLLER, displayLinkEnabled = true) }
        idle()
        assertEquals(DisplayLinkServer.Status.WAITING, app.graph.displayServer.state.value.status)
        app.graph.settings.update { it.copy(displayLinkEnabled = false) }
        idle()
        assertEquals(DisplayLinkServer.Status.OFF, app.graph.displayServer.state.value.status)
    }

    /** The phone's listening sockets (the server keeps them private). */
    @Suppress("UNCHECKED_CAST")
    private fun serverSocketsOf(server: DisplayLinkServer): Set<BluetoothServerSocket> =
        DisplayLinkServer::class.java.getDeclaredField("serverSockets")
            .apply { isAccessible = true }
            .get(server) as Set<BluetoothServerSocket>

    /**
     * Turning the link off forgets the display and what its map showed: the phone's panel no longer
     * drives a map that is gone, and its clock is said on the phone again.
     */
    @Test
    fun turningTheLinkOffForgetsTheDisplaysMap() {
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        shadowOf(adapter).setEnabled(true)
        val tablet = adapter.getRemoteDevice("00:11:22:33:44:03").also { shadowOf(it).setName("Galaxy Tab S9") }
        shadowOf(adapter).setBondedDevices(setOf(tablet))
        app.graph.settings.update { it.copy(role = DeviceRole.CONTROLLER, displayLinkEnabled = true) }
        idle()
        val server = app.graph.displayServer
        waitFor { serverSocketsOf(server).isNotEmpty() }
        val socket = Shadow.extract<ShadowBluetoothServerSocket>(serverSocketsOf(server).first()).deviceConnected(tablet)
        // What the phone sends is read away, as the tablet would.
        val sink = shadowOf(socket).outputStreamSink
        thread(isDaemon = true) { runCatching { while (sink.read() >= 0) Unit } }
        val feeder = shadowOf(socket).inputStreamFeeder
        fun tabletSays(message: LinkMessage) {
            feeder.write((LinkProtocol.encode(message) + "\n").toByteArray())
            feeder.flush()
        }
        tabletSays(LinkMessage.Hello(LinkProtocol.VERSION, LinkProtocol.ROLE_DISPLAY))
        waitFor { server.state.value.status == DisplayLinkServer.Status.CONNECTED }
        tabletSays(LinkMessage.MapView(hasMap = true, open = true))
        waitFor { server.mapView.value != null }

        app.graph.settings.update { it.copy(displayLinkEnabled = false) }
        idle()
        // A real socket ends its streams when closed; Robolectric's keeps them open.
        feeder.close()
        waitFor { server.mapView.value == null }
        assertEquals(DisplayLinkServer.Status.OFF, server.state.value.status)

        ShadowTextToSpeech.addLanguageAvailability(Locale.forLanguageTag("sv-SE"))
        app.graph.announcer.recheck()
        val tts = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        tts.onInitListener.onInit(TextToSpeech.SUCCESS)
        idle()
        val panel = RoutePanelSource(app.graph.controller, app.graph.street, app.graph.announcer, server, app.graph.scope)
        assertNull(panel.mapView.value)
        panel.sayTime()
        idle()
        assertTrue(tts.lastAnnouncement.orEmpty(), tts.lastAnnouncement.orEmpty().startsWith("Klockan är "))
    }
}
