package se.eldebosh.nastastopp.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import se.eldebosh.nastastopp.core.display.DisplayItem
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread

class LinkProtocolTest {

    private val snapshot = DisplaySnapshot(
        active = true,
        previous = DisplayItem("12:30", "Skoghall"),
        current = DisplayItem("12:48", "Herrhagen", "Storgatan 14, 652 24 Karlstad"),
        upcoming = listOf(DisplayItem("13:05", "Kil"), DisplayItem(null, "Grums")),
        remaining = 3,
        completed = 1,
        announcementSv = "Nästa stopp: Herrhagen. Därefter: Kil.",
    )

    @Test
    fun roundTripEveryMessageType() {
        listOf(
            LinkMessage.Hello(LinkProtocol.VERSION, LinkProtocol.ROLE_CONTROLLER),
            LinkMessage.State(snapshot),
            LinkMessage.Announce("Rutten är klar.", "The route is finished."),
            LinkMessage.Ping,
            LinkMessage.Command(LinkMessage.Command.Action.NEXT),
            LinkMessage.Command(LinkMessage.Command.Action.BACK),
            LinkMessage.Command(LinkMessage.Command.Action.REPEAT),
            LinkMessage.Order(listOf(12L, 9L, 14L)),
        ).forEach { msg ->
            val line = LinkProtocol.encode(msg)
            assertTrue(line, !line.contains('\n'))
            assertEquals(msg, LinkProtocol.decode(line))
        }
    }

    /** A tablet's floating panel sends back only the button pressed: nothing else goes to the phone. */
    @Test
    fun aCommandIsOnlyItsButton() {
        assertEquals("{\"type\":\"command\",\"action\":\"NEXT\"}", LinkProtocol.encode(LinkMessage.Command(LinkMessage.Command.Action.NEXT)))
        assertNull(LinkProtocol.decode("{\"type\":\"command\",\"action\":\"END\"}"))
    }

    /** The order set on the tablet's map is only the trips' numbers. */
    @Test
    fun anOrderIsOnlyTheTripsNumbers() {
        assertEquals("{\"type\":\"order\",\"ids\":[12,9]}", LinkProtocol.encode(LinkMessage.Order(listOf(12L, 9L))))
    }

    @Test
    fun garbageAndUnknownTypesAreIgnored() {
        assertNull(LinkProtocol.decode("not json"))
        assertNull(LinkProtocol.decode("{\"type\":\"launch-missiles\"}"))
        val input = ByteArrayInputStream(
            ("garbage\n\n{\"type\":\"unknown\"}\n" + LinkProtocol.encode(LinkMessage.Ping) + "\n").toByteArray(),
        )
        val session = LinkSession(input, ByteArrayOutputStream())
        assertEquals(LinkMessage.Ping, session.receive())
        assertNull(session.receive())
    }

    @Test(expected = IOException::class)
    fun overlongLineIsRejected() {
        val input = ByteArrayInputStream("x".repeat(LinkProtocol.MAX_LINE_CHARS + 10).toByteArray())
        LinkSession(input, ByteArrayOutputStream()).receive()
    }

    @Test
    fun controllerToDisplayOverAPipe() {
        val toDisplay = PipedInputStream()
        val controllerOut = PipedOutputStream(toDisplay)
        val controller = LinkSession(PipedInputStream(), controllerOut)
        val display = LinkSession(toDisplay, ByteArrayOutputStream())
        val received = mutableListOf<LinkMessage>()
        val reader = thread {
            while (true) received += display.receive() ?: break
        }
        controller.send(LinkMessage.Hello(LinkProtocol.VERSION, LinkProtocol.ROLE_CONTROLLER))
        controller.send(LinkMessage.State(snapshot))
        controller.send(LinkMessage.Announce("Nästa stopp: Herrhagen. Därefter: Kil."))
        controllerOut.close()
        reader.join(5_000)
        assertEquals(3, received.size)
        assertEquals(snapshot, (received[1] as LinkMessage.State).snapshot)
        assertEquals("Nästa stopp: Herrhagen. Därefter: Kil.", (received[2] as LinkMessage.Announce).sv)
    }

    /** The phone sends its trips only to a passenger display of this protocol that says so first. */
    @Test
    fun onlyADisplaysHelloOpensTheLink() {
        assertTrue(LinkProtocol.isDisplayHello(LinkMessage.Hello(LinkProtocol.VERSION, LinkProtocol.ROLE_DISPLAY)))
        assertFalse(LinkProtocol.isDisplayHello(LinkMessage.Hello(LinkProtocol.VERSION, LinkProtocol.ROLE_CONTROLLER)))
        assertFalse(LinkProtocol.isDisplayHello(LinkMessage.Hello(LinkProtocol.VERSION + 1, LinkProtocol.ROLE_DISPLAY)))
        assertFalse(LinkProtocol.isDisplayHello(LinkMessage.Ping))
        assertFalse(LinkProtocol.isDisplayHello(null))
    }
}
