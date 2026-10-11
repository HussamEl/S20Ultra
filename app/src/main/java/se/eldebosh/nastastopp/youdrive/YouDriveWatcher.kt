package se.eldebosh.nastastopp.youdrive

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.youdrive.AutoSignIn
import se.eldebosh.nastastopp.core.youdrive.BrowserIdentity
import se.eldebosh.nastastopp.core.youdrive.SignInScript
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
    private val login: YouDriveLogin,
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
        /** Why the page could not be opened (network / HTTP / certificate), shown to the driver. */
        val problem: String? = null,
    )

    private val appContext = context.applicationContext
    private val wrapper = MutableContextWrapper(appContext)
    private val handler = Handler(Looper.getMainLooper())
    private val watch = TripWatch()
    private val autoSignIn = AutoSignIn()
    private val json = Json { isLenient = true }
    private var webView: WebView? = null
    private var nextChangeId = 1L
    private var lastReloadMs = 0L
    private var fastReads = false
    private var loadedHidden = false
    private var noTripsSinceMs: Long? = null

    /** Set by YouDrive's window: puts a new page on screen after the old one had to be thrown away. */
    var onPageReplaced: (() -> Unit)? = null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The page has history to go back to (the phone's Back key goes back inside the page first). */
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val loop = object : Runnable {
        override fun run() {
            // Reloaded every 5 minutes, only in the background: never while the driver is looking
            // at or typing in the page.
            if (!fastReads && System.currentTimeMillis() - lastReloadMs >= RELOAD_MS) reload() else readNow()
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
            setSupportMultipleWindows(false)
            // Identify like Chrome on Android (same engine), not as a browser inside an app.
            userAgentString = BrowserIdentity.chromeUserAgent(WebSettings.getDefaultUserAgent(appContext))
            // Behave like Chrome: the page's own viewport and 100 % text (not the system font scale).
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100
            setGeolocationEnabled(false) // the page never gets the phone's position
        }
        // Client hints like Chrome's too (the "Android WebView" brand becomes "Google Chrome").
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val meta = WebSettingsCompat.getUserAgentMetadata(web.settings)
            val brands = meta.brandVersionList.map { b ->
                if (b.brand != BrowserIdentity.WEBVIEW_BRAND) b
                else UserAgentMetadata.BrandVersion.Builder(b).setBrand(BrowserIdentity.CHROME_BRAND).build()
            }
            WebSettingsCompat.setUserAgentMetadata(web.settings, UserAgentMetadata.Builder(meta).setBrandVersionList(brands).build())
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true) // BankID / Region Värmland login
        }
        web.webViewClient = PageClient()
        web.webChromeClient = object : WebChromeClient() {
            // The page's own script messages are never shown to the driver nor written to the
            // system log (debug builds only, for troubleshooting).
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    DebugLog.d { "youdrive script: ${message.message().take(120)} (${message.sourceId().substringAfterLast('/')}:${message.lineNumber()})" }
                }
                return true
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
        webView = web
        if (wrapper.baseContext is Activity) {
            // Shown in YouDrive's window: load once the page is on screen, at the phone's real size.
            web.post { if (web.url == null) load(web) }
        } else {
            // Watching in the background: give the page a size so it lays out off screen. It is
            // loaded again when it is shown, because a page laid out off screen may not draw.
            web.measure(View.MeasureSpec.makeMeasureSpec(OFFSCREEN_W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(OFFSCREEN_H, View.MeasureSpec.EXACTLY))
            web.layout(0, 0, OFFSCREEN_W, OFFSCREEN_H)
            loadedHidden = true
            load(web)
        }
        if (_state.value.status == Status.OFF) _state.value = _state.value.copy(status = Status.LOADING)
        return web
    }

    /**
     * Page events: links, errors, a stopped renderer. (onRenderProcessGone is implemented below;
     * the lint check also flags the Kotlin super-constructor call `WebViewClient()` itself.)
     */
    @SuppressLint("MissingOnRenderProcessGone")
    private inner class PageClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            openExternally(request.url)

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (_state.value.problem != null) _state.value = _state.value.copy(problem = null) // old problems go
        }

        override fun onPageFinished(view: WebView, url: String?) {
            _canGoBack.value = view.canGoBack()
            handler.postDelayed({ readNow() }, PAGE_QUICK_MS) // early, to repair a hidden login form
            handler.postDelayed({ readNow() }, PAGE_SETTLE_MS)
        }

        // YouDrive is a single-page app: its screens change the history without new pages.
        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            _canGoBack.value = view.canGoBack()
        }

        // Only when the page itself cannot be opened (no network, server down). Errors of
        // the page's own requests come and go and are not shown (the page handles them).
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) problem("${error.errorCode} ${error.description} (${request.url.host})")
            else DebugLog.d { "youdrive request error ${error.errorCode} ${request.url.host}" }
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (response.statusCode < 400) return
            if (request.isForMainFrame) problem("HTTP ${response.statusCode} (${request.url.host})")
            else DebugLog.d { "youdrive HTTP ${response.statusCode} ${request.url.host}" }
        }

        // The page's renderer was stopped (low memory or a crash): a new page is opened instead
        // of the whole app closing. The login in the page storage is kept.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            DebugLog.w { "youdrive renderer gone (crash=${detail.didCrash()})" }
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            if (webView !== view) return true
            webView = null
            loadedHidden = false
            _canGoBack.value = false
            handler.post {
                val shown = onPageReplaced
                when {
                    wrapper.baseContext is Activity && shown != null -> shown()
                    settings.current.youDriveWatch -> webView()
                }
            }
            return true
        }

        @SuppressLint("WebViewClientOnReceivedSslError") // shown to the driver, then cancelled as usual
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            problem("certificate ${error.primaryError} (${error.url.toUri().host})")
            handler.cancel()
        }
    }

    private fun load(web: WebView) {
        lastReloadMs = System.currentTimeMillis()
        web.loadUrl(URL)
    }

    /**
     * The page for YouDrive's window [activity] (dialogs and pickers need an activity context).
     * The caller adds it to its layout; a page loaded off screen is loaded again once shown.
     */
    fun attach(activity: Context): WebView {
        wrapper.baseContext = activity
        val web = webView()
        (web.parent as? ViewGroup)?.removeView(web)
        if (loadedHidden) {
            loadedHidden = false
            web.post { reload() }
        }
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
        noTripsSinceMs = null
        watch.reset()
        _state.value = _state.value.copy(status = Status.OFF)
    }

    /** BankID and other app links open outside; web pages stay in the WebView. */
    private fun openExternally(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return false
        // Only while YouDrive's window is shown: the page in the background never opens other apps.
        if (wrapper.baseContext !is Activity) return true
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
        webView?.let { load(it) } ?: webView()
    }

    fun reload() {
        lastReloadMs = System.currentTimeMillis()
        if (wrapper.baseContext !is Activity) loadedHidden = true
        webView?.reload() ?: webView()
    }

    /**
     * Logs out completely: clears what the page stored (also its session storage, which lives as
     * long as the page), cookies, web storage and cache, and throws the page away. [then] runs
     * when done, so the window can show a brand-new page.
     */
    fun logout(then: () -> Unit = {}) {
        autoSignIn.pause() // the driver logged out: do not sign straight back in
        val web = webView
        val finish = {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            web?.clearCache(true)
            (web?.parent as? ViewGroup)?.removeView(web)
            web?.destroy()
            if (webView === web) webView = null
            loadedHidden = false
            noTripsSinceMs = null
            _canGoBack.value = false
            watch.reset()
            _state.value = State(status = Status.OFF)
            then()
        }
        if (web == null) finish() else web.evaluateJavascript(CLEAR_STORAGE_JS) { finish() }
    }

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
            // {"t": visible text, "c": each trip card's text, "p": a login form (password field) is
            // shown, "f": what was repaired}
            val page = runCatching { json.decodeFromString<PageReading>(json.decodeFromString<String?>(result ?: "null") ?: "{}") }.getOrNull()
            if (!page?.f.isNullOrBlank()) DebugLog.d { "login form moved back on screen (${page.f.trim().take(60)})" }
            val loginForm = page?.p ?: false
            onPageText(page?.t.orEmpty(), cards = page?.c.orEmpty(), loginForm = loginForm)
            signInIfWanted(web, loginForm)
        }
    }

    /**
     * The automatic sign-in (a setting): when the login form shows, press Login, after filling it
     * from the login saved on this phone if YouDrive's own "Remember me" left it empty. Only on
     * YouDrive's own https page; the script checks that again inside the page.
     */
    private fun signInIfWanted(web: WebView, loginForm: Boolean) {
        if (!autoSignIn.onReading(loginForm, settings.current.youDriveAutoSignIn, System.currentTimeMillis())) {
            if (autoSignIn.gaveUp && loginForm) problem(appContext.getString(R.string.youdrive_sign_in_failed))
            return
        }
        val url = web.url?.toUri() ?: return
        if (url.scheme != "https" || url.host != AutoSignIn.HOST) return
        val saved = login.load()
        web.evaluateJavascript(SignInScript.build(saved?.username, saved?.password)) { result ->
            DebugLog.d { "automatic sign-in: $result" } // a status word only, never a value
        }
    }

    /** Parses one reading of the page (also the entry point for tests). */
    fun onPageText(
        text: String,
        nowMs: Long = System.currentTimeMillis(),
        nowTime: LocalTime = LocalTime.now(),
        loginForm: Boolean = TripWatch.looksLoggedOut(text),
        cards: List<String> = emptyList(),
    ) {
        val trips = TripWatch.tripsIn(text, extractor, cards)
        val before = watch.baseline.orEmpty()
        var changes = watch.onReading(trips, nowTime.hour * 60 + nowTime.minute)
        // Another view or day was opened in YouDrive, or a new day's list came: the new list is
        // simply taken over; nothing is announced as added or cancelled.
        if (changes.isNotEmpty() && TripWatch.isNewList(before, trips, changes.size)) changes = emptyList()
        val current = _state.value
        // A page that is reloading shows its login or an empty screen for a moment: the status
        // only changes when there are still no trips a little later (no flicker in the notification).
        if (trips.isNotEmpty()) noTripsSinceMs = null else if (noTripsSinceMs == null) noTripsSinceMs = nowMs
        val settling = trips.isEmpty() && current.status == Status.WATCHING && nowMs - (noTripsSinceMs ?: nowMs) < STATUS_GRACE_MS
        val status = when {
            trips.isNotEmpty() || settling -> Status.WATCHING
            loginForm -> Status.LOGGED_OUT
            text.isBlank() -> Status.LOADING
            else -> Status.NO_TRIPS
        }
        val shownTrips = if (trips.isNotEmpty()) watch.baseline ?: trips else current.trips
        val pending = changes.map { PendingChange(nextChangeId++, it, nowMs) }
        _state.value = current.copy(
            problem = if (trips.isNotEmpty()) null else current.problem,
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

        /**
         * Reads the visible text and whether a password field (the login form) is shown. If the
         * login form is drawn off screen or hidden by a stuck slide animation (seen in the app's
         * WebView, never in Chrome), its containers are put back in place.
         *
         * It also returns each trip card's own text ("c"). A card is found without knowing the
         * page's markup: from each visible kind label ("Pick-up", "Drop-off", "Pull-out",
         * "Pull-in") it climbs to the largest element that holds no other kind label.
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
              var cards = [];
              if (document.body) {
                var KIND = /^(pull[- ]?out|pick[- ]?up|drop[- ]?off|pull[- ]?in)$/i;
                var labels = [];
                var all = document.body.getElementsByTagName('*');
                for (var i = 0; i < all.length; i++) {
                  var e = all[i], own = '';
                  for (var k = 0; k < e.childNodes.length; k++) if (e.childNodes[k].nodeType === 3) own += e.childNodes[k].nodeValue;
                  if (KIND.test(own.trim()) && e.getClientRects().length > 0) labels.push(e);
                }
                var holds = function (el) { var n = 0; for (var j = 0; j < labels.length; j++) if (el.contains(labels[j])) n++; return n; };
                for (var m = 0; m < labels.length; m++) {
                  var c = labels[m];
                  while (c.parentElement && c.parentElement !== document.body && holds(c.parentElement) === 1) c = c.parentElement;
                  cards.push(c.innerText);
                }
              }
              return JSON.stringify({ t: document.body ? document.body.innerText : '', c: cards, p: !!pw && pw.offsetParent !== null, f: fixed });
            })()
        """.trimIndent()
        private const val CLEAR_STORAGE_JS = "(function(){try{sessionStorage.clear();localStorage.clear();}catch(e){}return 1;})()"
        private const val READ_MS = 60_000L
        private const val RELOAD_MS = 5 * 60_000L
        private const val FAST_READ_MS = 15_000L
        private const val PAGE_SETTLE_MS = 3_000L
        private const val PAGE_QUICK_MS = 1_200L
        private const val MAX_PENDING = 30
        private const val STATUS_GRACE_MS = 20_000L
        private const val OFFSCREEN_W = 1080
        private const val OFFSCREEN_H = 2200
    }
}

/** What the page reading script returns. */
@Serializable
private data class PageReading(val t: String = "", val c: List<String> = emptyList(), val p: Boolean = false, val f: String = "")
