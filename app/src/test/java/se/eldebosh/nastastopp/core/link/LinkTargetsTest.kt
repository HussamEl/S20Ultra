package se.eldebosh.nastastopp.core.link

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkTargetsTest {

    private val headset = LinkCandidate("00:00:00:00:00:01", "Buds", DeviceKind.fromMajorClass(0x0400))
    private val car = LinkCandidate("00:00:00:00:00:02", "Volvo", DeviceKind.fromMajorClass(0x0400))
    private val laptop = LinkCandidate("00:00:00:00:00:03", "Laptop", DeviceKind.fromMajorClass(0x0100))
    private val phone = LinkCandidate("00:00:00:00:00:04", "Galaxy S25 Ultra", DeviceKind.fromMajorClass(0x0200))
    private val misc = LinkCandidate("00:00:00:00:00:05", "Unknown", DeviceKind.fromMajorClass(0x1F00))
    private val all = listOf(headset, car, laptop, misc, phone)

    @Test
    fun phonesFirstThenComputersThenOthersAudioSkipped() {
        assertEquals(listOf(phone, laptop, misc), LinkTargets.order(all, preferred = null))
    }

    @Test
    fun preferredDeviceIsTriedFirstEvenIfItsKindIsIgnored() {
        assertEquals(listOf(laptop, phone, misc), LinkTargets.order(all, preferred = laptop.address))
        assertEquals(listOf(car, phone, laptop, misc), LinkTargets.order(all, preferred = car.address))
    }

    @Test
    fun unknownPreferredAddressIsIgnored() {
        assertEquals(listOf(phone, laptop, misc), LinkTargets.order(all, preferred = "AA:BB:CC:DD:EE:FF"))
    }
}
