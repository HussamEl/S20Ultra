package se.eldebosh.nastastopp.route

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.core.display.DisplayItem
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.core.geo.GeoLogic
import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.ExtractedStop
import se.eldebosh.nastastopp.core.parse.Localities
import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.core.parse.TripTimes
import se.eldebosh.nastastopp.core.route.Announcement
import se.eldebosh.nastastopp.core.route.Announcements
import se.eldebosh.nastastopp.core.route.ArrivalConfig
import se.eldebosh.nastastopp.core.route.ArrivalDetector
import se.eldebosh.nastastopp.core.route.DetectorEvent
import se.eldebosh.nastastopp.core.route.DetectorPhase
import se.eldebosh.nastastopp.core.route.Fix
import se.eldebosh.nastastopp.core.route.MapsUrlBuilder
import se.eldebosh.nastastopp.geo.Geocoding
import se.eldebosh.nastastopp.geo.LocateResult
import se.eldebosh.nastastopp.maps.MapsLauncher
import se.eldebosh.nastastopp.route.model.GeoPoint
import se.eldebosh.nastastopp.route.model.GeoStatus
import se.eldebosh.nastastopp.route.model.RouteData
import se.eldebosh.nastastopp.route.model.Stop
import se.eldebosh.nastastopp.service.RouteService
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.tts.Announcer

/** Live tracking info for the UI. */
data class TrackingState(
    val phase: DetectorPhase = DetectorPhase.IDLE,
    val distanceM: Double? = null,
    /** False when the current stop is not located or shares its place with a neighbour. */
    val autoEnabled: Boolean = false,
)

/**
 * Single source of truth for the route (draft and active). All calls on the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repo: RouteRepository,
    private val settings: SettingsStore,
    private val geocoding: Geocoding,
    private val announcer: Announcer,
    private val maps: MapsLauncher,
    private val localities: Localities,
    private val extractor: AddressExtractor,
) {
    private val _route = MutableStateFlow(repo.load())
    val route: StateFlow<RouteData?> = _route.asStateFlow()

    private val _tracking = MutableStateFlow(TrackingState())
    val tracking: StateFlow<TrackingState> = _tracking.asStateFlow()

    /** Every announcement this controller speaks (forwarded to connected passenger displays). */
    private val _announcements = MutableSharedFlow<Announcement>(extraBufferCapacity = 8)
    val announcements: SharedFlow<Announcement> = _announcements.asSharedFlow()

    /** What the passenger display shows (locally and on a connected tablet). */
    val display: StateFlow<DisplaySnapshot> = combine(_route, settings.state) { r, _ -> buildDisplay(r) }
        .stateIn(scope, SharingStarted.Eagerly, buildDisplay(_route.value))

    private val detector = ArrivalDetector(ArrivalConfig.forRadius(settings.current.arrivalRadiusM))
    private var detectorKey: String? = null
    private val persistDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var geocodeJob: Job? = null
    private var editBaseline: List<Long>? = null

    init {
        _route.value?.let { repo.scheduleExpiry(it.createdAtMs) }
        scope.launch {
            settings.state.map { it.arrivalRadiusM }.distinctUntilChanged().collect {
                detector.config = ArrivalConfig.forRadius(it)
            }
        }
        syncDetector()
        ensureGeocoding()
    }

    // ------------------------------------------------------------------------------------------
    // Queries

    /** Area/town name that may be spoken for [stop] — never a street, number or name. */
    fun spokenName(stop: Stop): String = GeoLogic.spokenName(
        subLocality = stop.geo?.subLocality,
        locality = stop.geo?.locality,
        parsedTown = stop.parsedTown,
        parsedTownKnown = stop.parsedTownKnown,
        detail = settings.current.detail,
        thoroughfare = stop.geo?.thoroughfare,
        isKnownLocality = localities::contains,
    )

    val isActive: Boolean get() = _route.value?.active == true

    fun announcementFor(stops: List<Stop>): Announcement =
        Announcements.forRemaining(stops.take(2).map { spokenName(it) }, settings.current.englishRepeat)

    private fun buildDisplay(r: RouteData?): DisplaySnapshot {
        if (r == null) return DisplaySnapshot()
        val full = settings.current.displayFullAddress
        return DisplaySnapshot.build(
            active = r.active,
            completed = r.completed,
            remaining = r.stops,
            item = { s -> DisplayItem(time = s.time, title = spokenName(s), subtitle = if (full) s.displayText else null) },
            announcement = if (r.active && r.stops.isNotEmpty()) announcementFor(r.stops) else null,
        )
    }

    /** Speaks on this device and tells connected displays. */
    private fun speak(announcement: Announcement) {
        announcer.speak(announcement)
        _announcements.tryEmit(announcement)
    }

    // ------------------------------------------------------------------------------------------
    // Editing (review screen)

    /** Appends extracted stops (merging a duplicate of the current last stop). Returns count added. */
    fun addExtracted(extracted: List<ExtractedStop>): Int {
        if (extracted.isEmpty()) return 0
        val base = _route.value ?: newRoute()
        var nextId = base.nextId
        val added = ArrayList<Stop>()
        for (e in extracted) {
            val prev = added.lastOrNull() ?: base.stops.lastOrNull()
            if (prev != null && extractor.isSameAddress(prev.toExtracted(), e)) continue
            added += e.toStop(nextId++)
        }
        set(base.copy(stops = base.stops + added, nextId = nextId))
        ensureGeocoding()
        return added.size
    }

    fun addManual(text: String, time: String? = null): Boolean {
        val e = extractor.fromManualText(text) ?: return false
        val base = _route.value ?: newRoute()
        set(base.copy(stops = base.stops + e.toStop(base.nextId).copy(time = time), nextId = base.nextId + 1))
        ensureGeocoding()
        return true
    }

    /**
     * Replaces a stop's text and time. The address is re-parsed and re-geocoded only if the text
     * changed.
     */
    fun editText(id: Long, text: String, time: String? = null): Boolean {
        val current = _route.value?.stops?.firstOrNull { it.id == id } ?: return false
        if (text.trim() == current.displayText) {
            update { r -> r.copy(stops = r.stops.map { if (it.id == id) it.copy(time = time) else it }) }
            return true
        }
        val e = extractor.fromManualText(text) ?: return false
        update { r ->
            r.copy(
                stops = r.stops.map {
                    if (it.id == id) e.toStop(id).copy(sourceOrder = it.sourceOrder, time = time) else it
                },
            )
        }
        ensureGeocoding()
        return true
    }

    /** Orders the remaining trips by their scheduled time (trips without a time keep their order, last). */
    fun sortByTime() = update { r -> r.copy(stops = r.stops.sortedBy { TripTimes.minutes(it.time) }) }

    fun retryLocate(id: Long) {
        update { r -> r.copy(stops = r.stops.map { if (it.id == id) it.copy(geoStatus = GeoStatus.PENDING, geo = null) else it }) }
        ensureGeocoding()
    }

    fun move(from: Int, to: Int) {
        update { r ->
            if (from !in r.stops.indices || to !in r.stops.indices || from == to) return@update r
            val list = r.stops.toMutableList()
            list.add(to, list.removeAt(from))
            r.copy(stops = list)
        }
    }

    fun delete(id: Long) = update { r -> r.copy(stops = r.stops.filterNot { it.id == id }) }

    /** Drops every stop above [id] (e.g. already completed trips in the screenshot). */
    fun deleteAllAbove(id: Long) = update { r ->
        val idx = r.stops.indexOfFirst { it.id == id }
        if (idx <= 0) r else r.copy(stops = r.stops.drop(idx))
    }

    /** Undo for delete / delete-all-above: puts a previous stop list back. */
    fun restoreStops(previous: List<Stop>) {
        update { r -> r.copy(stops = previous) }
        ensureGeocoding()
    }

    /** Deletes all route data (the "Clear" button). */
    fun clear() = endInternal()

    /** Called when the review screen is opened for an active route. */
    fun beginEdit() {
        editBaseline = _route.value?.takeIf { it.active }?.stops?.map { it.id }
    }

    /**
     * Called when leaving the review screen of an active route: re-announces if the next stop
     * changed and re-launches Maps if the first 10 stops changed.
     */
    fun finishEdit() {
        val baseline = editBaseline ?: return
        editBaseline = null
        val r = _route.value ?: return
        if (!r.active) return
        if (r.stops.isEmpty()) {
            end()
            return
        }
        val now = r.stops.map { it.id }
        val batchChanged = now.take(MapsUrlBuilder.MAX_STOPS_PER_LAUNCH) != baseline.take(MapsUrlBuilder.MAX_STOPS_PER_LAUNCH)
        if (now.firstOrNull() != baseline.firstOrNull() || now.getOrNull(1) != baseline.getOrNull(1)) {
            speak(announcementFor(r.stops))
        }
        if (batchChanged) openMaps()
    }

    // ------------------------------------------------------------------------------------------
    // Route

    /** "Starta rutt": announce immediately, open Maps with the first batch, start tracking. */
    fun start(): Boolean {
        val r = _route.value ?: return false
        if (r.stops.isEmpty()) return false
        val batch = r.stops.take(MapsUrlBuilder.MAX_STOPS_PER_LAUNCH)
        set(r.copy(active = true, batchEndStopId = batch.last().id))
        speak(announcementFor(r.stops))
        maps.launch(batch.map { it.navigationText })
        ensureServiceRunning()
        return true
    }

    /** Starts the foreground service if a route is active (call while the app is in the foreground). */
    fun ensureServiceRunning() {
        if (isActive && RouteService.hasLocationPermission(context)) RouteService.start(context)
    }

    /** Marks the current stop done, advances and announces. Automatic or manual ("Nästa"). */
    fun next(auto: Boolean = false) {
        val r = _route.value ?: return
        if (!r.active) return
        val done = r.stops.firstOrNull()
        val remaining = r.stops.drop(1)
        if (done == null || remaining.isEmpty()) {
            finish()
            return
        }
        var batchEnd = r.batchEndStopId
        val relaunch = batchEnd != null && (done.id == batchEnd || remaining.none { it.id == batchEnd })
        if (relaunch) batchEnd = remaining.take(MapsUrlBuilder.MAX_STOPS_PER_LAUNCH).last().id
        set(
            r.copy(
                stops = remaining,
                completed = r.completed + done,
                batchEndStopId = batchEnd,
            ),
        )
        speak(announcementFor(remaining))
        if (relaunch) {
            maps.launch(remaining.take(MapsUrlBuilder.MAX_STOPS_PER_LAUNCH).map { it.navigationText }, fromBackground = true)
        }
    }

    /** "Upprepa": repeats the announcement for the current state. */
    fun repeat() {
        val r = _route.value ?: return
        if (!r.active || r.stops.isEmpty()) return
        speak(announcementFor(r.stops))
    }

    /** "Öppna Maps": re-launches navigation with the remaining stops (max 10). */
    fun openMaps() {
        val r = _route.value ?: return
        if (r.stops.isEmpty()) return
        val batch = r.stops.take(MapsUrlBuilder.MAX_STOPS_PER_LAUNCH)
        if (r.active) set(r.copy(batchEndStopId = batch.last().id))
        maps.launch(batch.map { it.navigationText })
    }

    /** "Avsluta": ends the route and deletes its data. */
    fun end() {
        announcer.stop()
        endInternal()
    }

    private fun finish() {
        speak(Announcements.finished(settings.current.englishRepeat))
        endInternal()
    }

    private fun endInternal() {
        geocodeJob?.cancel()
        geocodeJob = null
        editBaseline = null
        set(null)
        RouteService.stop(context)
        maps.cancelOpenMapsNotification()
    }

    fun clearIfExpired() {
        val r = _route.value
        if (r == null) {
            repo.load() // deletes an expired file, if any
            return
        }
        if (repo.isExpired(r)) endInternal()
    }

    fun onLocation(fix: Fix) {
        val r = _route.value ?: return
        if (!r.active) return
        val event = detector.onFix(fix)
        _tracking.value = TrackingState(detector.phase, detector.lastDistanceM, detectorKey != null)
        if (event == DetectorEvent.Departed) next(auto = true)
    }

    // ------------------------------------------------------------------------------------------
    // Internals

    private fun newRoute(): RouteData {
        val now = System.currentTimeMillis()
        repo.scheduleExpiry(now)
        return RouteData(createdAtMs = now)
    }

    private inline fun update(transform: (RouteData) -> RouteData) {
        val r = _route.value ?: return
        val next = transform(r)
        if (next != r) set(next)
    }

    private fun set(value: RouteData?) {
        _route.value = value
        scope.launch(persistDispatcher) {
            if (value == null) repo.clear() else repo.save(value)
        }
        syncDetector()
    }

    /** Points the arrival detector at the current stop when automatic detection is allowed. */
    private fun syncDetector() {
        val r = _route.value
        val current = r?.takeIf { it.active }?.stops?.firstOrNull()
        val auto = current != null && current.isLocated &&
            !samePlace(current, r.stops.getOrNull(1)) && !samePlace(r.previousStop, current)
        val geo = current?.geo
        val key = if (auto && geo != null) "${current.id}:${geo.lat}:${geo.lng}" else null
        if (key != detectorKey) {
            detectorKey = key
            if (key != null && geo != null) detector.setTarget(geo.lat, geo.lng) else detector.setTarget(null, null)
        }
        _tracking.value = TrackingState(detector.phase, detector.lastDistanceM, key != null)
    }

    /** Two stops at the same place (≤ 30 m, or the same address text) — advance only manually. */
    fun samePlace(a: Stop?, b: Stop?): Boolean {
        if (a == null || b == null) return false
        val ga = a.geo
        val gb = b.geo
        if (a.isLocated && b.isLocated && ga != null && gb != null) {
            return GeoLogic.distanceMeters(ga.lat, ga.lng, gb.lat, gb.lng) <= SAME_PLACE_M
        }
        return TextNorm.key(a.navigationText) == TextNorm.key(b.navigationText)
    }

    private fun ensureGeocoding() {
        if (geocodeJob?.isActive == true) return
        geocodeJob = scope.launch {
            while (isActive) {
                val stop = _route.value?.stops?.firstOrNull { it.geoStatus == GeoStatus.PENDING } ?: break
                val result = try {
                    geocoding.locate(stop.candidates, stop.parsedPostalCode, stop.parsedTown)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                update { r ->
                    r.copy(
                        stops = r.stops.map { s ->
                            if (s.id == stop.id && s.geoStatus == GeoStatus.PENDING && s.candidates == stop.candidates) applyGeo(s, result) else s
                        },
                    )
                }
            }
        }
    }

    private fun applyGeo(stop: Stop, located: LocateResult?): Stop {
        if (located == null) return stop.copy(geoStatus = GeoStatus.NOT_LOCATED, geo = null)
        val res = located.result
        // Prefer the candidate that starts at the street the geocoder found: this drops leading
        // non-address tokens (e.g. a surname) from what is displayed and stored.
        val street = res.thoroughfare?.let { TextNorm.fold(it) }
        val display = street?.takeIf { it.isNotEmpty() }?.let { s ->
            stop.candidates.firstOrNull { TextNorm.fold(it).startsWith(s) }
        } ?: located.candidate
        return stop.copy(
            displayText = display,
            candidates = listOf(display),
            geoStatus = GeoStatus.LOCATED,
            geo = GeoPoint(
                lat = res.lat,
                lng = res.lng,
                addressLine = res.addressLine,
                postalCode = res.postalCode,
                locality = res.locality,
                subLocality = res.subLocality,
                thoroughfare = res.thoroughfare,
            ),
        )
    }

    private fun ExtractedStop.toStop(id: Long) = Stop(
        id = id,
        displayText = displayText,
        candidates = candidates,
        parsedPostalCode = parsedPostalCode,
        parsedTown = parsedTown,
        parsedTownKnown = parsedTownKnown,
        sourceOrder = sourceOrder,
        time = time,
    )

    private fun Stop.toExtracted() = ExtractedStop(displayText, candidates, parsedPostalCode, parsedTown, sourceOrder, parsedTownKnown, time)

    companion object {
        const val SAME_PLACE_M = 30.0
    }
}
