package se.eldebosh.nastastopp.geo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import se.eldebosh.nastastopp.core.geo.GeoLogic
import se.eldebosh.nastastopp.core.geo.GeoResult
import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.util.DebugLog
import java.util.Locale
import kotlin.coroutines.resume

/** Outcome of locating a stop: the chosen result and the candidate string that produced it. */
data class LocateResult(val result: GeoResult, val candidate: String)

/**
 * android.location.Geocoder wrapper (served by the system / Play Services process — this app has
 * no INTERNET permission). Swedish locale, Sweden bounding box, max 5 results, session cache.
 */
class Geocoding(context: Context) {

    private val appContext = context.applicationContext
    private val locale: Locale = Locale.forLanguageTag("sv-SE")
    private val cache = HashMap<String, List<GeoResult>>()

    val isAvailable: Boolean get() = Geocoder.isPresent()

    /**
     * Tries [candidates] in order. A result matching the parsed postal code (or town) wins at once;
     * otherwise the first non-empty answer is used.
     */
    suspend fun locate(candidates: List<String>, parsedPostal: String?, parsedTown: String?): LocateResult? {
        if (!isAvailable) return null
        var fallback: LocateResult? = null
        for (candidate in candidates.take(MAX_CANDIDATES)) {
            val results = lookup(candidate) ?: return fallback // geocoder failed (e.g. offline)
            if (results.isEmpty()) continue
            val chosen = GeoLogic.choose(results, parsedPostal, parsedTown) ?: continue
            val matches = matches(chosen, parsedPostal, parsedTown)
            if (matches) return LocateResult(chosen, candidate)
            if (fallback == null) fallback = LocateResult(chosen, candidate)
            if (parsedPostal == null && parsedTown == null) return fallback
        }
        return fallback
    }

    private fun matches(r: GeoResult, postal: String?, town: String?): Boolean {
        val p = postal?.filter { it.isDigit() }
        if (!p.isNullOrEmpty()) return r.postalCode?.filter { it.isDigit() } == p
        if (!town.isNullOrBlank()) {
            val t = TextNorm.fold(town)
            return listOfNotNull(r.locality, r.subLocality).any { TextNorm.fold(it) == t }
        }
        return true
    }

    /** Returns results (possibly empty), or null if the geocoder is unavailable / failed. */
    private suspend fun lookup(query: String): List<GeoResult>? {
        cache[query]?.let { return it }
        val addresses = withTimeoutOrNull(TIMEOUT_MS) { rawLookup(query) } ?: return null
        val results = addresses.mapNotNull { it.toResult() }
        cache[query] = results
        return results
    }

    private suspend fun rawLookup(query: String): List<Address>? {
        val geocoder = Geocoder(appContext, locale)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                try {
                    geocoder.getFromLocationName(
                        query, MAX_RESULTS,
                        GeoLogic.SWEDEN_LAT_MIN, GeoLogic.SWEDEN_LNG_MIN,
                        GeoLogic.SWEDEN_LAT_MAX, GeoLogic.SWEDEN_LNG_MAX,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses)
                            }

                            override fun onError(errorMessage: String?) {
                                DebugLog.d { "geocoder error: $errorMessage" }
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                } catch (e: Exception) {
                    DebugLog.w(e) { "geocoder threw" }
                    if (cont.isActive) cont.resume(null)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(
                        query, MAX_RESULTS,
                        GeoLogic.SWEDEN_LAT_MIN, GeoLogic.SWEDEN_LNG_MIN,
                        GeoLogic.SWEDEN_LAT_MAX, GeoLogic.SWEDEN_LNG_MAX,
                    ) ?: emptyList()
                } catch (e: Exception) {
                    DebugLog.w(e) { "geocoder threw" }
                    null
                }
            }
        }
    }

    /**
     * Addresses at a position (used for the street the vehicle is on now). Not cached; null if
     * the geocoder is unavailable or failed.
     */
    suspend fun reverse(lat: Double, lng: Double): List<GeoResult>? {
        if (!isAvailable) return null
        val addresses = withTimeoutOrNull(REVERSE_TIMEOUT_MS) { rawReverse(lat, lng) } ?: return null
        return addresses.mapNotNull { it.toResult() }
    }

    private suspend fun rawReverse(lat: Double, lng: Double): List<Address>? {
        val geocoder = Geocoder(appContext, locale)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                try {
                    geocoder.getFromLocation(
                        lat, lng, REVERSE_RESULTS,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses)
                            }

                            override fun onError(errorMessage: String?) {
                                DebugLog.d { "reverse geocoder error: $errorMessage" }
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                } catch (e: Exception) {
                    DebugLog.w(e) { "reverse geocoder threw" }
                    if (cont.isActive) cont.resume(null)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, REVERSE_RESULTS) ?: emptyList()
                } catch (e: Exception) {
                    DebugLog.w(e) { "reverse geocoder threw" }
                    null
                }
            }
        }
    }

    private fun Address.toResult(): GeoResult? {
        if (!hasLatitude() || !hasLongitude()) return null
        val line = if (maxAddressLineIndex >= 0) getAddressLine(0) else null
        return GeoResult(
            lat = latitude,
            lng = longitude,
            addressLine = line,
            postalCode = postalCode,
            locality = locality,
            subLocality = subLocality,
            thoroughfare = thoroughfare,
        )
    }

    companion object {
        private const val MAX_RESULTS = 5
        private const val MAX_CANDIDATES = 6
        private const val TIMEOUT_MS = 15_000L
        private const val REVERSE_RESULTS = 3
        private const val REVERSE_TIMEOUT_MS = 10_000L
    }
}
