package se.eldebosh.nastastopp.geo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import se.eldebosh.nastastopp.core.geo.AddressRegister
import se.eldebosh.nastastopp.core.geo.GeoLogic
import se.eldebosh.nastastopp.core.geo.GeoResult
import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.util.DebugLog
import java.util.Locale
import kotlin.coroutines.resume

/** The geocoder could not answer one of the questions (offline, busy): the stop is asked again later. */
class GeocoderFailed : Exception("geocoder failed")

/** Outcome of locating a stop: the chosen result and the candidate string that produced it. */
data class LocateResult(val result: GeoResult, val candidate: String)

/**
 * Finds a stop's point: first in Lantmäteriet's address register on the phone ([AddressRegister],
 * where the written postcode or town is the register's), then with android.location.Geocoder
 * (served by the system / Play Services process). Swedish locale, Sweden bounding box, max 5
 * results, session cache.
 */
class Geocoding(context: Context) {

    private val appContext = context.applicationContext
    private val locale: Locale = Locale.forLanguageTag("sv-SE")
    private val cache = HashMap<String, List<GeoResult>>()

    val isAvailable: Boolean get() = Geocoder.isPresent()

    /**
     * Tries [candidates] in order, in Lantmäteriet's register, then with the geocoder. A result matching the written postal code or town wins
     * ([GeoLogic.choose]); an answer in another place is never used, and the next candidate is
     * tried. Without a postal code or town the stop is looked for in Värmland only, and taken
     * only when it is in one town ([GeoLogic.inOneTown]): a town is never guessed.
     */
    suspend fun locate(candidates: List<String>, parsedPostal: String?, parsedTown: String?): LocateResult? {
        // Lantmäteriet's register first: on the phone, and it knows addresses the geocoder does not.
        val known = loaded ?: withContext(Dispatchers.IO) { register(appContext) }
        for (candidate in candidates.take(MAX_CANDIDATES)) {
            known.find(candidate, parsedPostal, parsedTown)?.let { return LocateResult(it, candidate) }
        }
        if (!isAvailable) return null
        val unplaced = parsedPostal == null && parsedTown == null
        var failed = false
        for (candidate in candidates.take(MAX_CANDIDATES)) {
            // A failed question (offline, busy) does not end the search: the next candidate is asked.
            val results = lookup(candidate, unplaced)
            if (results == null) {
                failed = true
                continue
            }
            if (results.isEmpty()) continue
            if (unplaced) {
                val one = GeoLogic.inOneTown(results) ?: continue
                return LocateResult(one, candidate)
            }
            GeoLogic.choose(results, parsedPostal, parsedTown)?.let { return LocateResult(it, candidate) }
        }
        if (failed) throw GeocoderFailed()
        return null
    }

    /** Returns results (possibly empty), or null if the geocoder is unavailable / failed; in Värmland when [varmland]. */
    private suspend fun lookup(query: String, varmland: Boolean = false): List<GeoResult>? {
        val key = if (varmland) "$query|V" else query
        cache[key]?.let { return it }
        val addresses = withTimeoutOrNull(TIMEOUT_MS) { rawLookup(query, varmland) } ?: return null
        val results = addresses.mapNotNull { it.toResult() }
        cache[key] = results
        return results
    }

    private suspend fun rawLookup(query: String, varmland: Boolean): List<Address>? {
        val geocoder = Geocoder(appContext, locale)
        val box = if (varmland) {
            doubleArrayOf(GeoLogic.VARMLAND_LAT_MIN, GeoLogic.VARMLAND_LNG_MIN, GeoLogic.VARMLAND_LAT_MAX, GeoLogic.VARMLAND_LNG_MAX)
        } else {
            doubleArrayOf(GeoLogic.SWEDEN_LAT_MIN, GeoLogic.SWEDEN_LNG_MIN, GeoLogic.SWEDEN_LAT_MAX, GeoLogic.SWEDEN_LNG_MAX)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                try {
                    geocoder.getFromLocationName(
                        query, MAX_RESULTS, box[0], box[1], box[2], box[3],
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
                    geocoder.getFromLocationName(query, MAX_RESULTS, box[0], box[1], box[2], box[3]) ?: emptyList()
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
        /** Lantmäteriet's addresses, read once for the app's life ([register]). */
        @Volatile
        private var loaded: AddressRegister? = null

        /**
         * Lantmäteriet's addresses, read from the app's assets the first time they are wanted
         * (off the main thread: the app starts reading them at its start, [App]).
         */
        fun register(context: Context): AddressRegister = loaded ?: synchronized(this) {
            loaded ?: runCatching { context.assets.open(AddressRegister.ASSET).bufferedReader().use { AddressRegister.parse(it.readText()) } }
                .onFailure { DebugLog.w(it) { "address register not read" } }
                .getOrDefault(AddressRegister.EMPTY)
                .also { loaded = it }
        }

        private const val MAX_RESULTS = 5
        private const val MAX_CANDIDATES = 6
        private const val TIMEOUT_MS = 15_000L
        private const val REVERSE_RESULTS = 3
        private const val REVERSE_TIMEOUT_MS = 10_000L
    }
}
