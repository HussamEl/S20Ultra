package se.eldebosh.nastastopp.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import se.eldebosh.nastastopp.core.nav.MapWay
import se.eldebosh.nastastopp.core.nav.RouteLine
import se.eldebosh.nastastopp.core.nav.RoutesApi
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

/**
 * The tablet's map of the way to the next stop: Google's map in a WebView of its own, with the
 * driver's own key (entered on the tablet, 208) and the route from Google's Routes API. Only the
 * tablet's own position (from [se.eldebosh.nastastopp.geo.TabletPosition]) and the stop go to
 * Google. No controls, no touch, and the page itself never gets the location; nothing is stored
 * or logged. The page grows and fades the map itself ([reveal], [conceal]): the WebView is never
 * scaled or faded from outside, so it is drawn the same on every device. What stops the map
 * ([trouble], [routeAnswer]) is shown under it, for the driver to see why.
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

    /** The way on the map: to the next stop, or to the trip the driver asked for ([focus]); null before an answer. */
    var route by mutableStateOf<RouteLine?>(null)
        private set

    /** Where the vehicle is (the tablet's own position) and where the next stop is. */
    private var vehicle by mutableStateOf<MapWay?>(null)

    /** The map knows where the vehicle is. */
    val located: Boolean get() = vehicle != null

    private val main = Handler(Looper.getMainLooper())
    private var askedFor: String? = null
    private var askedAtMs = 0L
    private var asking: Job? = null
    private var toNext: RouteLine? = null

    /** What the map shows the way to until [unfocus]: a [MapWay.Stop], or [NEXT]. */
    private var focused: Any? = null

    /** The page's last [reveal] or [conceal], given again when the page has loaded. */
    private var stage: String? = null

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
        // Always drawn under the display (the page shows and hides the map): the ground's colour
        // from the start, never a white flash before the page.
        setBackgroundColor(ground.toColorInt())
        // The map is only looked at: touches never reach it.
        setOnTouchListener { _, _ -> true }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onPageFinished(view: WebView, url: String?) {
                stage?.let { view.evaluateJavascript(it, null) }
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
        loadDataWithBaseURL(BASE, page, "text/html", "utf-8", null)
    }

    /** The map grows out of ([x], [y]), fractions of its size, over [ms]. */
    fun reveal(x: Float, y: Float, ms: Int) {
        stage = String.format(Locale.ROOT, "reveal(%.4f,%.4f,%d)", x, y, ms)
        js(stage!!)
    }

    /** The map goes back where it came from, over [ms]. */
    fun conceal(ms: Int) {
        stage = "conceal($ms)"
        js(stage!!)
    }

    /** Moves the vehicle on the map, and asks for the way again when the stop changed or a while has passed. */
    fun show(way: MapWay) {
        val first = vehicle == null
        vehicle = way
        js(String.format(Locale.ROOT, "setCar(%.6f,%.6f)", way.lat, way.lng))
        focused?.let { target ->
            // Asked for before the position came: the way now.
            if (first) ask(target)
            return
        }
        val destination = "${way.toLat},${way.toLng},${way.to}"
        val now = SystemClock.elapsedRealtime()
        if (asking?.isActive == true) return
        if (destination == askedFor && now - askedAtMs < REFRESH_MS) return
        askedFor = destination
        askedAtMs = now
        asking = scope.launch {
            val line = withContext(Dispatchers.IO) { fetch(way) } ?: return@launch
            toNext = line
            if (focused == null) draw(line)
        }
    }

    /**
     * Shows the way from the vehicle to [to] (a trip pressed long), or to the next stop when [to] is
     * null, until [unfocus]; as soon as the vehicle's position is known.
     */
    fun focus(to: MapWay.Stop?) {
        val target = to ?: NEXT
        focused = target
        if (to == null && toNext != null) {
            draw(toNext!!)
            return
        }
        route = null
        js("setRoute([])")
        asking?.cancel()
        askedFor = null
        ask(target)
    }

    private fun ask(target: Any) {
        val from = vehicle ?: return
        val stop = target as? MapWay.Stop
        val to = if (stop == null) from else from.copy(toLat = stop.lat, toLng = stop.lng, to = stop.address)
        asking = scope.launch {
            val line = withContext(Dispatchers.IO) { fetch(to) } ?: return@launch
            if (stop == null) toNext = line
            if (focused == target) draw(line)
        }
    }

    /** Back to the way to the next stop. */
    fun unfocus() {
        if (focused == null) return
        focused = null
        toNext?.let { draw(it) } ?: run {
            route = null
            js("setRoute([])")
        }
    }

    private fun draw(line: RouteLine) {
        route = line
        js("setRoute(" + line.path.joinToString(",", "[", "]") { (lat, lng) -> String.format(Locale.ROOT, "[%.5f,%.5f]", lat, lng) } + ")")
    }

    fun destroy() {
        asking?.cancel()
        view.destroy()
    }

    private fun js(code: String) = view.evaluateJavascript(code, null)

    private fun fetch(way: MapWay): RouteLine? = runCatching {
        val body = RoutesApi.body(way.lat, way.lng, way.toLat, way.toLng, way.to) ?: return null
        val conn = URI(RoutesApi.URL).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Goog-Api-Key", key)
            conn.setRequestProperty("X-Goog-FieldMask", RoutesApi.FIELDS)
            // The same page address the map loads from, for a key restricted to it.
            conn.setRequestProperty("Referer", BASE)
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                answered(code)
                return null
            }
            RoutesApi.parse(conn.inputStream.bufferedReader().use { it.readText() }).also { answered(if (it == null) code else null) }
        } finally {
            conn.disconnect()
        }
    }.onFailure { answered(0) }.getOrNull()

    private fun answered(code: Int?) {
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
        private val NEXT = Any()

        /** The way is asked for again at most this often for the same stop (Google counts each request). */
        private const val REFRESH_MS = 4 * 60_000L
    }
}
