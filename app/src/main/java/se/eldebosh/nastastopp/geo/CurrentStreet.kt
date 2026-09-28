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

/**
 * The street the vehicle is on now, from the route's location fixes through the system Geocoder
 * (reverse lookup). Kept in memory only — never stored, logged or sent to passenger displays.
 * Lookups run only while something shows it (the floating button or the route screen), and are
 * throttled by [StreetLookup]. All calls on the main thread.
 */
class CurrentStreet(
    private val scope: CoroutineScope,
    private val lookup: suspend (Double, Double) -> List<GeoResult>?,
) {
    private val _state = MutableStateFlow<StreetInfo?>(null)
    val state: StateFlow<StreetInfo?> = _state.asStateFlow()

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
    }
}
