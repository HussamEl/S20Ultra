package se.eldebosh.nastastopp.core.link

/** What kind of paired Bluetooth device this is (from its Bluetooth class). */
enum class DeviceKind {
    PHONE,
    COMPUTER,
    OTHER,

    /** Headsets, car kits, watches, printers …: never a driver's device, not tried automatically. */
    IGNORED,
    ;

    companion object {
        /** Maps a Bluetooth "major device class" (android.bluetooth.BluetoothClass.Device.Major). */
        fun fromMajorClass(major: Int): DeviceKind = when (major) {
            0x0200 -> PHONE
            0x0100 -> COMPUTER // tablets often report themselves as computers
            0x0000, 0x1F00 -> OTHER // misc / uncategorized
            else -> IGNORED // audio/video, peripheral, imaging, wearable, toy, health, networking
        }
    }
}

data class LinkCandidate(val address: String, val name: String, val kind: DeviceKind)

/**
 * The order in which a passenger display tries paired devices to find the driver's device, so
 * that it connects without the user having to pick one: the chosen/last-used device first, then
 * phones, then computers/tablets, then uncategorised devices. Audio devices etc. are skipped
 * unless explicitly chosen.
 */
object LinkTargets {
    fun order(devices: List<LinkCandidate>, preferred: String?): List<LinkCandidate> {
        val chosen = devices.filter { it.address == preferred }
        val rest = devices
            .filter { it.address != preferred && it.kind != DeviceKind.IGNORED }
            .sortedWith(compareBy<LinkCandidate> { it.kind.ordinal }.thenBy { it.name.lowercase() })
        return chosen + rest
    }
}
