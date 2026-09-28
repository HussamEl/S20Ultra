package se.eldebosh.nastastopp.link

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import se.eldebosh.nastastopp.core.link.DeviceKind
import se.eldebosh.nastastopp.core.link.LinkCandidate

/** A paired device that can be chosen as the controller. */
data class PairedDevice(val name: String, val address: String)

/** Link availability shared by the server (controller) and the client (display). */
enum class LinkAvailability { OK, NO_PERMISSION, NO_BLUETOOTH, BLUETOOTH_OFF }

object Bluetooth {

    /** BLUETOOTH_CONNECT is a runtime permission from Android 12; before that it is install-time. */
    val permission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null

    fun hasPermission(context: Context): Boolean =
        permission == null || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun availability(context: Context): LinkAvailability {
        val adapter = adapter(context) ?: return LinkAvailability.NO_BLUETOOTH
        if (!hasPermission(context)) return LinkAvailability.NO_PERMISSION
        return if (adapter.isEnabled) LinkAvailability.OK else LinkAvailability.BLUETOOTH_OFF
    }

    @SuppressLint("MissingPermission") // checked by hasPermission()
    private fun bonded(context: Context): List<BluetoothDevice> {
        if (!hasPermission(context)) return emptyList()
        val adapter = adapter(context) ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty().toList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.displayName(): String =
        try {
            name?.takeIf { it.isNotBlank() } ?: address
        } catch (_: SecurityException) {
            address
        }

    /** Devices already paired in the system Bluetooth settings (no scanning needed). */
    fun pairedDevices(context: Context): List<PairedDevice> =
        bonded(context).map { PairedDevice(it.displayName(), it.address) }.sortedBy { it.name.lowercase() }

    /** Paired devices with their kind, for automatic search of the driver's device. */
    fun linkCandidates(context: Context): List<LinkCandidate> = bonded(context).map { d ->
        val major = try {
            d.bluetoothClass?.majorDeviceClass ?: 0x1F00
        } catch (_: SecurityException) {
            0x1F00
        }
        LinkCandidate(d.address, d.displayName(), DeviceKind.fromMajorClass(major))
    }

    fun isBonded(context: Context, address: String): Boolean = bonded(context).any { it.address == address }

    @SuppressLint("MissingPermission")
    fun deviceName(context: Context, address: String): String {
        if (!hasPermission(context)) return address
        return try {
            adapter(context)?.getRemoteDevice(address)?.displayName() ?: address
        } catch (_: Exception) {
            address
        }
    }

    /** This device's Bluetooth name (what the other device shows in its paired list). */
    @SuppressLint("MissingPermission")
    fun localName(context: Context): String? {
        if (!hasPermission(context)) return null
        return try {
            adapter(context)?.name?.takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            null
        }
    }
}
