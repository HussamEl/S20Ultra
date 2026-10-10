package se.eldebosh.nastastopp.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.util.UUID

/**
 * Messages between the driver's device (controller) and a passenger display, one JSON object
 * per line: the display's state ([DisplaySnapshot]), the announcements and the controls the driver
 * used on the phone's floating panel for the display ([Remote]); the display sends back only the
 * buttons pressed on its floating panel ([Command]), the trips' order the driver set on its map
 * ([Order]: their numbers) and what its map shows ([MapView]: trip numbers, minutes and switches,
 * never a name or a position). The vehicle's position is never sent: a tablet's map takes it from
 * the tablet itself. A message of a kind a device does not know is skipped.
 */
@Serializable
sealed interface LinkMessage {
    @Serializable
    @SerialName("hello")
    data class Hello(val version: Int, val role: String) : LinkMessage

    /** Full display state; sent on connect and on every change. */
    @Serializable
    @SerialName("state")
    data class State(val snapshot: DisplaySnapshot) : LinkMessage

    /** The controller just spoke this announcement. */
    @Serializable
    @SerialName("announce")
    data class Announce(val sv: String, val en: String? = null) : LinkMessage

    /** Keep-alive so a dead connection is noticed. */
    @Serializable
    @SerialName("ping")
    data object Ping : LinkMessage

    /** A button the driver pressed on the tablet's floating panel, for the phone to carry out. */
    @Serializable
    @SerialName("command")
    data class Command(val action: Action) : LinkMessage {
        enum class Action { NEXT, BACK, REPEAT }
    }

    /** The order the driver set on the tablet's map for these trips (their [DisplaySnapshot] item ids), for the phone to take. */
    @Serializable
    @SerialName("order")
    data class Order(val ids: List<Long>) : LinkMessage

    /**
     * A control the driver used on the phone's floating panel, for the passenger display to carry
     * out as if tapped there: [id] is a trip's number ([DisplaySnapshot] item id; null: the next
     * stop), [ids] an order to try on the map, [on] a switch.
     */
    @Serializable
    @SerialName("remote")
    data class Remote(val action: Action, val id: Long? = null, val ids: List<Long>? = null, val on: Boolean? = null) : LinkMessage {
        enum class Action {
            /** Shows trip [id] at the top of the display (null: back to the next stop). */
            SHOW_TRIP,

            /** Opens the display's map for trip [id] (null: the next stop), as a long press there. */
            OPEN_MAP,
            CLOSE_MAP,

            /** Google's satellite picture ([on]) or the map. */
            SATELLITE,
            TO_CAR,
            TO_STOP,
            WHOLE,

            /** Looks at trip [id] of the way (picks it in the list). */
            LOOK_AT,

            /** Tries the order [ids] on the map (the same trips as its way). */
            TRY_ORDER,
            UNDO,

            /** Sends the order tried to the phone, as "Use" on the display's list. */
            APPLY,
            ADD,
            ADD_EARLIER,

            /** Takes trip [id] off the way on the map only. */
            REMOVE,
            SUGGEST,

            /** Says the time on the display and shows it large, as a tap on its clock. */
            SAY_TIME,
            EARTH,
            STREET_PHOTOS,
        }
    }

    /**
     * What the passenger display's map shows, for the phone's floating panel: whether it [open]s
     * (and whether the display has a map at all, [hasMap]); the way's trips in their order ([ids],
     * the one looked at [at]) with each leg's minutes ([minutes], null until known); whether the
     * order is one tried ([changed]) and can be used ([allowed]); whether a trip can be added after
     * ([canAdd]) or before ([canAddEarlier]) the way; Google's satellite picture; Google being asked
     * for the best order ([suggesting]) and whether the tablet knows where it is ([located]).
     */
    @Serializable
    @SerialName("map")
    data class MapView(
        val hasMap: Boolean = false,
        val open: Boolean = false,
        val ids: List<Long> = emptyList(),
        val at: Int = 0,
        val minutes: List<Int?> = emptyList(),
        val changed: Boolean = false,
        val allowed: Boolean = true,
        val canAdd: Boolean = false,
        val canAddEarlier: Boolean = false,
        val satellite: Boolean = false,
        val suggesting: Boolean = false,
        val located: Boolean = false,
    ) : LinkMessage
}

object LinkProtocol {
    const val VERSION = 1
    const val ROLE_CONTROLLER = "controller"
    const val ROLE_DISPLAY = "display"

    /** A passenger display of this protocol says so first: the only link the phone sends its trips to. */
    fun isDisplayHello(message: LinkMessage?): Boolean =
        message is LinkMessage.Hello && message.version == VERSION && message.role == ROLE_DISPLAY

    /** The phone answers that it is a controller of this protocol: only then does the tablet count the link as made. */
    fun isControllerHello(message: LinkMessage?): Boolean =
        message is LinkMessage.Hello && message.version == VERSION && message.role == ROLE_CONTROLLER

    /** Bluetooth RFCOMM service of this app (random, fixed): secure (authenticated) channel. */
    val SERVICE_UUID: UUID = UUID.fromString("7d3f2a91-5c4e-4b8a-9e61-2f0c8d4b6a15")

    /**
     * Fallback channel without link-key authentication (still encrypted on Bluetooth 2.1+). Some
     * devices fail the secure RFCOMM handshake; the server only accepts paired devices here too.
     */
    val SERVICE_UUID_INSECURE: UUID = UUID.fromString("7d3f2a91-5c4e-4b8a-9e61-2f0c8d4b6a16")
    const val SERVICE_NAME = "NastaStopp display"

    /** A line longer than this is treated as a broken connection (protects memory). */
    const val MAX_LINE_CHARS = 64 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        classDiscriminator = "type"
    }

    fun encode(message: LinkMessage): String = json.encodeToString(LinkMessage.serializer(), message)

    /** Null for anything that is not a valid message (unknown types are ignored, not fatal). */
    fun decode(line: String): LinkMessage? =
        try {
            json.decodeFromString(LinkMessage.serializer(), line)
        } catch (_: Exception) {
            null
        }
}

/** Line-based message stream over any byte streams (a Bluetooth socket in the app, pipes in tests). */
class LinkSession(input: InputStream, output: OutputStream) : Closeable {
    private val reader: Reader = input.reader(Charsets.UTF_8).buffered()
    private val writer: Writer = output.writer(Charsets.UTF_8).buffered()

    @Synchronized
    fun send(message: LinkMessage) {
        writer.write(LinkProtocol.encode(message))
        writer.write("\n")
        writer.flush()
    }

    /** Blocks until the next valid message; null when the stream ends. */
    fun receive(): LinkMessage? {
        while (true) {
            val line = readLine() ?: return null
            if (line.isBlank()) continue
            LinkProtocol.decode(line)?.let { return it }
        }
    }

    private fun readLine(): String? {
        val sb = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString()
            sb.append(c.toChar())
            if (sb.length > LinkProtocol.MAX_LINE_CHARS) throw IOException("line too long")
        }
    }

    override fun close() {
        try {
            reader.close()
        } catch (_: IOException) {
        }
        try {
            writer.close()
        } catch (_: IOException) {
        }
    }
}
