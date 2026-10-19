package se.eldebosh.nastastopp.core.nav

import java.util.EnumMap

/**
 * When each service ([WayService]) of the tablet's map may be asked again, from the answers so far;
 * in memory only. Each ask costs (Google, mapmap, and the tablet's daily limit on the company's
 * server), so an answer that says "not now" or "never" pauses the asks:
 * - Google's or mapmap's own answer (asked directly, or passed on by the server, [ServerTrouble]
 *   null): the ways wait [RETRY_MS]; a refusal (a 4xx other than [TOO_MANY]) is also not asked
 *   again for the same stops ([Outcome.REFUSE_STOPS]).
 * - The server's refusals ([held]): a tablet stopped or unknown to it asks nothing until [clear]; a
 *   daily limit pauses only its own service until the time given (the next UTC midnight); a
 *   service the server has no key for waits [NO_KEY_MS]; no answer (from the server, or from Google
 *   or mapmap behind it) backs off the ways from [RETRY_MS] to [MOST_WAIT_MS].
 *
 * The waits after a failure are the ways' (asked by themselves as the car moves). The travel times
 * ([WayService.matrix]) are asked only on the driver's tap: they never wait for the ways, and their
 * answers never make the ways wait; only a refusal of the server's that still holds stops them.
 *
 * Times: nowMs is the monotonic clock (elapsedRealtime), wallMs the wall clock (the daily limit).
 */
class WayGate {

    /** What the answer means for the next asks. */
    enum class Outcome {
        /** The answer came. */
        OK,

        /** Refused for good for these stops: not asked again for them. */
        REFUSE_STOPS,

        /** Asked again later. */
        WAITING,

        /** Nothing is asked until [clear]. */
        STOPPED,
    }

    /** The server's word that this tablet asks nothing ([ServerTrouble.NotEnrolled], [ServerTrouble.Stopped] or [ServerTrouble.Role]), until [clear]. */
    private var stopped: ServerTrouble? = null
    private val limitUntil = EnumMap<WayService, Long>(WayService::class.java)
    private val noKeyUntil = EnumMap<WayService, Long>(WayService::class.java)

    /** The ways' wait after a failure (monotonic), and the failures in a row with no answer, for the backoff. */
    private var waitUntil = 0L
    private var failures = 0

    /** Whether [service] may be asked now: no refusal of the server's holds ([held]), and a way's wait has passed. */
    @Synchronized
    fun mayAsk(service: WayService, nowMs: Long, wallMs: Long): Boolean =
        held(service, wallMs) == null && (service.matrix || nowMs >= waitUntil)

    /**
     * The server's refusal that still stops [service], or null: the tablet stopped or unknown, the
     * service's daily limit ([ServerTrouble.DailyLimit] with its end), or no key there.
     */
    @Synchronized
    fun held(service: WayService, wallMs: Long): ServerTrouble? {
        stopped?.let { return it }
        limitUntil[service]?.let { if (wallMs < it) return ServerTrouble.DailyLimit(it) else limitUntil.remove(service) }
        noKeyUntil[service]?.let { if (wallMs < it) return ServerTrouble.NoKey else noKeyUntil.remove(service) }
        return null
    }

    /** Records the answer to an ask of [service]: its HTTP [code] (0: none) and the server's own [trouble], if any. */
    @Synchronized
    fun after(service: WayService, code: Int, trouble: ServerTrouble?, nowMs: Long, wallMs: Long): Outcome {
        val way = !service.matrix
        if (code == OK_CODE && trouble == null) {
            if (way) {
                failures = 0
                waitUntil = 0L
            }
            return Outcome.OK
        }
        return when (trouble) {
            null -> {
                if (way) waitUntil = nowMs + RETRY_MS
                if (code in 400..499 && code != TOO_MANY) Outcome.REFUSE_STOPS else Outcome.WAITING
            }
            ServerTrouble.NotEnrolled, ServerTrouble.Stopped, ServerTrouble.Role -> {
                stopped = trouble
                Outcome.STOPPED
            }
            is ServerTrouble.DailyLimit -> {
                limitUntil[service] = trouble.untilWallMs
                Outcome.WAITING
            }
            ServerTrouble.NoKey -> {
                noKeyUntil[service] = wallMs + NO_KEY_MS
                Outcome.WAITING
            }
            ServerTrouble.Down, ServerTrouble.UpstreamDown -> {
                if (way) {
                    waitUntil = nowMs + minOf(RETRY_MS shl failures.coerceAtMost(MAX_DOUBLINGS), MOST_WAIT_MS)
                    failures++
                }
                Outcome.WAITING
            }
            // The server refused the request itself: as Google's or mapmap's own refusal.
            is ServerTrouble.Bug -> {
                if (way) waitUntil = nowMs + RETRY_MS
                Outcome.REFUSE_STOPS
            }
        }
    }

    /** Forgets every answer: a new access or source asks afresh. */
    @Synchronized
    fun clear() {
        stopped = null
        limitUntil.clear()
        noKeyUntil.clear()
        waitUntil = 0L
        failures = 0
    }

    /** Until when (wall clock) each service waits for its daily limit on the company's server; a time may have passed. */
    @Synchronized
    fun limits(): Map<WayService, Long> = EnumMap(limitUntil)

    companion object {
        private const val OK_CODE = 200

        /** A way that did not come (no answer, an error or a refusal): asked again after this long. */
        const val RETRY_MS = 30_000L

        /** "Too many requests": a wait, not a refusal. */
        const val TOO_MANY = 429

        /** The server has no key for a service: asked again after this long. */
        const val NO_KEY_MS = 10 * 60_000L

        /** The longest wait while the server does not answer. */
        const val MOST_WAIT_MS = 5 * 60_000L

        private const val MAX_DOUBLINGS = 10
    }
}
