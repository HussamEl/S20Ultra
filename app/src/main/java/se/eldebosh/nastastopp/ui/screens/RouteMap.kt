package se.eldebosh.nastastopp.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.eldebosh.nastastopp.core.geo.GeoLogic
import se.eldebosh.nastastopp.core.nav.MapWay
import se.eldebosh.nastastopp.core.nav.MapmapApi
import se.eldebosh.nastastopp.core.nav.OrderPlanner
import se.eldebosh.nastastopp.core.nav.RouteLine
import se.eldebosh.nastastopp.core.nav.RoutesApi
import se.eldebosh.nastastopp.core.nav.WaySource
import java.net.HttpURLConnection
import java.net.URI
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import se.eldebosh.nastastopp.ui.theme.DayColors
import se.eldebosh.nastastopp.ui.theme.DisplayColors

/**
 * The tablet's map of the way to the next stop: Google's map in a WebView of its own, with the
 * driver's own key (entered on the tablet, 208) and the route from Google's Routes API, or from
 * mapmap.ai when the driver chose it (304, [useWays]). Only the tablet's own position (from
 * [se.eldebosh.nastastopp.geo.TabletPosition]) and the stops go to them, never a name. The page
 * itself never gets the location; nothing is stored or logged. The page grows and fades the map
 * itself ([reveal], [conceal]): the WebView is never scaled or faded from outside, so it is drawn
 * the same on every device. What stops the map ([trouble],
 * [routeAnswer]) is shown under it, for the driver to see why.
 *
 * One map for the display's whole life (Google counts each map made, not what it shows): only
 * looked at, touches never reach it. Opened by the driver ([reveal] held), it is his, moved by his
 * fingers and buttons ([showSatellite], [toCar], [toStop], [whole]). Google's 3D view and street photos are
 * Google's own apps, opened by the display ([se.eldebosh.nastastopp.maps.MapsLauncher.openEarth]),
 * never made here. Every way goes from the vehicle through a few stops in turn, lettered on the map
 * ([focus]); the driver can try another order and ask for the best one ([suggest]).
 *
 * @param ground the page's colour ("#F3F4F6"), shown until the tiles come.
 */
@Stable
class RouteMap(context: Context, private val key: String, night: Boolean, ground: String, private val scope: CoroutineScope) {
    /** The map has loaded with the key. */
    var ready by mutableStateOf(false)
        private set

    /** Google refused the key. */
    var refused by mutableStateOf(false)
        private set

    /** The WebView's renderer stopped (memory): this map is finished, and a new one takes its place. */
    var gone by mutableStateOf(false)
        private set

    /** The map's pictures (Google's tiles) have come at least once. */
    var tiles by mutableStateOf(false)
        private set

    /** What stops the map, or null. */
    var trouble by mutableStateOf<Trouble?>(null)
        private set

    /** Google's answer to the last ask for the way when it gave none (an HTTP code; 0: no answer), or null. */
    var routeAnswer by mutableStateOf<Int?>(null)
        private set

    /**
     * The way on the map, from the vehicle through its stops in turn: the next stops, or the
     * driver's ([focus]); null before an answer. [routeKey] names the stops it goes through, in
     * their order ([keyOf]), so a way asked for another order is never taken for this one.
     */
    var route by mutableStateOf<RouteLine?>(null)
        private set

    var routeKey by mutableStateOf<String?>(null)
        private set

    /** Where the vehicle is (the tablet's own position) and the next stops. */
    private var vehicle by mutableStateOf<MapWay?>(null)

    /** The map knows where the vehicle is. */
    val located: Boolean get() = vehicle != null

    /** The best order Google's travel times give for the driver's way ([suggest]), or null. */
    var suggestion by mutableStateOf<Suggestion?>(null)
        private set

    /** Google is being asked for the travel times between the driver's stops. */
    var suggesting by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var askedFor: String? = null
    private var askedAtMs = 0L

    /**
     * When Google last gave no way for the next stops (no network, an error): asked again after
     * [RETRY_MS]; not for the same stops when Google refused the request itself ([refusedFor]: the
     * key, its rights or the request; asking again would be refused again).
     */
    private var failedAtMs: Long? = null
    private var refusedFor: String? = null

    /** The last answer's HTTP code (0: no answer), as it came. */
    @Volatile private var lastCode: Int? = null

    /** Where the vehicle was when the next stops' way was last asked for. */
    private var askedFrom: MapWay? = null
    private var asking: Job? = null

    /**
     * Ways already given, in memory only and for a short while ([KNOWN_MS]), from about
     * the same place ([REUSE_M]): a stop opened again, or an order tried again, needs no new ask.
     */
    private val known = object : LinkedHashMap<String, Known>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Known>?): Boolean = size > KNOWN_WAYS
    }

    private class Known(val atMs: Long, val fromLat: Double, val fromLng: Double, val line: RouteLine)

    private fun knownFor(key: String, from: MapWay): RouteLine? = known[key]?.takeIf {
        SystemClock.elapsedRealtime() - it.atMs < KNOWN_MS &&
            GeoLogic.distanceMeters(it.fromLat, it.fromLng, from.lat, from.lng) < REUSE_M
    }?.line

    /** Who gives the ways and travel times, and the driver's mapmap key ([useWays]). */
    @Volatile private var waySource = WaySource.GOOGLE
    @Volatile private var mapmapKey: String? = null

    /**
     * The ways and travel times from [source], mapmap's with the driver's own [key] (304–309).
     * A change forgets the ways already given, so the next one comes from the source chosen.
     */
    fun useWays(source: WaySource, key: String?) {
        val usable = key?.takeIf { MapmapApi.isKey(it) }
        if (source == waySource && usable == mapmapKey) return
        waySource = source
        mapmapKey = usable
        known.clear()
        refusedFor = null
        failedAtMs = null
        askedFor = null
    }

    /**
     * mapmap's key when the driver chose mapmap and has its key, and every stop of [stops] has its
     * point (mapmap is asked by points only); null: Google is asked.
     */
    private fun mapmap(stops: List<MapWay.Stop>): String? = mapmapKey?.takeIf { waySource == WaySource.MAPMAP && MapmapApi.canAsk(stops) }

    /** The driver's way until [unfocus]: its stops in turn and the one he looks at; null for the next stops. */
    private var focused: Way? = null

    /** The driver's way: through [stops] in turn, [at] the one he looks at. */
    data class Way(val stops: List<MapWay.Stop>, val at: Int)

    /**
     * The best order for the driver's way ([key]: its stops as asked), as indexes into them, with
     * the way as it was asked ([current]) to compare.
     */
    data class Suggestion(val key: String, val best: OrderPlanner.Plan, val current: OrderPlanner.Plan?)

    /** The driver holds the map (a held [reveal]): his touches reach it. */
    private var held = false

    /** Each touch on the held map (the display keeps it open while it is used). */
    var onTouch: (() -> Unit)? = null

    /** The page's last [reveal] or [conceal], given again when the page has loaded. */
    private var stage: String? = null

    /** The display's look given after the page was made ([setLook]), given again when it has loaded. */
    private var look: String? = null

    /** Where the driver's list of trips is over the held map ([listAt]), given again when the page has loaded. */
    private var room: String? = null


    // JavaScript runs Google's map; the page is the app's own and nothing else can be loaded.
    // onRenderProcessGone is implemented below; lint does not see it in an object expression.
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility", "MissingOnRenderProcessGone")
    val view: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setGeolocationEnabled(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportZoom(false)
        isFocusable = false
        // The ground's colour from the start, never a white flash before the page. Not drawn while
        // the map is away ([conceal]): a full-screen page drawn under the display with every frame
        // of its moving parts would cost the tablet smoothness.
        setBackgroundColor(ground.toColorInt())
        visibility = View.INVISIBLE
        // Only the map the driver holds is touched; else touches never reach it.
        setOnTouchListener { _, event ->
            if (held && event.actionMasked == MotionEvent.ACTION_DOWN) onTouch?.invoke()
            !held
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onPageFinished(view: WebView, url: String?) {
                look?.let { view.evaluateJavascript(it, null) }
                room?.let { view.evaluateJavascript(it, null) }
                stage?.let { view.evaluateJavascript(it, null) }
                if (satellite) view.evaluateJavascript("satellite(true)", null)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                ready = false
                gone = true
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, false, false)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.deny()
            }

            // Nothing from the page reaches the log.
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true
        }
        addJavascriptInterface(Bridge(), "Android")
        val page = context.assets.open(PAGE).bufferedReader().use { it.readText() }
            .replace("__KEY__", key)
            .replace("__NIGHT__", night.toString())
            .replace("__GROUND__", ground)
            // The borders are the app's own (SCB's, drawn on the page): nothing is asked for them.
            .replace("__BORDERS__", runCatching { context.assets.open(BORDERS).bufferedReader().use { it.readText() } }.getOrDefault("null"))
        loadDataWithBaseURL(BASE, page, "text/html", "utf-8", null)
    }

    /**
     * The map grows out of ([x], [y]), fractions of its size, over [ms]: the driver's own, taking
     * his touches while [held].
     */
    fun reveal(x: Float, y: Float, ms: Int, held: Boolean = false) {
        main.removeCallbacks(hide)
        this.held = held
        view.visibility = View.VISIBLE
        stage = String.format(Locale.ROOT, "hold(%b);reveal(%.4f,%.4f,%d)", held, x, y, ms)
        js(stage!!)
    }

    /** The map goes back where it came from, over [ms], and is then no longer drawn. */
    fun conceal(ms: Int) {
        held = false
        stage = "hold(false);conceal($ms)"
        js(stage!!)
        main.removeCallbacks(hide)
        main.postDelayed(hide, ms + HIDE_AFTER_MS)
    }

    /**
     * The driver's list of trips covers [left] to [right] (shares of the map's width): the way
     * keeps clear of it, on the side it is nearer to.
     */
    fun listAt(left: Float, right: Float) {
        val next = if (left + right < 1f) {
            String.format(Locale.ROOT, "room(%.2f,0)", right.coerceIn(0f, 1f))
        } else {
            String.format(Locale.ROOT, "room(0,%.2f)", (1f - left).coerceIn(0f, 1f))
        }
        if (next == room) return
        room = next
        js(next)
    }

    /** Google's satellite picture (with the roads' names) instead of the map, on the driver's tap (270). */
    var satellite by mutableStateOf(false)
        private set

    /** The satellite picture ([on]) or the map: the same map, never a new one. */
    fun showSatellite(on: Boolean) {
        satellite = on
        js("satellite($on)")
    }

    /** The held map to the car. */
    fun toCar() = js("toCar()")

    /** The held map to the stop looked at. */
    fun toStop() = js("toStop()")

    /** The car, the way and the stop at once. */
    fun whole() = js("whole()")

    /** The held map to stop [index] of the driver's way. */
    fun lookAt(index: Int) = js("lookAt($index)")

    /**
     * The display's look changed (black or light): the same map in the new colours, never a new
     * one (Google counts each map made).
     */
    fun setLook(night: Boolean, ground: String) {
        view.setBackgroundColor(ground.toColorInt())
        look = "setLook($night,'$ground')"
        js(look!!)
    }

    private val hide = Runnable { view.visibility = View.INVISIBLE }

    /** Moves the vehicle on the map; asks for the way through the next stops again when they changed or a while has passed. */
    fun show(way: MapWay) {
        val first = vehicle == null
        vehicle = way
        js(String.format(Locale.ROOT, "setCar(%.6f,%.6f)", way.lat, way.lng))
        focused?.let { target ->
            // Asked for before the position came: the way now.
            if (first) ask(target)
            return
        }
        if (way.stops.isEmpty()) return
        val key = keyOf(way.stops)
        val now = SystemClock.elapsedRealtime()
        if (asking?.isActive == true) return
        if (key == refusedFor) return
        failedAtMs?.let { if (now - it < RETRY_MS) return }
        // The same stops: asked again only after a while and once the car has gone some way, so a
        // car waiting at a stop asks nothing; at once when no way came.
        val from = askedFrom
        if (failedAtMs == null && key == askedFor && from != null &&
            (now - askedAtMs < REFRESH_MS || GeoLogic.distanceMeters(from.lat, from.lng, way.lat, way.lng) < MOVED_M)
        ) {
            return
        }
        askedFor = key
        askedAtMs = now
        askedFrom = way
        knownFor(key, way)?.let { line ->
            failedAtMs = null
            if (focused == null) draw(line, Way(way.stops, 0))
            return
        }
        asking = scope.launch {
            val line = withContext(Dispatchers.IO) { fetch(way.lat, way.lng, way.stops) }
            if (line == null) {
                failedAtMs = SystemClock.elapsedRealtime()
                if (lastCode.let { it != null && it in 400..499 && it != TOO_MANY }) refusedFor = key
                return@launch
            }
            failedAtMs = null
            known[key] = Known(SystemClock.elapsedRealtime(), way.lat, way.lng, line)
            if (focused == null) draw(line, Way(way.stops, 0))
        }
    }

    /**
     * Shows the driver's way until [unfocus]: from the vehicle through [stops] in turn, [at] the one
     * he looks at (a trip pressed long, the map sign, or an order he is trying); as soon as the
     * vehicle's position is known.
     */
    fun focus(stops: List<MapWay.Stop>, at: Int) {
        val way = Way(stops, at.coerceIn(0, (stops.size - 1).coerceAtLeast(0)))
        // A way already given, while it still holds ([knownFor]); an old one is asked for again.
        val cached = vehicle?.let { knownFor(keyOf(stops), it) }
        if (way == focused && (cached != null || asking?.isActive == true)) return
        focused = way
        if (cached != null) {
            draw(cached, way)
            return
        }
        // The stops at once, in their new order; the way through them when Google gives it.
        js("setWay(" + wayJson(way, null) + ")")
        asking?.cancel()
        askedFor = null
        ask(way)
    }

    private fun ask(way: Way) {
        val from = vehicle ?: return
        val key = keyOf(way.stops)
        knownFor(key, from)?.let {
            draw(it, way)
            return
        }
        asking = scope.launch {
            val line = withContext(Dispatchers.IO) { fetch(from.lat, from.lng, way.stops) } ?: return@launch
            known[key] = Known(SystemClock.elapsedRealtime(), from.lat, from.lng, line)
            if (focused?.stops == way.stops) draw(line, focused!!)
        }
    }

    /** Back to the way through the next stops. */
    fun unfocus() {
        if (focused == null) return
        focused = null
        suggestion = null
        val next = vehicle?.stops.orEmpty()
        val line = vehicle?.let { knownFor(keyOf(next), it) }
        if (line != null) {
            draw(line, Way(next, 0))
        } else {
            route = null
            routeKey = null
            js("setWay(" + wayJson(Way(next, 0), null) + ")")
        }
    }

    /**
     * Asks Google or mapmap ([useWays]) for the travel times between the vehicle and the driver's [stops], and finds the
     * best order for them ([trips], the same order; [now]: seconds of the day): [suggestion].
     */
    fun suggest(stops: List<MapWay.Stop>, trips: List<OrderPlanner.Trip>, now: Int) {
        val from = vehicle ?: return
        if (suggesting || stops.size != trips.size) return
        suggesting = true
        suggestion = null
        scope.launch {
            val seconds = withContext(Dispatchers.IO) { fetchMatrix(from.lat, from.lng, stops) }
            suggesting = false
            seconds ?: return@launch
            // The order shown may have no way (or drop a passenger off first): the best one is still put up.
            val current = OrderPlanner.plan(stops.indices.toList(), trips, seconds, now)?.takeIf { OrderPlanner.allowed(it.order, trips) }
            val best = OrderPlanner.best(trips, seconds, now) ?: return@launch
            suggestion = Suggestion(keyOf(stops), best, current)
        }
    }

    private fun draw(line: RouteLine, way: Way) {
        route = line
        routeKey = keyOf(way.stops)
        js("setWay(" + wayJson(way, line) + ")")
    }

    /**
     * The way for the page: each stop's point (its own, else where its leg ends; null when neither
     * is known yet), the one looked at, and each leg's line (none before Google's answer).
     */
    private fun wayJson(way: Way, line: RouteLine?): String {
        fun point(p: Pair<Double, Double>?) = p?.let { (lat, lng) -> String.format(Locale.ROOT, "[%.6f,%.6f]", lat, lng) } ?: "null"
        val legs = line?.legs?.takeIf { it.size == way.stops.size }
        val stops = way.stops.mapIndexed { i, s ->
            point(if (s.lat != null && s.lng != null) s.lat to s.lng else legs?.get(i)?.path?.lastOrNull())
        }
        val paths = when {
            line == null -> "null"
            legs != null -> legs.joinToString(",", "[", "]") { leg -> leg.path.joinToString(",", "[", "]") { (lat, lng) -> String.format(Locale.ROOT, "[%.5f,%.5f]", lat, lng) } }
            // No legs in the answer: the whole way as one, up to the stop looked at.
            else -> "[" + line.path.joinToString(",", "[", "]") { (lat, lng) -> String.format(Locale.ROOT, "[%.5f,%.5f]", lat, lng) } + "]"
        }
        // Each stop's own colour by day and by night (and the way on from it), and on its pin its
        // trip's time, its address and its passenger's last name (on the page only).
        fun colorsOf(list: List<String>) = way.stops.indices.joinToString(",", "[", "]") { "\"" + list[it % list.size] + "\"" }
        fun texts(of: (MapWay.Stop) -> String?) = way.stops.joinToString(",", "[", "]") { s -> of(s)?.let(::jsonString) ?: "null" }
        val times = way.stops.joinToString(",", "[", "]") { s -> s.time?.filter { it.isDigit() || it == ':' }?.let { "\"$it\"" } ?: "null" }
        return "{\"stops\":" + stops.joinToString(",", "[", "]") + ",\"focus\":" + way.at + ",\"legs\":" + paths +
            ",\"colors\":" + colorsOf(STOP_COLORS) + ",\"nightColors\":" + colorsOf(NIGHT_STOP_COLORS) + ",\"times\":" + times +
            ",\"labels\":" + texts { it.label } + ",\"names\":" + texts { it.name } + "}"
    }

    fun destroy() {
        main.removeCallbacks(hide)
        asking?.cancel()
        view.destroy()
    }

    private fun js(code: String) = view.evaluateJavascript(code, null)

    private fun fetch(lat: Double, lng: Double, stops: List<MapWay.Stop>): RouteLine? {
        mapmap(stops)?.let { mapmapKey ->
            val url = MapmapApi.routeUrl(lat, lng, stops) ?: return null
            val text = request(url, mapmapHeaders(mapmapKey), null) ?: return null
            return MapmapApi.parse(text).also { if (it == null) answered(HttpURLConnection.HTTP_OK) }
        }
        val body = RoutesApi.body(lat, lng, stops) ?: return null
        val text = request(RoutesApi.URL, googleHeaders(RoutesApi.FIELDS), body) ?: return null
        return RoutesApi.parse(text).also { if (it == null) answered(HttpURLConnection.HTTP_OK) }
    }

    private fun fetchMatrix(lat: Double, lng: Double, stops: List<MapWay.Stop>): Array<IntArray>? {
        mapmap(stops)?.let { mapmapKey ->
            val body = MapmapApi.matrixBody(lat, lng, stops) ?: return null
            val text = request(MapmapApi.MATRIX_URL, mapmapHeaders(mapmapKey), body) ?: return null
            return MapmapApi.parseMatrix(text, stops.size).also { if (it == null) answered(HttpURLConnection.HTTP_OK) }
        }
        val body = RoutesApi.matrixBody(lat, lng, stops) ?: return null
        val text = request(RoutesApi.MATRIX_URL, googleHeaders(RoutesApi.MATRIX_FIELDS), body) ?: return null
        return RoutesApi.parseMatrix(text, stops.size).also { if (it == null) answered(HttpURLConnection.HTTP_OK) }
    }

    private fun googleHeaders(fields: String) = mapOf(
        "X-Goog-Api-Key" to key,
        "X-Goog-FieldMask" to fields,
        // The same page address the map loads from, for a key restricted to it.
        "Referer" to BASE,
    )

    /** The key goes in a header, never in the address. */
    private fun mapmapHeaders(mapmapKey: String) = mapOf("Authorization" to "Bearer $mapmapKey")

    /**
     * The answer to [body] (POST), or to the address alone (GET, no [body]), or null (its HTTP
     * code, or 0 for none, goes to [routeAnswer]).
     */
    private fun request(url: String, headers: Map<String, String>, body: String?): String? = runCatching {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = if (body != null) "POST" else "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            headers.forEach { (name, value) -> conn.setRequestProperty(name, value) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                answered(code)
                return null
            }
            conn.inputStream.bufferedReader().use { it.readText() }.also { answered(null) }
        } finally {
            conn.disconnect()
        }
    }.onFailure { answered(0) }.getOrNull()

    private fun answered(code: Int?) {
        lastCode = code
        main.post { routeAnswer = code }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            main.post {
                ready = true
                if (trouble == Trouble.NO_SCRIPT) trouble = null
            }
        }

        @JavascriptInterface
        fun onTiles() {
            main.post {
                tiles = true
                if (trouble == Trouble.NO_TILES) trouble = null
            }
        }

        /** [kind]: "script" (Google's script did not come), "tiles" (no pictures yet) or "page" (an error, [detail]). */
        @JavascriptInterface
        fun onProblem(kind: String?, detail: String?) {
            val found = when (kind) {
                "script" -> Trouble.NO_SCRIPT
                "tiles" -> Trouble.NO_TILES
                else -> Trouble.PAGE
            }
            main.post {
                if (found == Trouble.NO_TILES && tiles) return@post
                trouble = found
                troubleDetail = detail?.take(DETAIL_CHARS)?.takeIf { it.isNotBlank() }
            }
        }

        @JavascriptInterface
        fun onAuthFailure() {
            main.post {
                refused = true
                ready = false
            }
        }
    }

    /** What stops the map. */
    enum class Trouble {
        /** Google's map script did not load (no internet, or blocked). */
        NO_SCRIPT,

        /** The map is made but its pictures have not come. */
        NO_TILES,

        /** An error in the page ([troubleDetail]). */
        PAGE,
    }

    /** The page's own words for a [Trouble.PAGE], short; never logged. */
    var troubleDetail by mutableStateOf<String?>(null)
        private set

    companion object {
        private const val DETAIL_CHARS = 80

        /** The page's address: a key restricted to websites must allow it and every page under it. */
        const val BASE = "https://nastastopp.app/"
        private const val PAGE = "route_map.html"

        /** The map stops being drawn this long after it has gone. */
        private const val HIDE_AFTER_MS = 100L

        /**
         * The next stops' way is asked for again at most this often, and only once the car has gone
         * [MOVED_M] (Google counts each request).
         */
        private const val REFRESH_MS = 4 * 60_000L
        private const val MOVED_M = 1_500.0

        /** A way asked for is used again for this long, from as near as [REUSE_M]. */
        private const val KNOWN_MS = 15 * 60_000L

        /** No way came for the next stops: asked again after this long, whether the car moved or not. */
        private const val RETRY_MS = 30_000L

        /** Google's "too many requests": a wait, not a refusal. */
        private const val TOO_MANY = 429
        private const val REUSE_M = 1_000.0

        /** Ways kept for orders tried again. */
        private const val KNOWN_WAYS = 12

        /** The stops of a way in their order, as one name. */
        fun keyOf(stops: List<MapWay.Stop>): String = stops.joinToString("|") { it.key }

        /** The stops' own colours (AppColors.wayStops), by day and by night, for the page. */
        private val STOP_COLORS = DayColors.wayStops.map { String.format(Locale.ROOT, "#%06X", it.toArgb() and 0xFFFFFF) }
        private val NIGHT_STOP_COLORS = DisplayColors.wayStops.map { String.format(Locale.ROOT, "#%06X", it.toArgb() and 0xFFFFFF) }

        /** The borders the page draws (tools/make-boundaries.py). */
        private const val BORDERS = "boundaries.json"

        /** [text] as a JSON string, safe inside the page's script. */
        fun jsonString(text: String): String = buildString {
            append('"')
            text.forEach { c ->
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '<' || c == '>' || c == '&' || c == '\u2028' || c == '\u2029' || c < ' ' -> append(String.format(Locale.ROOT, "\\u%04x", c.code))
                    else -> append(c)
                }
            }
            append('"')
        }
    }
}
