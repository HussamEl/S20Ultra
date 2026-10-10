package se.eldebosh.nastastopp.net

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import se.eldebosh.nastastopp.core.nav.CompanyServer
import se.eldebosh.nastastopp.core.nav.HttpAsk
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/** The one HTTP transport, against a server on this machine: what it sends, what it reads, and where the pass may go. */
class HttpsTest {

    private class Seen(val method: String, val path: String, val headers: Map<String, String>, val body: String)

    private lateinit var server: HttpServer
    private val seen = mutableListOf<Seen>()
    private var answer: Triple<Int, String, Map<String, String>> = Triple(200, """{"ok":true}""", emptyMap())
    private val tokenHeader = "Bearer " + "ab12".repeat(16)

    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            synchronized(seen) {
                seen += Seen(
                    exchange.requestMethod,
                    exchange.requestURI.path,
                    exchange.requestHeaders.mapValues { it.value.joinToString() }.mapKeys { it.key.lowercase() },
                    exchange.requestBody.readBytes().toString(Charsets.UTF_8),
                )
            }
            val (code, text, headers) = answer
            headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            val bytes = text.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) } else exchange.close()
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun aPostCarriesItsHeadersAndBodyAndReadsTheAnswer() {
        val reply = Https.send(HttpAsk("$base/route", mapOf("X-Goog-FieldMask" to "routes.duration"), """{"a":"Å"}""", 5_000, viaServer = false))
        assertEquals(200, reply.code)
        assertEquals("""{"ok":true}""", reply.text)
        val request = seen.single()
        assertEquals("POST", request.method)
        assertEquals("/route", request.path)
        assertEquals("routes.duration", request.headers["x-goog-fieldmask"])
        assertEquals("application/json", request.headers["content-type"])
        assertEquals("""{"a":"Å"}""", request.body)
    }

    @Test
    fun withoutABodyItIsAGet() {
        Https.send(HttpAsk("$base/route/v1/driving/1,2;3,4", emptyMap(), null, 5_000, viaServer = false))
        assertEquals("GET", seen.single().method)
        assertEquals("", seen.single().body)
    }

    @Test
    fun aRefusalGivesItsCodeOnly() {
        answer = Triple(403, """{"error":"denied"}""", mapOf(CompanyServer.ERROR_HEADER to "stopped", "Retry-After" to "60"))
        val reply = Https.send(HttpAsk("$base/route", emptyMap(), "{}", 5_000, viaServer = false))
        assertEquals(403, reply.code)
        assertNull(reply.text)
        // Only the company's server's own words are read, and only on its own asks.
        assertNull(reply.slug)
        assertNull(reply.retryAfterSec)
    }

    @Test
    fun thePassGoesNowhereButTheCompanyServer() {
        for (url in listOf("$base/v1/config", "https://127.0.0.1:${server.address.port}/v1/config", "https://api.nastastopp.se.evil.com/v1/config")) {
            val reply = Https.send(HttpAsk(url, mapOf("Authorization" to tokenHeader), null, 5_000, viaServer = true))
            assertEquals(0, reply.code)
            assertNull(reply.slug)
        }
        // Nothing was sent.
        assertEquals(0, seen.size)
    }

    @Test
    fun noAnswerIsCodeZero() {
        val closed = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
        val reply = Https.send(HttpAsk("http://127.0.0.1:$closed/route", emptyMap(), null, 2_000, viaServer = false))
        assertEquals(0, reply.code)
        assertNull(reply.text)
        assertNull(reply.slug)
    }

    @Test
    fun theServersOwnWordIsKeptOnlyWhenWellFormed() {
        val limit = Https.serverError(429, " daily-limit ", "3600")
        assertEquals(429, limit.code)
        assertEquals("daily-limit", limit.slug)
        assertEquals(3_600L, limit.retryAfterSec)
        assertNull(limit.text)
        assertNull(Https.serverError(403, "<script>", null).slug)
        assertNull(Https.serverError(403, "Stopped", null).slug)
        assertNull(Https.serverError(403, "x".repeat(33), null).slug)
        assertNull(Https.serverError(500, null, "Wed, 21 Oct 2026 07:28:00 GMT").retryAfterSec)
        assertNull(Https.serverError(500, null, "-5").retryAfterSec)
        assertEquals("Reply(429, daily-limit)", limit.toString())
    }
}
