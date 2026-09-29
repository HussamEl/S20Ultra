package se.eldebosh.nastastopp.geo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.core.geo.GeoResult
import se.eldebosh.nastastopp.core.geo.StreetInfo
import se.eldebosh.nastastopp.core.geo.StreetLookup
import se.eldebosh.nastastopp.core.route.Fix
import kotlin.math.roundToInt

/**
 * The street the vehicle is on now, from the route's location fixes through the system Geocoder
 * (reverse lookup), and its speed (1.7). Kept in memory only — never stored, logged or sent to
 * passenger displays. Lookups run only while something shows it (the floating button or the
 * route screen), and are throttled by [StreetLookup]. All calls on the main thread.
 */
class CurrentStreet(
    private val scope: CoroutineScope,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val lookup: suspend (Double, Double) -> List<GeoResult>?,
) {
    private val _state = MutableStateFlow<StreetInfo?>(null)
    val state: StateFlow<StreetInfo?> = _state.asStateFlow()

    private var speedKmh: Int? = null
    private var speedAtMs = 0L

    /** The speed of the last position in km/h, or null when unknown or older than [SPEED_FRESH_MS]. */
    fun speedNow(): Int? = speedKmh?.takeIf { clockMs() - speedAtMs <= SPEED_FRESH_MS }

    private val wanted = HashSet<String>()
    private var lastLookup: Fix? = null
    private var lastFailed = false
    private var job: Job? = null

    val isWanted: Boolean get() = wanted.isNotEmpty()

    /** Registers / unregisters a screen that shows the street ([key] identifies it). */
    fun want(key: String, on: Boolean) {
        if (on) wanted += key else wanted -= key
    }

    fun onFix(fix: Fix) {
        fix.speedMps?.let { mps ->
            speedKmh = (mps * MPS_TO_KMH).roundToInt().coerceAtLeast(0)
            speedAtMs = clockMs()
        }
        if (wanted.isEmpty() || job?.isActive == true) return
        if (!StreetLookup.shouldLookup(lastLookup, fix, lastFailed)) return
        lastLookup = fix
        job = scope.launch {
            val info = try {
                lookup(fix.lat, fix.lng)?.let { StreetLookup.pick(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            lastFailed = info == null
            if (info != null) _state.value = info
        }
    }

    /** Forgets the street (route ended). */
    fun reset() {
        job?.cancel()
        job = null
        lastLookup = null
        lastFailed = false
        _state.value = null
        speedKmh = null
    }

    private companion object {
        const val MPS_TO_KMH = 3.6f

        /** A speed older than this is not shown (no new positions: signal lost or stopped service). */
        const val SPEED_FRESH_MS = 10_000L
    }
}
