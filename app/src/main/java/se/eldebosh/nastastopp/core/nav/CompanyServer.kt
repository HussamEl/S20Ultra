package se.eldebosh.nastastopp.core.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.util.Locale

/**
 * The company's server, api.nastastopp.se (`HussamEl/nastastopp-web`): a tablet connects to it once
 * with a code the owner makes on the admin page, and from then on gets its map key, ways and
 * travel times from it; the server asks Google or mapmap.ai with the company's own keys. This is
 * its contract, as the app sees it: the addresses, what is sent, what comes back and how the
 * server's own refusals read. The tablet's pass ([DeviceToken]) goes only in the Authorization
 * header, and only to this server ([mayCarryToken]); never in an address, a body or a log.
 */
object CompanyServer {
    const val HOST = "api.nastastopp.se"
    const val BASE = "https://$HOST"

    const val ENROLL = "$BASE/v1/enroll"
    const val CONFIG = "$BASE/v1/config"
    const val LEAVE = "$BASE/v1/leave"

    /** Google's Routes API through the server (POST, the same bodies and field masks as [RoutesApi]). */
    const val ROUTES = "$BASE/v1/google/routes"
    const val MATRIX = "$BASE/v1/google/matrix"

    /** mapmap.ai through the server: the route is a POST of its points ([MapmapApi.serverRouteBody]), so no point is in an address. */
    const val MAPMAP_ROUTE = "$BASE/v1/mapmap/route"
    const val MAPMAP_MATRIX = "$BASE/v1/mapmap/matrix"

    /** The role the tablet enrols as: the server's own word, not the app's DeviceRole. */
    const val ROLE = "tablet"

    /**
     * The address the tablet's map page loads under with the company's map key: a name the company
     * owns that serves nothing, so the owner can restrict the key to this name and every page under it.
     */
    const val PAGE_BASE = "https://map.nastastopp.se/"

    /** The header that marks the server's own errors; an answer without it is Google's or mapmap's, passed on. */
    const val ERROR_HEADER = "X-Nasta-Error"
    const val USER_AGENT = "NastaStopp"

    /**
     * The server's own word for "no answer": from Google or mapmap behind it (502,
     * [ServerTrouble.UpstreamDown]), or, given by the app itself with no code, from the server
     * ([ServerTrouble.Down]).
     */
    const val NO_ANSWER = "no-answer"

    const val CODE_LENGTH = 8

    /** The letters a code is made of (the server's Store.php): no O, 0, I, 1 or L, which look alike. */
    private const val CODE_LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val LOOKALIKES = "O0I1L"
    private const val LABEL_CHARS = 40
    private const val DAY_MS = 24 * 60 * 60_000L

    /** The code as typed, made whole: upper case, without spaces or "-"; null when it cannot be one. */
    fun code(typed: String): String? {
        val code = typed.uppercase(Locale.ROOT).filter { !it.isWhitespace() && it != '-' }
        return code.takeIf { it.length == CODE_LENGTH && it.all { c -> c in CODE_LETTERS } }
    }

    /** Whether [typed] has a letter or digit that is never in a code (O, 0, I, 1, L), for a hint. */
    fun hasLookalikes(typed: String): Boolean = typed.uppercase(Locale.ROOT).any { it in LOOKALIKES }

    /** What a new tablet sends to connect: its [code] (already made whole by [code]) and its role. */
    fun enrollBody(code: String): String = buildJsonObject {
        put("code", code)
        put("role", ROLE)
    }.toString()

    /** A tablet newly connected: its pass, and the name the owner gave it on the admin page ("tablet-40274"). */
    class Enrolled(val token: DeviceToken, val label: String) {
        override fun toString() = "Enrolled(***, $label)"
    }

    /** The answer to [ENROLL], or null when it is not one (no pass, or not a pass as the server makes them). */
    fun parseEnroll(text: String): Enrolled? {
        val answer = objectOf(text) ?: return null
        val token = answer.string("token")?.takeIf { DeviceToken.isToken(it) } ?: return null
        return Enrolled(DeviceToken(token), label(answer.string("label")).orEmpty())
    }

    /**
     * What the server gives the tablet ([CONFIG]): the company's map key, kept only when it is a key
     * as Google issues them (it is put into the map page), and which ways the server can ask.
     */
    data class Config(val mapsJsKey: String?, val google: Boolean, val mapmap: Boolean, val label: String?) {
        override fun toString() = "Config(key=${if (mapsJsKey == null) "none" else "***"}, google=$google, mapmap=$mapmap)"
    }

    /** The answer to [CONFIG], or null when it is not one. */
    fun parseConfig(text: String): Config? {
        val answer = objectOf(text) ?: return null
        val ways = answer["ways"] as? JsonObject
        fun way(name: String) = (ways?.get(name) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true
        return Config(
            mapsJsKey = answer.string("mapsJsKey")?.takeIf { RoutesApi.isKey(it) },
            google = way("google"),
            mapmap = way("mapmap"),
            label = label(answer.string("label")),
        )
    }

    /**
     * Whether the pass may go to [url]: https, exactly [HOST] on its own port, and no user part
     * (so "api.nastastopp.se.evil.com" and "api.nastastopp.se@evil.com" never get it).
     */
    fun mayCarryToken(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host == HOST && uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443)
    }

    /** The headers of an ask to the server: the pass, the field mask Google is asked with ([fields], when given), and the app's name. */
    fun headers(token: DeviceToken, fields: String? = null): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${token.value}")
        if (fields != null) put("X-Goog-FieldMask", fields)
        put("User-Agent", USER_AGENT)
    }

    /**
     * The server's own refusal in an answer: its HTTP [code], the [slug] in [ERROR_HEADER] and its
     * Retry-After ([retryAfterSec]), at [wallMs]. Null without a slug: the code is then Google's or
     * mapmap's, passed on by the server, and read as when they are asked directly.
     */
    fun classify(code: Int, slug: String?, retryAfterSec: Long?, wallMs: Long): ServerTrouble? =
        when (slug?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }) {
            null -> null
            "not-enrolled" -> ServerTrouble.NotEnrolled
            "stopped" -> ServerTrouble.Stopped
            "role" -> ServerTrouble.Role
            "daily-limit" -> ServerTrouble.DailyLimit(
                retryAfterSec?.takeIf { it > 0 }?.let { wallMs + minOf(it, DAY_MS / 1000) * 1000 } ?: nextUtcMidnight(wallMs),
            )
            "no-key" -> ServerTrouble.NoKey
            // The server's 502: it answered, Google or mapmap behind it did not. No code: the server itself did not.
            NO_ANSWER -> if (code == 0) ServerTrouble.Down else ServerTrouble.UpstreamDown
            "server" -> ServerTrouble.Down
            // "field-mask", "bad-request", "too-large", "unknown", and any word the app does not know.
            else -> ServerTrouble.Bug(code)
        }

    /** The next midnight in UTC after [wallMs], when the server's daily counts begin again. */
    fun nextUtcMidnight(wallMs: Long): Long = (wallMs / DAY_MS + 1) * DAY_MS

    /** A name to show: printable characters only, trimmed, at most [LABEL_CHARS]; null when none is left. */
    private fun label(text: String?): String? =
        text?.filter { c -> !c.isISOControl() && Character.getType(c) !in HIDDEN }?.trim()?.take(LABEL_CHARS)?.trim()?.takeIf { it.isNotEmpty() }

    private fun objectOf(text: String): JsonObject? = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    private fun JsonObject.string(name: String): String? = (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private val HIDDEN = setOf(
        Character.FORMAT.toInt(),
        Character.LINE_SEPARATOR.toInt(),
        Character.PARAGRAPH_SEPARATOR.toInt(),
        Character.SURROGATE.toInt(),
        Character.PRIVATE_USE.toInt(),
        Character.UNASSIGNED.toInt(),
    )

    private val json = Json { ignoreUnknownKeys = true }
}

/** The tablet's pass to the company's server: 64 lower-case hex digits. Its text form never shows it. */
class DeviceToken(val value: String) {
    init {
        require(isToken(value)) { "not a device token" }
    }

    override fun equals(other: Any?) = other is DeviceToken && other.value == value

    override fun hashCode() = value.hashCode()

    override fun toString() = "DeviceToken(***)"

    companion object {
        private val TOKEN = Regex("[0-9a-f]{64}")

        /** Whether [text] is a pass as the server makes them. */
        fun isToken(text: String): Boolean = TOKEN.matches(text)
    }
}

/** The company server's own refusals, marked by [CompanyServer.ERROR_HEADER] ([CompanyServer.classify]). */
sealed interface ServerTrouble {
    /** The server does not know this tablet's pass (401): a new code is needed. */
    data object NotEnrolled : ServerTrouble

    /** The owner stopped this tablet on the admin page (403): his Start brings it back. */
    data object Stopped : ServerTrouble

    /** The pass is not a tablet's (403). */
    data object Role : ServerTrouble

    /** This service's limit for today is reached (429): asked again at [untilWallMs] (wall clock). */
    data class DailyLimit(val untilWallMs: Long) : ServerTrouble

    /** The server has no key for this service (503). */
    data object NoKey : ServerTrouble

    /** The server did not answer, or failed itself (500). */
    data object Down : ServerTrouble

    /** The server answered, but Google or mapmap behind it did not (502): the connection is fine. */
    data object UpstreamDown : ServerTrouble

    /** The server refused the request itself ([code]): the app asked wrongly, and asking again would be refused again. */
    data class Bug(val code: Int) : ServerTrouble
}
