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
 * per line. Only what the display shows is ever sent (see [DisplaySnapshot]).
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
}

object LinkProtocol {
    const val VERSION = 1
    const val ROLE_CONTROLLER = "controller"
    const val ROLE_DISPLAY = "display"

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
