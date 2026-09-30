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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.eldebosh.nastastopp.core.link.LinkMessage
import se.eldebosh.nastastopp.core.nav.RouteLine
import se.eldebosh.nastastopp.core.nav.RoutesApi
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

/**
 * The tablet's map of the way to the next stop: Google's map in a WebView of its own, with the
 * driver's own key (entered on the tablet, 208) and the route from Google's Routes API. Only the
 * vehicle's position and the next stop go to Google, and only while the driver has the map on
 * (phone Settings 209). No controls, no touch, no device location; nothing is stored or logged.
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

    /** The last route asked for, or null before the first answer. */
    var route by mutableStateOf<RouteLine?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var askedFor: String? = null
    private var askedAtMs = 0L
    private var asking: Job? = null

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
        // The map is only looked at: touches never reach it.
        setOnTouchListener { _, _ -> true }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

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

    /** Moves the vehicle on the map, and asks for the way again when the stop changed or a while has passed. */
    fun show(where: LinkMessage.Where) {
        js(String.format(Locale.ROOT, "setCar(%.6f,%.6f)", where.lat, where.lng))
        val destination = "${where.toLat},${where.toLng},${where.to}"
        val now = SystemClock.elapsedRealtime()
        if (asking?.isActive == true) return
        if (destination == askedFor && now - askedAtMs < REFRESH_MS) return
        askedFor = destination
        askedAtMs = now
        asking = scope.launch {
            val line = withContext(Dispatchers.IO) { fetch(where) } ?: return@launch
            route = line
            js("setRoute(" + line.path.joinToString(",", "[", "]") { (lat, lng) -> String.format(Locale.ROOT, "[%.5f,%.5f]", lat, lng) } + ")")
        }
    }

    fun destroy() {
        asking?.cancel()
        view.destroy()
    }

    private fun js(code: String) = view.evaluateJavascript(code, null)

    private fun fetch(where: LinkMessage.Where): RouteLine? = runCatching {
        val body = RoutesApi.body(where.lat, where.lng, where.toLat, where.toLng, where.to) ?: return null
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
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            RoutesApi.parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            main.post { ready = true }
        }

        @JavascriptInterface
        fun onAuthFailure() {
            main.post {
                refused = true
                ready = false
            }
        }
    }

    companion object {
        /** The page's address: a key restricted to websites must allow it and every page under it. */
        const val BASE = "https://nastastopp.app/"
        private const val PAGE = "route_map.html"

        /** The way is asked for again at most this often for the same stop (Google counts each request). */
        private const val REFRESH_MS = 4 * 60_000L
    }
}
