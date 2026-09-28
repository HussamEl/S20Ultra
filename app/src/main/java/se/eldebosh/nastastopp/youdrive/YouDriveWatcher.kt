package se.eldebosh.nastastopp.youdrive

import android.annotation.SuppressLint
import android.app.Activity
import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import kotlinx.serialization.Serializable
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.youdrive.TripChange
import se.eldebosh.nastastopp.core.youdrive.TripWatch
import se.eldebosh.nastastopp.core.youdrive.WatchedTrip
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.util.DebugLog
import java.time.LocalTime

/**
 * Keeps the driver's YouDrive page (https://youdrive.regionvarmland.se) open in a WebView,
 * reads its visible text every minute and alerts when a trip is added or cancelled.
 *
 * Privacy: the page text is only held in memory while it is parsed; only times and addresses
 * are kept (the same extractor as for screenshots drops names and everything else). The
 * YouDrive login (cookies, page storage) stays in app-private storage and can be cleared.
 * All calls on the main thread.
 */
class YouDriveWatcher(
    context: Context,
    private val settings: SettingsStore,
    private val extractor: AddressExtractor,
    private val alerts: (List<PendingChange>) -> Unit,
) {
    enum class Status { OFF, LOADING, WATCHING, NO_TRIPS, LOGGED_OUT }

    data class PendingChange(val id: Long, val change: TripChange, val atMs: Long)

    data class State(
        val status: Status = Status.OFF,
        val trips: List<WatchedTrip> = emptyList(),
        val lastReadMs: Long? = null,
        /** Changes the driver has not handled yet (newest last). */
        val changes: List<PendingChange> = emptyList(),
        /** Last problem of the page (load / HTTP / certificate / script error), shown to the driver. */
        val problem: String? = null,
    )

    private val appContext = context.applicationContext
    private val wrapper = MutableContextWrapper(appContext)
    private val handler = Handler(Looper.getMainLooper())
    private val watch = TripWatch()
    private val json = Json { isLenient = true }
    private var webView: WebView? = null
    private var nextChangeId = 1L
    private var lastReloadMs = 0L
    private var fastReads = false

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The page has history to go back to (the phone's Back key goes back inside the page first). */
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val loop = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val reloadMs = settings.current.youDriveReloadMin * 60_000L
            if (reloadMs > 0 && now - lastReloadMs >= reloadMs) reload() else readNow()
            handler.postDelayed(this, if (fastReads) FAST_READ_MS else READ_MS)
        }
    }

    // ------------------------------------------------------------------------------------------
    // WebView

    /** The page's WebView, created on first use (it keeps running when the screen is closed). */
    @SuppressLint("SetJavaScriptEnabled")
    fun webView(): WebView {
        webView?.let { return it }
        val web = WebView(wrapper)
        web.settings.apply {
            javaScriptEnabled = true // YouDrive is a JavaScript app
            domStorageEnabled = true // it keeps its login in page storage
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false // YouDrive's own alert sound
            setSupportMultipleWindows(false)
            // Behave like Chrome: the page's own viewport and 100 % text (not the system font scale).
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true) // BankID / Region Värmland login
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                openExternally(request.url)

            override fun onPageFinished(view: WebView, url: String?) {
                _canGoBack.value = view.canGoBack()
                handler.postDelayed({ readNow() }, PAGE_QUICK_MS) // early, to repair a hidden login form
                handler.postDelayed({ readNow() }, PAGE_SETTLE_MS)
            }

            // YouDrive is a single-page app: its screens change the history without new pages.
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                _canGoBack.value = view.canGoBack()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame || isYouDrive(request.url)) {
                    problem("${error.errorCode} ${error.description} (${request.url.host})")
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (response.statusCode >= 400 && (request.isForMainFrame || request.url.host == API_HOST)) {
                    problem("HTTP ${response.statusCode} ${request.url.host}${request.url.path.orEmpty().take(40)}")
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError") // shown to the driver, then cancelled as usual
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                problem("certificate ${error.primaryError} (${error.url.toUri().host})")
                handler.cancel()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val source = message.sourceId().substringAfterLast('/')
                // appTag.js / cordova.js are the app-only files YouDrive also requests in Chrome (harmless).
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR && source !in HARMLESS_SCRIPTS) {
                    problem("script: ${message.message().take(120)} ($source:${message.lineNumber()})")
                }
                return true // never written to the system log
            }

            // Page dialogs need an activity: without one (page in the background) they are dismissed.
            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean =
                if (wrapper.baseContext is Activity) super.onJsAlert(view, url, message, result) else result.cancel().let { true }

            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean =
                if (wrapper.baseContext is Activity) super.onJsConfirm(view, url, message, result) else result.cancel().let { true }

            override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean =
                if (wrapper.baseContext is Activity) super.onJsPrompt(view, url, message, defaultValue, result) else result.cancel().let { true }

            // The app never shares the phone's position with the page.
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }
        }
        // A size, so the page lays out while it is not on screen.
        web.measure(View.MeasureSpec.makeMeasureSpec(OFFSCREEN_W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(OFFSCREEN_H, View.MeasureSpec.EXACTLY))
        web.layout(0, 0, OFFSCREEN_W, OFFSCREEN_H)
        web.loadUrl(URL)
        lastReloadMs = System.currentTimeMillis()
        webView = web
        if (_state.value.status == Status.OFF) _state.value = _state.value.copy(status = Status.LOADING)
        return web
    }

    /** Shows the page inside [activity] (dialogs and pickers need an activity context). */
    fun attach(activity: Context): WebView {
        wrapper.baseContext = activity
        val web = webView()
        (web.parent as? ViewGroup)?.removeView(web)
        fastReads = true
        restartLoop()
        handler.postDelayed({ readNow() }, PAGE_QUICK_MS)
        return web
    }

    /**
     * The screen was closed: while watching, the page keeps running in the background; otherwise
     * it is closed (the login is kept and the page opens again next time).
     */
    fun detach() {
        webView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        wrapper.baseContext = appContext
        fastReads = false
        if (settings.current.youDriveWatch) restartLoop() else close()
    }

    private fun close() {
        stopLoop()
        webView?.destroy()
        webView = null
        _canGoBack.value = false
        watch.reset()
        _state.value = _state.value.copy(status = Status.OFF)
    }

    /** BankID and other app links open outside; web pages stay in the WebView. */
    private fun openExternally(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return false
        val intent = if (scheme == "intent") {
            runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()?.apply {
                // Only let the link open apps the normal way (no explicit component).
                component = null
                selector = null
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        } ?: return true
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            wrapper.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            DebugLog.d { "no app for $scheme link" }
        } catch (e: SecurityException) {
            DebugLog.w(e) { "link blocked" }
        }
        return true
    }

    /** Back inside the page (e.g. from its Settings screen to the login). */
    fun goBack() {
        val web = webView ?: return
        if (web.canGoBack()) web.goBack()
        _canGoBack.value = web.canGoBack()
    }

    /** Opens YouDrive's start page (login, or the trips when logged in). */
    fun openStart() {
        lastReloadMs = System.currentTimeMillis()
        webView?.loadUrl(URL) ?: webView()
    }

    fun reload() {
        lastReloadMs = System.currentTimeMillis()
        webView?.reload() ?: webView()
    }

    /**
     * Clears the YouDrive login and everything the page stored (also its session storage, which
     * lives as long as the page), then shows the start page again.
     */
    fun logout() {
        val web = webView
        val restart = {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
            web?.clearCache(true)
            web?.loadUrl(URL)
            web?.clearHistory()
            _canGoBack.value = false
        }
        watch.reset()
        _state.value = State(status = if (web != null) Status.LOADING else Status.OFF)
        if (web == null) restart() else web.evaluateJavascript(CLEAR_STORAGE_JS) { restart() }
    }

    private fun isYouDrive(uri: Uri) = uri.host == API_HOST || uri.host == URL.toUri().host

    private fun problem(text: String) {
        _state.value = _state.value.copy(problem = text)
    }

    // ------------------------------------------------------------------------------------------
    // Watching

    /** Starts (or stops) watching according to the setting; called by the service and the UI. */
    fun sync() {
        if (settings.current.youDriveWatch) {
            webView()
            restartLoop()
        } else if (!fastReads) {
            close()
        }
    }

    private fun restartLoop() {
        handler.removeCallbacks(loop)
        handler.postDelayed(loop, if (fastReads) FAST_READ_MS else READ_MS)
    }

    private fun stopLoop() = handler.removeCallbacks(loop)

    /** Reads the page's visible text now. */
    fun readNow() {
        val web = webView ?: return
        web.evaluateJavascript(READ_PAGE_JS) { result ->
            // {"t": visible text, "p": a login form (password field) is shown, "f": what was repaired}
            val page = runCatching { json.decodeFromString<PageReading>(json.decodeFromString<String?>(result ?: "null") ?: "{}") }.getOrNull()
            if (!page?.f.isNullOrBlank()) problem("login form was off screen (${page.f.trim().take(60)}), moved back")
            onPageText(page?.t.orEmpty(), loginForm = page?.p ?: false)
        }
    }

    /** Parses one reading of the page (also the entry point for tests). */
    fun onPageText(
        text: String,
        nowMs: Long = System.currentTimeMillis(),
        nowTime: LocalTime = LocalTime.now(),
        loginForm: Boolean = TripWatch.looksLoggedOut(text),
    ) {
        val trips = TripWatch.tripsIn(text, extractor)
        val changes = watch.onReading(trips, nowTime.hour * 60 + nowTime.minute)
        val status = when {
            trips.isNotEmpty() -> Status.WATCHING
            loginForm -> Status.LOGGED_OUT
            text.isBlank() -> Status.LOADING
            else -> Status.NO_TRIPS
        }
        val current = _state.value
        val shownTrips = if (trips.isNotEmpty()) watch.baseline ?: trips else current.trips
        val pending = changes.map { PendingChange(nextChangeId++, it, nowMs) }
        _state.value = current.copy(
            status = status,
            trips = shownTrips,
            lastReadMs = nowMs,
            changes = (current.changes + pending).takeLast(MAX_PENDING),
        )
        if (pending.isNotEmpty() && settings.current.youDriveWatch) alerts(pending)
    }

    fun dismiss(id: Long) {
        _state.value = _state.value.copy(changes = _state.value.changes.filterNot { it.id == id })
    }

    fun dismissAll() {
        _state.value = _state.value.copy(changes = emptyList())
    }

    companion object {
        const val URL = "https://youdrive.regionvarmland.se/"
        private const val API_HOST = "youapi.regionvarmland.se"

        private val HARMLESS_SCRIPTS = setOf("appTag.js", "cordova.js")

        /**
         * Reads the visible text and whether a password field (the login form) is shown. If the
         * login form is drawn off screen or hidden by a stuck slide animation (seen in the app's
         * WebView, never in Chrome), its containers are put back in place.
         */
        private val READ_PAGE_JS = """
            (function(){
              var fixed = '';
              var pw = document.querySelector('input[type=password]');
              if (pw) {
                var r = pw.getBoundingClientRect();
                var off = r.width === 0 || r.right <= 0 || r.left >= innerWidth || r.bottom <= 0 || r.top >= innerHeight;
                for (var n = pw.parentElement; n && n !== document.body; n = n.parentElement) {
                  var cs = getComputedStyle(n);
                  var hidden = cs.visibility === 'hidden' || cs.opacity === '0';
                  if ((off && cs.transform !== 'none') || hidden) {
                    fixed += cs.transform + ' ';
                    n.style.transition = 'none';
                    n.style.transform = 'none';
                    n.style.visibility = 'visible';
                    n.style.opacity = '1';
                  }
                }
              }
              return JSON.stringify({ t: document.body ? document.body.innerText : '', p: !!pw && pw.offsetParent !== null, f: fixed });
            })()
        """.trimIndent()
        private const val CLEAR_STORAGE_JS = "(function(){try{sessionStorage.clear();localStorage.clear();}catch(e){}return 1;})()"
        private const val READ_MS = 60_000L
        private const val FAST_READ_MS = 15_000L
        private const val PAGE_SETTLE_MS = 3_000L
        private const val PAGE_QUICK_MS = 1_200L
        private const val MAX_PENDING = 30
        private const val OFFSCREEN_W = 1080
        private const val OFFSCREEN_H = 2200
    }
}

/** What the page reading script returns. */
@Serializable
private data class PageReading(val t: String = "", val p: Boolean = false, val f: String = "")
