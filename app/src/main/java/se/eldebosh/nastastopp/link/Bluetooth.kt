package se.eldebosh.nastastopp.link

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

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

    /** Devices already paired in the system Bluetooth settings (no scanning needed). */
    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun pairedDevices(context: Context): List<PairedDevice> {
        if (!hasPermission(context)) return emptyList()
        val adapter = adapter(context) ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                .map { PairedDevice(it.name?.takeIf { n -> n.isNotBlank() } ?: it.address, it.address) }
                .sortedBy { it.name.lowercase() }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun deviceName(context: Context, address: String): String {
        if (!hasPermission(context)) return address
        return try {
            adapter(context)?.getRemoteDevice(address)?.name?.takeIf { it.isNotBlank() } ?: address
        } catch (_: Exception) {
            address
        }
    }
}
