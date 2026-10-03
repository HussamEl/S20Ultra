package se.eldebosh.nastastopp.geo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.core.geo.GeoLogic
import se.eldebosh.nastastopp.core.geo.GeoResult
import se.eldebosh.nastastopp.core.geo.StreetInfo
import se.eldebosh.nastastopp.core.geo.StreetLookup
import se.eldebosh.nastastopp.core.geo.StreetMap
import se.eldebosh.nastastopp.core.geo.StreetMatcher
import se.eldebosh.nastastopp.core.geo.StreetTracker
import se.eldebosh.nastastopp.core.geo.Fix
import kotlin.math.roundToInt

/**
 * The street the vehicle is on now, and its speed. Kept in memory only — never stored, logged or
 * sent to passenger displays. All calls on the main thread.
 *
 * The street is exact or absent, never invented:
 * - with the offline street map ([map]), every good GPS position is matched to the road it is on
 *   ([StreetMatcher]: distance and heading);
 * - without it, the system geocoder's nearest address counts only when it lies within 40 m
 *   ([StreetLookup.pickNear]); lookups are throttled and run only while something shows the
 *   street (the floating button or the route screen);
 * - either way a new street is taken only after two readings agree ([StreetTracker]).
 * The area (district) comes from the geocoder in both cases.
 */
class CurrentStreet(
    private val scope: CoroutineScope,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val lookup: suspend (Double, Double) -> List<GeoResult>?,
) {
    private val _state = MutableStateFlow<StreetInfo?>(null)
    val state: StateFlow<StreetInfo?> = _state.asStateFlow()

    /** The offline street map, once downloaded and loaded (null: the geocoder is used). */
    @Volatile
    var map: StreetMap? = null

    private val tracker = StreetTracker()
    private var area: String? = null

    private var speedKmh: Int? = null
    private var speedAtMs = 0L
    private var previous: Fix? = null

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
        updateSpeed(fix)
        val m = map
        if (m != null) {
            // The road from the map, on every position; the geocoder only for the area.
            publish(tracker.onReading(StreetMatcher.match(m, fix), clockMs()))
        }
        if (wanted.isEmpty() || job?.isActive == true) return
        if (!StreetLookup.shouldLookup(lastLookup, fix, lastFailed, confirming = map == null && tracker.confirming)) return
        lastLookup = fix
        job = scope.launch {
            val info = try {
                lookup(fix.lat, fix.lng)?.let { StreetLookup.pickNear(it, fix.lat, fix.lng) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            lastFailed = info == null
            if (info == null) return@launch
            area = info.area ?: area
            // Without the map, the geocoder's street (if its address is close enough) is the reading.
            publish(if (map == null) tracker.onReading(info.street, clockMs()) else tracker.current)
        }
    }

    private fun publish(street: String?) {
        val info = if (street == null && area == null) null else StreetInfo(street, area)
        if (info != _state.value) _state.value = info
    }

    /** GPS speed when the position has one, otherwise from the distance to the previous position. */
    private fun updateSpeed(fix: Fix) {
        val prev = previous
        previous = fix
        val mps = fix.speedMps ?: prev?.let { p ->
            val seconds = (fix.timeMs - p.timeMs) / 1000.0
            val exact = (fix.accuracyM ?: 0f) <= DERIVED_MAX_ACCURACY_M && (p.accuracyM ?: 0f) <= DERIVED_MAX_ACCURACY_M
            if (exact && seconds in 0.5..10.0) (GeoLogic.distanceMeters(p.lat, p.lng, fix.lat, fix.lng) / seconds).toFloat() else null
        } ?: return
        speedKmh = (mps * MPS_TO_KMH).roundToInt().coerceAtLeast(0)
        speedAtMs = clockMs()
    }

    /** Forgets the street (route ended). */
    fun reset() {
        job?.cancel()
        job = null
        lastLookup = null
        lastFailed = false
        tracker.reset()
        area = null
        _state.value = null
        speedKmh = null
        previous = null
    }

    private companion object {
        const val MPS_TO_KMH = 3.6f

        /** A speed older than this is not shown (no new positions: signal lost or stopped service). */
        const val SPEED_FRESH_MS = 10_000L

        /** A speed worked out from two positions only when both are this exact. */
        const val DERIVED_MAX_ACCURACY_M = 20f
    }
}
