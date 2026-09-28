package se.eldebosh.nastastopp.robo

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.link.DisplayLinkClient
import se.eldebosh.nastastopp.link.DisplayLinkServer
import se.eldebosh.nastastopp.settings.DeviceRole

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
}
