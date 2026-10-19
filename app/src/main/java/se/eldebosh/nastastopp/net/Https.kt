package se.eldebosh.nastastopp.net

import se.eldebosh.nastastopp.core.nav.CompanyServer
import se.eldebosh.nastastopp.core.nav.HttpAsk
import java.net.HttpURLConnection
import java.net.URI

/** Sends an [HttpAsk] and gives its [Reply] (tests use a stand-in that records what would be sent). */
fun interface WayTransport {
    fun send(ask: HttpAsk): Reply
}

/**
 * An answer: its HTTP [code] (0: none came), its [text] when it is 200, and, from the company's
 * server, its own error word ([slug], [CompanyServer.ERROR_HEADER]) and Retry-After
 * ([retryAfterSec]). An ask to the server that got no answer at all has code 0 and the slug
 * [CompanyServer.NO_ANSWER] ([se.eldebosh.nastastopp.core.nav.ServerTrouble.Down]), so it backs off
 * as when the server says Google or mapmap did not answer. Its text form shows only the code.
 */
class Reply(val code: Int, val text: String?, val slug: String? = null, val retryAfterSec: Long? = null) {
    override fun toString() = "Reply($code${slug?.let { ", $it" }.orEmpty()})"
}

/**
 * The one way the tablet's map and its connection to the company's server reach the internet:
 * HttpURLConnection with the system's certificate checks (res/xml/network_security_config.xml).
 * An ask that carries the tablet's pass ([HttpAsk.viaServer]) goes only to an address that passes
 * [CompanyServer.mayCarryToken] (else nothing is sent) and follows no redirect. Nothing is
 * logged: no address, header, body or code.
 */
object Https : WayTransport {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val OK = HttpURLConnection.HTTP_OK
    private val SLUG = Regex("[a-z][a-z-]{0,31}")

    override fun send(ask: HttpAsk): Reply {
        if (ask.viaServer && !CompanyServer.mayCarryToken(ask.url)) return Reply(0, null)
        return runCatching {
            val conn = URI(ask.url).toURL().openConnection() as HttpURLConnection
            try {
                conn.requestMethod = if (ask.body != null) "POST" else "GET"
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = ask.readTimeoutMs
                conn.useCaches = false
                if (ask.viaServer) conn.instanceFollowRedirects = false
                ask.headers.forEach { (name, value) -> conn.setRequestProperty(name, value) }
                if (ask.body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(ask.body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                when {
                    code == OK -> Reply(code, conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                    ask.viaServer -> serverError(code, conn.getHeaderField(CompanyServer.ERROR_HEADER), conn.getHeaderField("Retry-After"))
                    else -> Reply(code, null)
                }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { Reply(0, null, slug = if (ask.viaServer) CompanyServer.NO_ANSWER else null) }
    }

    /** A refusal from the company's server: its own word ([slug]) and Retry-After, each kept only when well formed. */
    internal fun serverError(code: Int, slug: String?, retryAfter: String?): Reply = Reply(
        code,
        null,
        slug = slug?.trim()?.takeIf { SLUG.matches(it) },
        retryAfterSec = retryAfter?.trim()?.toLongOrNull()?.takeIf { it >= 0 },
    )
}
