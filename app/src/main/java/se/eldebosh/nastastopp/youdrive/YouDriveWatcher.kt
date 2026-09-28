package se.eldebosh.nastastopp.youdrive

import android.annotation.SuppressLint
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
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true) // BankID / Region Värmland login
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                openExternally(request.url)

            override fun onPageFinished(view: WebView, url: String?) {
                handler.postDelayed({ readNow() }, PAGE_SETTLE_MS)
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

    fun reload() {
        lastReloadMs = System.currentTimeMillis()
        webView?.reload() ?: webView()
    }

    /** Clears the YouDrive login and everything the page stored, then shows the login again. */
    fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        webView?.clearCache(true)
        watch.reset()
        _state.value = State(status = if (webView != null) Status.LOADING else Status.OFF)
        webView?.loadUrl(URL)
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
        web.evaluateJavascript(READ_TEXT_JS) { result ->
            val text = runCatching { json.decodeFromString<String?>(result ?: "null") }.getOrNull().orEmpty()
            onPageText(text)
        }
    }

    /** Parses one reading of the page (also the entry point for tests). */
    fun onPageText(text: String, nowMs: Long = System.currentTimeMillis(), nowTime: LocalTime = LocalTime.now()) {
        val trips = TripWatch.tripsIn(text, extractor)
        val changes = watch.onReading(trips, nowTime.hour * 60 + nowTime.minute)
        val status = when {
            trips.isNotEmpty() -> Status.WATCHING
            text.isBlank() -> Status.LOADING
            TripWatch.looksLoggedOut(text) -> Status.LOGGED_OUT
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
        private const val READ_TEXT_JS = "(function(){return document.body ? document.body.innerText : '';})()"
        private const val READ_MS = 60_000L
        private const val FAST_READ_MS = 15_000L
        private const val PAGE_SETTLE_MS = 3_000L
        private const val MAX_PENDING = 30
        private const val OFFSCREEN_W = 1080
        private const val OFFSCREEN_H = 2200
    }
}
