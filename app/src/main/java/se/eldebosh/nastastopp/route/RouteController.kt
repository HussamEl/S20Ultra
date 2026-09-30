package se.eldebosh.nastastopp.route

import android.content.Context
import androidx.annotation.VisibleForTesting
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import se.eldebosh.nastastopp.core.display.DisplayItem
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.core.nav.DisplayEta
import se.eldebosh.nastastopp.core.weather.DisplayWeather
import se.eldebosh.nastastopp.core.geo.AnnouncementDetail
import se.eldebosh.nastastopp.core.geo.GeoLogic
import se.eldebosh.nastastopp.core.geo.StreetInfo
import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.ExtractedStop
import se.eldebosh.nastastopp.core.parse.Localities
import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.core.parse.TripKind
import se.eldebosh.nastastopp.core.parse.TripTimes
import se.eldebosh.nastastopp.core.route.Announcement
import se.eldebosh.nastastopp.core.route.Announcements
import se.eldebosh.nastastopp.core.route.MapsUrlBuilder
import se.eldebosh.nastastopp.geo.Geocoding
import se.eldebosh.nastastopp.geo.LocateResult
import se.eldebosh.nastastopp.maps.MapsLauncher
import se.eldebosh.nastastopp.core.youdrive.TripWatch
import se.eldebosh.nastastopp.route.model.GeoPoint
import se.eldebosh.nastastopp.route.model.GeoStatus
import se.eldebosh.nastastopp.route.model.RouteData
import se.eldebosh.nastastopp.route.model.Stop
import se.eldebosh.nastastopp.service.StreetService
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.tts.Announcer
import kotlin.math.abs

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
    private val history: TripHistory,
) {
    private val _route = MutableStateFlow(repo.load())
    val route: StateFlow<RouteData?> = _route.asStateFlow()

    /** Every announcement this controller speaks (forwarded to connected passenger displays). */
    private val _announcements = MutableSharedFlow<Announcement>(extraBufferCapacity = 8)
    val announcements: SharedFlow<Announcement> = _announcements.asSharedFlow()

    /** The area's weather and Google Maps' travel time, for the passenger display. */
    private val extras = MutableStateFlow(DisplayExtras())

    /** What the passenger display shows (locally and on a connected tablet). */
    val display: StateFlow<DisplaySnapshot> = combine(_route, settings.state, extras) { r, _, x -> buildDisplay(r, x) }
        .stateIn(scope, SharingStarted.Eagerly, buildDisplay(_route.value, extras.value))

    /** Shows [weather] and Google Maps' remaining travel time ([eta]) on the passenger display. */
    fun setDisplayExtras(weather: DisplayWeather?, eta: DisplayEta?) {
        extras.value = DisplayExtras(weather, eta)
    }

    private val persistDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var geocodeJob: Job? = null
    private var editBaseline: List<Long>? = null

    init {
        _route.value?.let { repo.scheduleExpiry(it.createdAtMs) }
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

    /**
     * A stop's street and number as said aloud and shown beside its time on the panel
     * ("Storgatan 14"). A surname that some lists put before the street ("Andersson Storgatan 14")
     * is left out, so a name is never spoken.
     */
    fun streetOf(stop: Stop): String = DisplayItem.streetPart(TripWatch.streetAddress(stop.candidates, stop.displayText))

    /** The next stop in full (street and number, district, town), for the announcement. */
    fun fullSpokenName(stop: Stop): String = GeoLogic.fullSpokenName(
        street = streetOf(stop),
        district = spokenName(stop, AnnouncementDetail.DISTRICT),
        town = spokenName(stop, AnnouncementDetail.TOWN_ONLY),
    )

    private fun spokenName(stop: Stop, detail: AnnouncementDetail): String = GeoLogic.spokenName(
        subLocality = stop.geo?.subLocality,
        locality = stop.geo?.locality,
        parsedTown = stop.parsedTown,
        parsedTownKnown = stop.parsedTownKnown,
        detail = detail,
        thoroughfare = stop.geo?.thoroughfare,
        isKnownLocality = localities::contains,
    )

    /** The stop after the next one: its street and number, then its district (or town). */
    fun thenSpokenName(stop: Stop): String = GeoLogic.fullSpokenName(
        street = streetOf(stop),
        district = spokenName(stop, AnnouncementDetail.DISTRICT),
        town = null,
    )

    /**
     * "Nästa stopp: …. Därefter: …." With the full announcement (the default) the next stop is
     * said with its street and number, district and town, and the one after it with its street
     * and number and district. Otherwise both by district or town only.
     */
    fun announcementFor(stops: List<Stop>): Announcement {
        val first = stops.firstOrNull() ?: return Announcements.finished(settings.current.englishRepeat)
        val full = settings.current.detail == AnnouncementDetail.FULL
        val next = if (full) fullSpokenName(first) else spokenName(first)
        val then = stops.getOrNull(1)?.let { if (full) thenSpokenName(it) else spokenName(it) }
        return Announcements.forRemaining(listOfNotNull(next, then), settings.current.englishRepeat)
    }

    private fun buildDisplay(r: RouteData?, x: DisplayExtras): DisplaySnapshot {
        if (r == null) return DisplaySnapshot()
        val full = settings.current.displayFullAddress
        return DisplaySnapshot.build(
            active = r.active,
            completed = r.completed,
            remaining = r.stops,
            item = { s ->
                // The street address with the house number (setting 114), the area under it.
                if (full) DisplayItem(time = s.time, title = DisplayItem.streetPart(s.displayText), subtitle = spokenName(s), doneInYouDrive = s.youDriveDone)
                else DisplayItem(time = s.time, title = spokenName(s), doneInYouDrive = s.youDriveDone)
            },
            announcement = if (r.active && r.stops.isNotEmpty()) announcementFor(r.stops) else null,
        ).let { if (it.active) it.copy(weather = x.weather, eta = x.eta) else it }
    }

    private data class DisplayExtras(val weather: DisplayWeather? = null, val eta: DisplayEta? = null)

    /** Speaks on this device and tells connected displays. */
    private fun speak(announcement: Announcement) {
        announcer.speak(announcement)
        _announcements.tryEmit(announcement)
    }

    // ------------------------------------------------------------------------------------------
    // Editing (review screen)

    /**
     * Appends extracted stops (merging a duplicate of the current last stop). A "Pull-out" becomes
     * the route's start point instead of a stop. Returns how many stops were added.
     */
    fun addExtracted(extracted: List<ExtractedStop>): Int {
        if (extracted.isEmpty()) return 0
        var base = _route.value ?: newRoute()
        var nextId = base.nextId
        val added = ArrayList<Stop>()
        for (e in extracted) {
            if (e.kind == TripKind.PULL_OUT) {
                base = base.copy(depot = e.toStop(nextId++))
                continue
            }
            val prev = added.lastOrNull() ?: base.stops.lastOrNull()
            if (prev != null && extractor.isSameAddress(prev.toExtracted(), e) && extractor.sameKindOrUnknown(prev.toExtracted(), e)) continue
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
                    if (it.id == id) e.toStop(id).copy(sourceOrder = it.sourceOrder, time = time, kind = it.kind, name = it.name) else it
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

    // ------------------------------------------------------------------------------------------
    // YouDrive (trips added / cancelled on the dispatch page)

    /** True if [e] is already one of the remaining trips (see [sameTrip]), or the start point. */
    fun hasTrip(e: ExtractedStop): Boolean = findTrip(e) != null

    private fun findTrip(e: ExtractedStop): Stop? {
        val r = _route.value ?: return null
        if (e.kind == TripKind.PULL_OUT) return r.depot?.takeIf { extractor.isSameAddress(it.toExtracted(), e) }
        return r.stops.firstOrNull { sameTrip(it, e) }
    }

    /**
     * [stop] is the same YouDrive trip as [e]: the same address, the same kind and passenger when
     * both are known, and times at most [SAME_TRIP_MIN] minutes apart. The tolerance lets a trip
     * re-planned by YouDrive (or read from a screenshot with its booked time) still count as the
     * same trip, so it is refreshed instead of added twice.
     */
    private fun sameTrip(stop: Stop, e: ExtractedStop): Boolean {
        if (stop.kind != null && e.kind != null && stop.kind != e.kind) return false
        if (stop.name != null && e.name != null && !stop.name.equals(e.name, ignoreCase = true)) return false
        if (stop.time != null && e.time != null && abs(TripTimes.minutes(stop.time) - TripTimes.minutes(e.time)) > SAME_TRIP_MIN) return false
        return extractor.isSameAddress(stop.toExtracted(), e)
    }

    /**
     * Adds a trip from YouDrive in time order among the remaining trips (the current trip of an
     * active route stays first). Re-announces / re-launches Maps if the next trips changed.
     * Returns false if the trip is already in the list.
     */
    fun insertTrip(e: ExtractedStop): Boolean = importTrips(listOf(e)) == 1

    /** Adds / refreshes every YouDrive trip ([syncTrips]); returns how many were added. */
    fun importTrips(trips: List<ExtractedStop>): Int = syncTrips(trips).added

    /** What a sync with YouDrive did: trips [added], and trips already in the list [updated]. */
    data class SyncResult(val added: Int, val updated: Int)

    /**
     * Brings the list in line with YouDrive ("Add all trips"). A trip that is not in the list yet
     * is added in time order. A trip that is ([sameTrip]) takes over YouDrive's time, kind and
     * name, and other copies of it are removed. Stops YouDrive
     * does not know (added by hand or from a screenshot) stay. Announces and re-launches Maps at
     * most once.
     */
    fun syncTrips(trips: List<ExtractedStop>): SyncResult {
        beginEdit()
        var added = 0
        var updated = 0
        for (e in trips) {
            val base = _route.value ?: newRoute()
            if (e.kind == TripKind.PULL_OUT) {
                // The day's start point: shown above the trips, never navigated to.
                val old = base.depot
                if (old != null && extractor.isSameAddress(old.toExtracted(), e)) {
                    if (old.time != e.time || old.displayText != e.displayText) {
                        set(base.copy(depot = e.toStop(old.id)))
                        updated++
                    }
                } else {
                    set(base.copy(depot = e.toStop(base.nextId), nextId = base.nextId + 1))
                    added++
                }
                continue
            }
            val matches = base.stops.filter { sameTrip(it, e) }
            val finished = if (matches.isEmpty()) base.completed.firstOrNull { sameTrip(it, e) } else null
            if (finished != null) {
                // Already done here: it stays done; only YouDrive's own mark is kept up to date.
                if (finished.youDriveDone != e.youDriveDone) {
                    set(base.copy(completed = base.completed.map { if (it.id == finished.id) it.copy(youDriveDone = e.youDriveDone) else it }))
                    updated++
                }
                continue
            }
            if (matches.isEmpty()) {
                set(base.copy(stops = insertByTime(base.stops, e.toStop(base.nextId), base.active), nextId = base.nextId + 1))
                added++
                continue
            }
            val refreshed = refreshed(base, matches, e)
            if (refreshed != base.stops) {
                set(base.copy(stops = refreshed))
                updated++
            }
        }
        if (added + updated > 0) ensureGeocoding()
        finishEdit()
        return SyncResult(added, updated)
    }

    /**
     * The list with [matches] (copies of YouDrive trip [e]) made one up-to-date trip: YouDrive's
     * time, kind and name (and text, while the stop is not located yet). The current trip of an
     * active route stays first; a trip whose time moved goes back to its place in time order.
     */
    private fun refreshed(base: RouteData, matches: List<Stop>, e: ExtractedStop): List<Stop> {
        val current = base.stops.firstOrNull()?.takeIf { base.active }
        val keep = matches.firstOrNull { it.id == current?.id } ?: matches.first()
        var fresh = keep.copy(time = e.time ?: keep.time, kind = e.kind ?: keep.kind, name = e.name ?: keep.name, youDriveDone = e.youDriveDone)
        if (!keep.isLocated && keep.displayText != e.displayText) {
            fresh = fresh.copy(
                displayText = e.displayText,
                candidates = e.candidates,
                parsedPostalCode = e.parsedPostalCode,
                parsedTown = e.parsedTown,
                parsedTownKnown = e.parsedTownKnown,
                geoStatus = GeoStatus.PENDING,
                geo = null,
            )
        }
        val copies = matches.map { it.id }.toSet() - keep.id
        val list = base.stops.filterNot { it.id in copies }.map { if (it.id == keep.id) fresh else it }
        if (fresh.time == keep.time || keep.id == current?.id) return list
        return insertByTime(list.filterNot { it.id == fresh.id }, fresh, base.active)
    }

    /** [stop] inserted before the first later trip (after the current trip of an active route). */
    private fun insertByTime(list: List<Stop>, stop: Stop, active: Boolean): List<Stop> {
        val out = list.toMutableList()
        val first = if (active) minOf(1, out.size) else 0
        var index = out.size
        if (stop.time != null) {
            val t = TripTimes.minutes(stop.time)
            for (i in first until out.size) {
                if (TripTimes.minutes(out[i].time) > t) {
                    index = i
                    break
                }
            }
        }
        out.add(index, stop)
        return out
    }

    /** Removes a trip YouDrive reported as cancelled. Returns false if it is not in the list. */
    fun removeTrip(e: ExtractedStop): Boolean {
        val stop = findTrip(e) ?: return false
        beginEdit()
        update { r -> r.copy(stops = r.stops.filterNot { it.id == stop.id }, depot = r.depot?.takeUnless { it.id == stop.id }) }
        finishEdit()
        return true
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

    /** "Starta rutt": announce immediately, open Maps with the first batch, find the street. */
    fun start(): Boolean {
        val r = _route.value ?: return false
        if (r.stops.isEmpty()) return false
        val batch = r.stops.take(MapsUrlBuilder.MAX_STOPS_PER_LAUNCH)
        set(r.copy(active = true, batchStartStopId = batch.first().id, batchEndStopId = batch.last().id))
        speak(announcementFor(r.stops))
        maps.launch(batch.map { it.navigationText })
        ensureStreetService()
        return true
    }

    /**
     * Starts naming the street while a route is active, if the driver allowed location (call while
     * the app is in the foreground). Its positions only name the street: they never move the route on.
     */
    fun ensureStreetService() {
        if (isActive) StreetService.start(context)
    }

    /** Marks the current stop done, advances and announces ("Nästa": always the driver's tap). */
    fun next() {
        val r = _route.value ?: return
        if (!r.active) return
        val done = r.stops.firstOrNull()
        val remaining = r.stops.drop(1)
        if (done != null) record(done, completed = true)
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
                batchStartStopId = if (relaunch) remaining.first().id else r.batchStartStopId,
            ),
        )
        speak(announcementFor(remaining))
        if (relaunch) {
            maps.launch(remaining.take(MapsUrlBuilder.MAX_STOPS_PER_LAUNCH).map { it.navigationText }, fromBackground = true)
        }
    }

    /**
     * "Back": undoes the last "Nästa". The previous trip becomes the current
     * one again, its history entry is removed and the announcement is repeated. If Google Maps was
     * launched starting at the current trip (end of a batch, or "Open Maps"), it is launched again
     * from the restored trip so the navigation includes it. Returns false if there is no trip to
     * go back to.
     */
    fun back(): Boolean {
        val r = _route.value ?: return false
        if (!r.active) return false
        val restored = r.completed.lastOrNull() ?: return false
        history.removeLatest(restored.displayText, restored.time)
        val stops = listOf(restored) + r.stops
        val relaunch = r.batchStartStopId != null && r.batchStartStopId == r.stops.firstOrNull()?.id
        val batch = stops.take(MapsUrlBuilder.MAX_STOPS_PER_LAUNCH)
        set(
            r.copy(
                stops = stops,
                completed = r.completed.dropLast(1),
                batchStartStopId = if (relaunch) restored.id else r.batchStartStopId,
                batchEndStopId = if (relaunch) batch.last().id else r.batchEndStopId,
            ),
        )
        speak(announcementFor(stops))
        if (relaunch) maps.launch(batch.map { it.navigationText }, fromBackground = true)
        return true
    }

    /**
     * Speaks the street the vehicle is on now and its area, when the driver taps the street. Not
     * sent to passenger displays. Returns false when no street is known yet.
     */
    fun speakStreet(info: StreetInfo?): Boolean {
        val text = info?.spoken ?: return false
        announcer.speak(Announcement(text, null))
        return true
    }

    /**
     * Says the next stop's street and number ("Storgatan 14") when the driver taps them on the
     * floating panel. Never the passenger's name, and not sent to passenger displays. Returns
     * false without a route.
     */
    fun speakStopStreet(): Boolean {
        val stop = _route.value?.takeIf { it.active }?.stops?.firstOrNull() ?: return false
        announcer.speak(Announcement(streetOf(stop), null))
        return true
    }

    /**
     * Says the street the vehicle has just turned into (the panel's speaker button or Settings 137
     * turn it off). Queued after any announcement, only during a
     * route, and not sent to passenger displays.
     */
    fun sayStreetChange(street: String): Boolean {
        if (!isActive || !settings.current.sayStreetChanges) return false
        announcer.speak(Announcement(street, null), interrupt = false)
        return true
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
        if (r.active) set(r.copy(batchStartStopId = batch.first().id, batchEndStopId = batch.last().id))
        maps.launch(batch.map { it.navigationText })
    }

    /**
     * "Avsluta": ends the route and deletes the route data. Trips that were still open are kept in
     * the history (marked as not completed) so the day's trips stay visible on the Home screen.
     */
    fun end() {
        announcer.stop()
        _route.value?.takeIf { it.active }?.stops?.forEach { record(it, completed = false) }
        endInternal()
    }

    /** Adds a trip of the active route to the history shown on the Home screen. */
    private fun record(stop: Stop, completed: Boolean) {
        history.add(stop.time, spokenName(stop), stop.displayText, done = completed)
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
        StreetService.stop(context)
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
    }

    /** Waits until every queued save / delete of the route file is done (tests only). */
    @VisibleForTesting
    internal fun awaitPersisted() = runBlocking(persistDispatcher) {}

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
        kind = kind,
        name = name,
        youDriveDone = youDriveDone,
    )

    private fun Stop.toExtracted() = ExtractedStop(displayText, candidates, parsedPostalCode, parsedTown, sourceOrder, parsedTownKnown, time, kind, name, youDriveDone)

    companion object {
        /** Two readings of one YouDrive trip are at most this many minutes apart (see [sameTrip]). */
        const val SAME_TRIP_MIN = 45
    }
}
