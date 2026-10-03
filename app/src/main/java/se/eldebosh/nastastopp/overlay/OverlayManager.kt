package se.eldebosh.nastastopp.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.MainActivity
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.display.TimeStatus
import se.eldebosh.nastastopp.core.parse.TimeLevel
import se.eldebosh.nastastopp.core.parse.TripKind
import se.eldebosh.nastastopp.core.parse.TripTimes
import se.eldebosh.nastastopp.service.Notifications
import se.eldebosh.nastastopp.settings.AppSettings
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.ui.theme.AppColors
import se.eldebosh.nastastopp.ui.theme.AppTheme
import se.eldebosh.nastastopp.ui.theme.DayColors
import se.eldebosh.nastastopp.ui.theme.DayEffects
import se.eldebosh.nastastopp.util.LocaleHelper
import se.eldebosh.nastastopp.util.SystemIntents
import se.eldebosh.nastastopp.util.TimeLabels

/**
 * Optional floating panel over Google Maps (needs SYSTEM_ALERT_WINDOW), shown while a route is
 * active. One card of smoked glass that reads on any map or wallpaper (design: see [glass]):
 *
 *     ┌──────────────────────────────────────┐
 *     │ 13:14  1/11                  (–)  (×) │   clock, trip n/total, minimise, close
 *     │  ⎛45⎞ ┌────────────────────────────┐ │   the speed (km/h) in its own circle;
 *     │  ⎝  ⎠ │ Drottninggatan         (🔊) │ │   the street we are on (tap = say it; 🔊 =
 *     │       │ Centrum                    │ │   say each new street by itself: on / off)
 *     │       └────────────────────────────┘ │
 *     │ ┌──────────────────────────────────┐ │
 *     │ ▌ [14:33] Storgatan 14             │ │   the next trip: time, the stop's street (tap = say it),
 *     │ ▌ Anna Testsson         ● in 7 min │ │   passenger and on-time status
 *     │ ▌ Karlstad                         │ │   and town; the stripe: its kind's colour
 *     │ └──────────────────────────────────┘ │
 *     │ [⏮ Back] [═════════ ⏭ Next ════════] │   compact; Next is the bright one
 *     └──────────────────────────────────────┘
 *
 * It follows the UI language (Arabic: mirrored). Next: tap = "Nästa", long-press = repeat. Back:
 * undo the last "Nästa". The street bar: tap = say the street, long-press = repeat. The stop's
 * street: tap = say its street and number. "–" shrinks it to a small glass capsule with a
 * countdown to the trip and a chevron at each side for Back and Next (tap the capsule to
 * expand); "×" closes it, and it comes back from the notification
 * that appears, the Quick Settings tile or the app. Everything can be dragged; the position is
 * remembered and kept on screen.
 *
 * The phone's panel shows its own route ([RoutePanelSource]). A tablet can show the same panel
 * over its passenger display ([LinkPanelSource]): without the passenger's name and without the
 * street bar and speed, which stay on the phone; its Next, Back and Repeat go to the phone.
 *
 * @param wanted whether this panel belongs to the device's role and settings.
 * @param bubbleScale the capsule's size (the tablet's is twice the phone's, read from further away).
 * @param swellLastMinute in the trip's last minute the capsule swells to twice its size and blinks.
 */
class OverlayManager(
    private val context: Context,
    private val source: PanelSource,
    private val settings: SettingsStore,
    scope: CoroutineScope,
    private val bubbleScale: Float = 1f,
    private val swellLastMinute: Boolean = false,
    private val wanted: (AppSettings) -> Boolean,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
    private var root: View? = null
    private var views: Views? = null
    private var layoutKey: String? = null
    private var ui: Context = context
    private var reminderShown: Boolean? = null
    private var beat: ValueAnimator? = null
    private var swell = 1f
    private var swollen = false
    private var swelling: ValueAnimator? = null
    private var blink: ValueAnimator? = null

    /** The panel's colours and effects for the current look; set each time the panel is built. */
    private var pc = PanelColors(DayColors)
    private var fx = DayEffects

    /** The capsule's views, updated every second while it is shown (the full panel draws itself). */
    private class Views {
        var bubble: LinearLayout? = null
        var bubbleHours: TextView? = null
        var bubbleColon: TextView? = null
        var bubbleMinutes: TextView? = null
        var bubbleSeconds: TextView? = null
        var bubbleBack: View? = null
        var bubbleNext: View? = null
        var bubbleRing: GradientDrawable? = null
    }

    /** The full panel's window: its lifecycle (for Compose) and whether the driver gave it a height. */
    private var owner: PanelOwner? = null
    private val sized = mutableStateOf(false)

    private val tick = object : Runnable {
        override fun run() {
            updateTimes()
            handler.postDelayed(this, 1_000L - System.currentTimeMillis() % 1_000L + 20L)
        }
    }

    /** Screens the panel must never cover (the passenger display), while they are shown. */
    private val suppressedBy = HashSet<String>()

    init {
        scope.launch {
            combine(source.changes, settings.state) { _, _ -> }.collect { refresh() }
        }
    }

    val canShow: Boolean get() = Settings.canDrawOverlays(context)


    /** [key]'s screen is shown ([on]) or gone: the panel stays away while any such screen is shown. */
    fun suppress(key: String, on: Boolean) {
        val changed = if (on) suppressedBy.add(key) else suppressedBy.remove(key)
        if (changed) refresh()
    }

    /** Shows/updates/hides the panel according to the route, the settings and the permission. */
    fun refresh() {
        val s = settings.current
        // The other role's panel: nothing to show here, and its reminder is left alone.
        if (!wanted(s)) {
            hide()
            return
        }
        val routeOn = source.trip() != null && canShow
        setReminder(routeOn && s.overlayHidden)
        if (!routeOn || s.overlayHidden || suppressedBy.isNotEmpty()) {
            source.street?.want(WANT_KEY, false)
            hide()
            return
        }
        source.street?.want(WANT_KEY, !s.overlayMinimized)
        val key = "${s.overlayMinimized}|${s.uiLanguage}|${s.showRefNumbers}|${AppTheme.isNight(s.appearance, systemNight())}"
        if (root == null || key != layoutKey) {
            hide()
            show(s.overlayMinimized)
            layoutKey = key
        }
        bind()
    }

    private fun systemNight() =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).roundToInt()

    // ------------------------------------------------------------------------------------------
    // Content

    private fun bind() = updateTimes()

    /** The capsule's countdown (every second). */
    private fun updateTimes() {
        val v = views ?: return
        val t = source.trip() ?: return
        val now = LocalTime.now()
        val until = TripTimes.minutesUntil(t.time, now.hour * 60 + now.minute)
        // The capsule counts down to the trip's time like the display's clock (hours, minutes,
        // small seconds), in its colours: green, orange within five minutes (beating at its
        // minute), red once late (beating from five minutes). Without a time: the trip's number.
        val status = TimeStatus.of(until)
        val countdown = until?.let { pc.countdown(status) }
        val parts = until?.let { TimeStatus.countdown(it * 60 - now.second) }
        val ink = countdown ?: pc.text
        v.bubbleHours?.apply {
            text = parts?.hours.orEmpty()
            visibility = if (parts?.hours == null) View.GONE else View.VISIBLE
            setTextColor(ink)
        }
        v.bubbleColon?.apply {
            visibility = if (parts?.hours == null) View.GONE else View.VISIBLE
            setTextColor(ink)
        }
        v.bubbleMinutes?.apply {
            text = parts?.minutes ?: progress(t.done, t.left)
            setTextColor(ink)
        }
        v.bubbleSeconds?.apply {
            text = parts?.seconds.orEmpty()
            visibility = if (parts == null) View.GONE else View.VISIBLE
            setTextColor(ink)
        }
        v.bubbleRing?.setStroke(dp(BUBBLE_RING_DP * bubbleScale * swell), countdown ?: pc.edgeLight)
        beat(v, until != null && status.beating)
        val secondsLeft = until?.let { it * 60 - now.second }
        swellFor(v, swellLastMinute && secondsLeft != null && secondsLeft in 0..60)
    }

    /** The capsule swells to twice its size and blinks through the trip's last minute, then settles. */
    private fun swellFor(v: Views, on: Boolean) {
        if (on == swollen) return
        swollen = on
        swelling?.cancel()
        swelling = ValueAnimator.ofFloat(swell, if (on) SWELL else 1f).apply {
            duration = SWELL_MS
            interpolator = if (on) OvershootInterpolator(1.4f) else DecelerateInterpolator()
            addUpdateListener {
                swell = it.animatedValue as Float
                sizeBubble(v, bubbleScale * swell)
            }
            start()
        }
        blink?.cancel()
        blink = null
        v.bubble?.alpha = 1f
        if (on) {
            blink = ValueAnimator.ofFloat(1f, BLINK_LOW).apply {
                duration = BLINK_MS
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { v.bubble?.alpha = it.animatedValue as Float }
                start()
            }
        }
    }

    /** Lays the capsule out at [f] times its base size: its height, digits, ring and rounding, and the chevrons beside it. */
    private fun sizeBubble(v: Views, f: Float) {
        val bubble = v.bubble ?: return
        bubble.background = bubbleBackground(BUBBLE_H_DP * f, v.bubbleRing)
        bubble.setPadding(dp(14f * f), 0, dp(14f * f), 0)
        bubble.layoutParams = bubble.layoutParams?.apply { height = dp(BUBBLE_H_DP * f) }
        fun TextView.size(sp: Float) = setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(sp * f).toFloat())
        v.bubbleHours?.size(BUBBLE_MIN_SP * BUBBLE_HOUR_SHARE)
        v.bubbleColon?.size(BUBBLE_MIN_SP * BUBBLE_HOUR_SHARE)
        v.bubbleMinutes?.size(BUBBLE_MIN_SP)
        v.bubbleSeconds?.apply {
            size(BUBBLE_MIN_SP * BUBBLE_SECOND_SHARE)
            (layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(3f * f)
        }
        for (chevron in listOfNotNull(v.bubbleBack, v.bubbleNext)) {
            chevron.layoutParams = chevron.layoutParams?.apply {
                width = dp(CHEVRON_W_DP * f)
                height = dp(BUBBLE_H_DP * f)
            }
        }
        v.bubbleRing?.cornerRadius = dp(BUBBLE_H_DP * f / 2).toFloat()
        bubble.requestLayout()
    }

    /** The capsule's glass, the deeper glass under the countdown (so its colours read on any map), and its ring. */
    private fun bubbleBackground(heightDp: Float, ring: GradientDrawable?): Drawable =
        LayerDrawable(listOfNotNull(glass(heightDp / 2), rounded(pc.well, heightDp / 2), ring).toTypedArray())

    /** The capsule's ring beats while the trip is due or long late. */
    private fun beat(v: Views, on: Boolean) {
        if (on == (beat != null)) return
        if (!on) {
            beat?.cancel()
            beat = null
            v.bubbleRing?.alpha = 255
            return
        }
        beat = ValueAnimator.ofInt(255, (255 * BEAT_LOW).roundToInt()).apply {
            duration = BEAT_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                v.bubbleRing?.alpha = it.animatedValue as Int
            }
            start()
        }
    }

    private fun progress(done: Int, remaining: Int) = "${done + 1}/${done + remaining}"

    // ------------------------------------------------------------------------------------------
    // Views

    // Gravity.LEFT on purpose: overlay x/y are absolute screen coordinates, also in RTL.
    @SuppressLint("RtlHardcoded")
    private fun show(minimized: Boolean) {
        ui = LocaleHelper.wrap(context, settings.current.uiLanguage)
        pc = PanelColors(AppTheme.colorsFor(settings.current.appearance, systemNight()))
        fx = AppTheme.effectsFor(settings.current.appearance, systemNight())
        val (x, y) = settings.overlayPosition() ?: (dp(16f) to dp(200f))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            this.x = x
            this.y = y
        }
        val v = Views()
        if (!minimized) sizePanel(lp)
        val content = if (minimized) buildBubble(lp, v) else buildPanel(lp)
        content.layoutDirection = ui.resources.configuration.layoutDirection
        content.addOnLayoutChangeListener { view, l, t, r, b, ol, ot, orr, ob ->
            if (r - l != orr - ol || b - t != ob - ot) keepOnScreen(view, lp)
            logRefBoundsSoon()
        }
        try {
            wm?.addView(content, lp)
            root = content
            views = v
            handler.removeCallbacks(tick)
            tick.run()
        } catch (_: Exception) {
            root = null
            views = null
        }
    }

    /**
     * Minimised: a small glass capsule counting down to the next trip like the display's clock:
     * the hours (from an hour away) medium, the minutes large and the seconds small at their
     * right with nothing between them ("7 42", "+3 10" once late); the digits and the ring in the
     * display clock's colours. A thin, faint chevron floats at each side: back and on to the next
     * trip, as in the full panel (a long press on the next one repeats the announcement). Tap the
     * capsule = expand.
     */
    // TouchHandler calls performClick on a tap, so clicks stay accessible.
    @SuppressLint("ClickableViewAccessibility")
    private fun buildBubble(lp: WindowManager.LayoutParams, v: Views): View {
        val ring = GradientDrawable().apply {
            cornerRadius = dp(BUBBLE_H_DP / 2).toFloat()
            setColor(pc.clear)
            setStroke(dp(BUBBLE_RING_DP * bubbleScale), pc.edgeLight)
        }
        val hours = text(BUBBLE_MIN_SP, pc.text, bold = true).apply { includeFontPadding = false }
        val colon = text(BUBBLE_MIN_SP, pc.text, bold = true).apply {
            text = ":"
            includeFontPadding = false
        }
        val minutes = text(BUBBLE_MIN_SP, pc.text, bold = true).apply { includeFontPadding = false }
        val seconds = text(BUBBLE_MIN_SP, pc.text).apply {
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }
        // The digits stand on one baseline.
        val count = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(hours, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(colon, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(minutes, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(seconds, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        val bubble = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            elevation = dp(fx.panelShadow.value).toFloat()
            contentDescription = ui.getString(R.string.overlay_expand_desc)
            addView(count, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        val rtl = ui.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val back = View(ui).apply {
            background = Chevron(pointsRight = rtl)
            contentDescription = ui.getString(R.string.overlay_back)
        }
        val next = View(ui).apply {
            background = Chevron(pointsRight = !rtl)
            contentDescription = ui.getString(R.string.overlay_next)
        }
        val row = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(back, LinearLayout.LayoutParams(dp(CHEVRON_W_DP), dp(BUBBLE_H_DP)))
            addView(bubble, LinearLayout.LayoutParams(WRAP, dp(BUBBLE_H_DP)))
            addView(next, LinearLayout.LayoutParams(dp(CHEVRON_W_DP), dp(BUBBLE_H_DP)))
        }
        val frame = FrameLayout(ui).apply {
            shadowRoom()
            addView(row, FrameLayout.LayoutParams(WRAP, WRAP))
        }
        bubble.ref(17)
        back.ref(12)
        next.ref(14)
        bubble.setOnClickListener { settings.update { it.copy(overlayMinimized = false) } }
        bubble.setOnTouchListener(TouchHandler(lp, frame, onLongPress = null, press = true))
        back.setOnClickListener { if (!source.back()) toast(R.string.overlay_no_previous) }
        back.setOnTouchListener(TouchHandler(lp, frame, onLongPress = null, press = true))
        next.setOnClickListener { source.next() }
        next.setOnTouchListener(TouchHandler(lp, frame, onLongPress = { source.repeat() }, press = true))
        v.bubble = bubble
        v.bubbleHours = hours
        v.bubbleColon = colon
        v.bubbleMinutes = minutes
        v.bubbleSeconds = seconds
        v.bubbleBack = back
        v.bubbleNext = next
        v.bubbleRing = ring
        sizeBubble(v, bubbleScale * swell)
        return frame
    }

    /**
     * A chevron floating beside the capsule (‹ or ›): a thin rounded stroke in the panel's text
     * colour, a little see-through, with a soft dark halo so it reads on a light map as well as on
     * a dark one.
     */
    private inner class Chevron(private val pointsRight: Boolean) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = pc.text
            alpha = (255 * CHEVRON_ALPHA).roundToInt()
            setShadowLayer(dp(2f).toFloat(), 0f, dp(1f).toFloat(), pc.halo)
        }
        private val path = Path()

        override fun draw(canvas: Canvas) {
            val b = bounds
            val tall = b.height() * CHEVRON_TALL
            val wide = tall * 0.5f
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val dir = if (pointsRight) 1f else -1f
            paint.strokeWidth = b.height() * CHEVRON_STROKE
            path.reset()
            path.moveTo(cx - dir * wide / 2, cy - tall / 2)
            path.lineTo(cx + dir * wide / 2, cy)
            path.lineTo(cx - dir * wide / 2, cy + tall / 2)
            canvas.drawPath(path, paint)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    @SuppressLint("ClickableViewAccessibility")
    /**
     * The full panel ([FloatingPanel], drawn with Compose in the passenger display's look) in a
     * window the driver moves by its bar and sizes from any edge or corner ([PanelFrame]). Its size
     * is kept (and opens so next time); until he sizes it, it is as tall as what it shows.
     */
    private fun buildPanel(lp: WindowManager.LayoutParams): View {
        val owner = PanelOwner().also { owner = it }
        val frame = PanelFrame(lp)
        val actions = object : PanelActions {
            override fun minimize() = settings.update { it.copy(overlayMinimized = true) }

            override fun close() = hideByUser()

            override fun openApp() = this@OverlayManager.openApp()

            override fun toggleSayStreet() = settings.update { it.copy(sayStreetChanges = !it.sayStreetChanges) }

            override fun toast(text: Int) = this@OverlayManager.toast(text)

            override fun barAt(bounds: androidx.compose.ui.geometry.Rect) {
                frame.bar.set(bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.right.roundToInt(), bounds.bottom.roundToInt())
            }
        }
        val compose = ComposeView(ui).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val s by settings.state.collectAsState()
                FloatingPanel(source, actions, s.sayStreetChanges, fill = sized.value)
            }
        }
        frame.setPadding(dp(EDGE_DP), dp(EDGE_DP), dp(EDGE_DP), dp(EDGE_DP))
        frame.addView(compose, FrameLayout.LayoutParams(MATCH, if (lp.height > 0) MATCH else WRAP))
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        frame.setViewTreeViewModelStoreOwner(owner)
        return frame
    }

    /** The window as big as the driver left it (within the screen), else its usual width and as tall as its content. */
    private fun sizePanel(lp: WindowManager.LayoutParams) {
        val dm = context.resources.displayMetrics
        val kept = settings.overlaySize()
        lp.width = kept?.first?.coerceIn(dp(PANEL_MIN_W_DP), dm.widthPixels) ?: dp(PANEL_W_DP).coerceAtMost(dm.widthPixels)
        lp.height = kept?.second?.coerceIn(dp(PANEL_MIN_H_DP), dm.heightPixels) ?: WRAP
        sized.value = kept != null
    }

    /**
     * The full panel's window: a transparent margin ([EDGE_DP]) all round where a finger sizes it
     * like any window (a side edge: the width only; the top or bottom: the height only; a corner:
     * both; the opposite side stays), and the panel's bar ([bar]), where a finger that moves drags
     * the window. Taps everywhere else go to the panel.
     */
    private inner class PanelFrame(private val lp: WindowManager.LayoutParams) : FrameLayout(ui) {
        val bar = android.graphics.Rect()
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var mode = NONE
        private var sideX = 0
        private var sideY = 0
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    lastX = e.rawX
                    lastY = e.rawY
                    val edge = dp(EDGE_DP)
                    sideX = if (e.x < edge) -1 else if (e.x > width - edge) 1 else 0
                    sideY = if (e.y < edge) -1 else if (e.y > height - edge) 1 else 0
                    mode = when {
                        sideX != 0 || sideY != 0 -> RESIZE
                        bar.contains(e.x.roundToInt(), e.y.roundToInt()) -> MAYBE_MOVE
                        else -> NONE
                    }
                    return mode == RESIZE
                }
                MotionEvent.ACTION_MOVE -> if (mode == MAYBE_MOVE && (abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop)) {
                    mode = MOVE
                    lastX = e.rawX
                    lastY = e.rawY
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = NONE
            }
            return false
        }

        // A drag of the window's edge or bar: only moves and sizes the window, never a click.
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> return mode == RESIZE
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - lastX
                    val dy = e.rawY - lastY
                    lastX = e.rawX
                    lastY = e.rawY
                    when (mode) {
                        RESIZE -> resizeBy(dx.roundToInt(), dy.roundToInt())
                        MOVE -> moveBy(dx.roundToInt(), dy.roundToInt())
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (mode == RESIZE) settings.setOverlaySize(lp.width, lp.height)
                    if (mode == RESIZE || mode == MOVE) {
                        settings.setOverlayPosition(lp.x, lp.y)
                        keepOnScreen(this, lp)
                        logRefBoundsSoon()
                    }
                    mode = NONE
                }
            }
            return true
        }

        private fun resizeBy(dx: Int, dy: Int) {
            val dm = context.resources.displayMetrics
            val w = if (lp.width > 0) lp.width else width
            val h = if (lp.height > 0) lp.height else height
            val newW = if (sideX != 0) (w + sideX * dx).coerceIn(dp(PANEL_MIN_W_DP), dm.widthPixels) else w
            val newH = if (sideY != 0) (h + sideY * dy).coerceIn(dp(PANEL_MIN_H_DP), dm.heightPixels) else h
            if (sideX < 0) lp.x += w - newW
            if (sideY < 0) lp.y += h - newH
            lp.width = newW
            lp.height = newH
            if (!sized.value) {
                sized.value = true
                getChildAt(0)?.layoutParams = LayoutParams(MATCH, MATCH)
            }
            runCatching { wm?.updateViewLayout(this, lp) }
        }

        private fun moveBy(dx: Int, dy: Int) {
            lp.x += dx
            lp.y += dy
            runCatching { wm?.updateViewLayout(this, lp) }
        }
    }

    private fun text(sp: Float, color: Int, bold: Boolean = false) = TextView(ui).apply {
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * FONT_SCALE)
        gravity = Gravity.CENTER
        if (bold) paint.isFakeBoldText = true
    }

    /** [v] sp (with the panel's font scale) in pixels. */
    private fun sp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v * FONT_SCALE, context.resources.displayMetrics).roundToInt()

    private fun rounded(fill: Int, radiusDp: Float) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(fill)
    }

    /**
     * The smoked glass: a translucent fill (the map shows through), a soft sheen fading down from
     * the top, and two hairline edges (dark outside, light inside), so the card stands out on a
     * light map as well as on a dark one. The parts with text sit on deeper glass ([PanelColors.well]).
     */
    private fun glass(radiusDp: Float): Drawable {
        val r = dp(radiusDp).toFloat()
        val inset = dp(1f)
        val body = GradientDrawable().apply {
            cornerRadius = r
            setColor(pc.glass)
            setStroke(inset, pc.edgeDark)
        }
        val sheen = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(pc.sheen, pc.clear, pc.clear)).apply { cornerRadius = r }
        val edge = GradientDrawable().apply {
            cornerRadius = r - inset
            setColor(pc.clear)
            setStroke(inset, pc.edgeLight)
        }
        return LayerDrawable(arrayOf(body, sheen, edge)).apply {
            setLayerInset(1, inset, inset, inset, inset)
            setLayerInset(2, inset, inset, inset, inset)
        }
    }

    /**
     * Small translucent reference number in the top-start (or bottom-end) corner of [this]
     * (setting). Texts get a little start padding so the number does not cover them.
     */
    private fun View.ref(n: Int, bottomEnd: Boolean = false, padText: Boolean = true) {
        id = REF_IDS[n - 1] // resource-id "ref_<n>" for device tests, also with the numbers hidden
        if (!settings.current.showRefNumbers) return
        if (padText && this is TextView) setPaddingRelative(maxOf(paddingStart, dp(REF_TEXT_PAD_DP)), paddingTop, paddingEnd, paddingBottom)
        val rtl = ui.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val badge = RefBadge(n.toString(), rtl, bottomEnd)
        addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> badge.setBounds(0, 0, r - l, b - t) }
        overlay.add(badge)
    }

    private inner class RefBadge(private val text: String, private val rtl: Boolean, private val bottomEnd: Boolean) : Drawable() {
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pc.onRefPill
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 8f, context.resources.displayMetrics)
            isFakeBoldText = true
        }
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pc.refPill }

        override fun draw(canvas: Canvas) {
            val pad = dp(3f).toFloat()
            val w = textPaint.measureText(text) + 2 * pad
            val h = textPaint.textSize + pad
            val inset = dp(2f).toFloat()
            val atLeft = rtl == bottomEnd // top-start or bottom-end, in either direction
            val x = if (atLeft) inset else bounds.width() - w - inset
            val y = if (bottomEnd) bounds.height() - h - inset else inset
            canvas.drawRoundRect(RectF(x, y, x + w, y + h), pad, pad, bgPaint)
            canvas.drawText(text, x + pad, y + h - pad * 0.9f - textPaint.descent() / 2, textPaint)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // ------------------------------------------------------------------------------------------
    // Actions

    private fun toast(text: Int) = Toast.makeText(ui, text, Toast.LENGTH_SHORT).show()

    private fun hideByUser() {
        settings.update { it.copy(overlayHidden = true) }
        val explain = LocaleHelper.explanationContext(ui, settings.current.uiLanguage, settings.current.explanationsArabic)
        Toast.makeText(ui, explain.getString(R.string.overlay_hidden_toast), Toast.LENGTH_LONG).show()
    }

    private fun openApp() {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    /** While the driver has closed the panel during a route, a notification brings it back. */
    private fun setReminder(show: Boolean) {
        if (reminderShown == show) return
        reminderShown = show
        if (show) {
            Notifications.post(context, Notifications.ID_OVERLAY_HIDDEN, Notifications.buildOverlayHidden(LocaleHelper.wrap(context, settings.current.uiLanguage)))
        } else {
            Notifications.cancel(context, Notifications.ID_OVERLAY_HIDDEN)
        }
    }

    /**
     * For device tests: UI Automator does not see overlay windows, so with
     * `adb shell setprop log.tag.NastaStoppRefs DEBUG` every layout or move of the panel logs its
     * numbered parts' screen bounds ("ref_5=[812,640][980,808] …"). Ids and bounds only, never
     * stop data; silent unless switched on.
     */
    private fun logRefBoundsSoon() {
        if (!Log.isLoggable(REF_LOG_TAG, Log.DEBUG)) return
        handler.removeCallbacks(logRefBounds)
        handler.postDelayed(logRefBounds, 150) // after the window has moved
    }

    private val logRefBounds = Runnable {
        val panel = root ?: return@Runnable
        val at = IntArray(2)
        val parts = REF_IDS.withIndex().mapNotNull { (i, id) ->
            val v = panel.findViewById<View>(id)?.takeIf { it.isShown } ?: return@mapNotNull null
            v.getLocationOnScreen(at)
            "ref_${i + 1}=[${at[0]},${at[1]}][${at[0] + v.width},${at[1] + v.height}]"
        }
        Log.d(REF_LOG_TAG, parts.joinToString(" "))
    }

    /**
     * Room around the card for its shadow. The window is a little larger than the card and takes
     * the taps in that margin too, so it is kept small, mostly below the card where the shadow falls.
     */
    private fun View.shadowRoom() = setPadding(dp(SHADOW_SIDE_DP), dp(SHADOW_TOP_DP), dp(SHADOW_SIDE_DP), dp(SHADOW_BOTTOM_DP))

    /**
     * The highest the window may go: the card stays below the status bar and an app's top bar
     * (the route screen's bar, Maps' turn banner), so it never covers their buttons.
     */
    private fun minY(): Int = dp(TOP_CLEAR_DP) - dp(SHADOW_TOP_DP)

    /** Moves the window back inside the screen, below the top bars (after a size change or a drag). */
    private fun keepOnScreen(view: View, lp: WindowManager.LayoutParams) {
        if (root != view) return
        val dm = context.resources.displayMetrics
        val nx = lp.x.coerceIn(0, (dm.widthPixels - view.width).coerceAtLeast(0))
        val ny = lp.y.coerceIn(minY(), (dm.heightPixels - view.height).coerceAtLeast(minY()))
        if (nx == lp.x && ny == lp.y) return
        lp.x = nx
        lp.y = ny
        runCatching { wm?.updateViewLayout(view, lp) }
        settings.setOverlayPosition(nx, ny)
    }

    fun hide() {
        handler.removeCallbacks(tick)
        beat?.cancel()
        beat = null
        swelling?.cancel()
        swelling = null
        blink?.cancel()
        blink = null
        swell = 1f
        swollen = false
        val v = root
        root = null
        views = null
        layoutKey = null
        if (v != null) runCatching { wm?.removeView(v) }
        owner?.destroy()
        owner = null
    }

    /**
     * Distinguishes drag, tap and long-press. Dragging moves the whole overlay window ([window]);
     * a tap calls performClick (→ the view's click listener), a long-press runs [onLongPress].
     */
    private inner class TouchHandler(
        private val lp: WindowManager.LayoutParams,
        private val window: View,
        private val onLongPress: (() -> Unit)?,
        /** A button: it shrinks a little while pressed (AppEffects.panelPressedScale). */
        private val press: Boolean = false,
    ) : View.OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private val longPress = Runnable {
            longPressed = true
            window.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        }

        private fun pressed(v: View, on: Boolean) {
            v.isPressed = on
            if (!press) return
            val scale = if (on) fx.panelPressedScale else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(fx.panelPressMs).start()
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    longPressed = false
                    pressed(v, true)
                    if (onLongPress != null) handler.postDelayed(longPress, longPressMs)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        pressed(v, false)
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging && !longPressed) {
                        lp.x = (startX + dx).roundToInt()
                        lp.y = (startY + dy).roundToInt().coerceAtLeast(minY())
                        runCatching { wm?.updateViewLayout(window, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    pressed(v, false)
                    if (dragging) {
                        settings.setOverlayPosition(lp.x, lp.y)
                        keepOnScreen(window, lp)
                        logRefBoundsSoon()
                    } else if (!longPressed) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        v.performClick() // → the view's click listener
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    pressed(v, false)
                }
            }
            return true
        }
    }

    companion object {
        private const val WANT_KEY = "overlay"
        /** The full panel: its usual width, the least it is sized to, and the margin where a finger sizes it. */
        private const val PANEL_W_DP = 360f
        private const val PANEL_MIN_W_DP = 240f
        private const val PANEL_MIN_H_DP = 160f
        private const val EDGE_DP = 14f

        /** What a finger on the full panel's window does: nothing yet, size it, maybe move it, move it. */
        private const val NONE = 0
        private const val RESIZE = 1
        private const val MAYBE_MOVE = 2
        private const val MOVE = 3

        /** The capsule's ring and dot beat between full and this, as the display clock's colon does. */
        private const val BEAT_LOW = 0.2f
        private const val BEAT_MS = 700L

        /**
         * The capsule: its minutes' size (the hours and seconds are shares of it, as on the
         * display's clock), the chevrons beside it (their width, the stroke's height and weight as
         * shares of the capsule's height, and how see-through), its ring, and how it swells and
         * blinks in the trip's last minute.
         */
        private const val BUBBLE_MIN_SP = 18f
        private const val BUBBLE_HOUR_SHARE = 0.62f
        private const val BUBBLE_SECOND_SHARE = 0.55f
        private const val CHEVRON_W_DP = 22f
        private const val CHEVRON_TALL = 0.32f
        private const val CHEVRON_STROKE = 0.05f
        private const val CHEVRON_ALPHA = 0.6f
        private const val BUBBLE_RING_DP = 2.5f
        private const val SWELL = 2f
        private const val SWELL_MS = 500L
        private const val BLINK_LOW = 0.35f
        private const val BLINK_MS = 450L

        private const val BUBBLE_H_DP = 44f

        /** Room around the card for its shadow: little above and beside it, more below. */
        private const val SHADOW_TOP_DP = 2f
        private const val SHADOW_SIDE_DP = 6f
        private const val SHADOW_BOTTOM_DP = 12f

        /** The card's top stays at least this far from the screen's top (status bar + top bar). */
        private const val TOP_CLEAR_DP = 100f
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val REF_TEXT_PAD_DP = 17f
        private const val REF_LOG_TAG = "NastaStoppRefs"
        private val REF_IDS = intArrayOf(
            R.id.ref_1, R.id.ref_2, R.id.ref_3, R.id.ref_4, R.id.ref_5, R.id.ref_6, R.id.ref_7, R.id.ref_8, R.id.ref_9,
            R.id.ref_10, R.id.ref_11, R.id.ref_12, R.id.ref_13, R.id.ref_14, R.id.ref_15, R.id.ref_16, R.id.ref_17,
            R.id.ref_18, R.id.ref_19,
        )

        /** Scale of every panel text (a compact panel over the map). */
        private const val FONT_SCALE = 0.9f
    }
}

/**
 * The floating panel's colours as ARGB ints (the panel is made of Views), taken from the app's
 * colour roles ([AppColors.panel] and the yellow accent), so it has no colours of its own.
 */
private class PanelColors(c: AppColors) {
    private val p = c.panel
    val glass = p.glass.toArgb()
    val sheen = p.sheen.toArgb()
    val well = p.well.toArgb()
    val control = p.control.toArgb()
    val edgeLight = p.edgeLight.toArgb()
    val edgeDark = p.edgeDark.toArgb()
    val text = p.text.toArgb()
    val muted = p.textMuted.toArgb()
    val next = c.accent.toArgb()
    val onNext = c.onAccent.toArgb()
    val refPill = c.refPill.toArgb()
    val onRefPill = c.onRefPill.toArgb()
    val clear = Color.Transparent.toArgb()

    /** The soft dark halo behind what floats on the map without glass (the capsule's chevrons). */
    val halo = p.halo.toArgb()

    /** The trip's stripe and kind chip, in its YouDrive card colour. */
    fun trip(kind: TripKind?): Int = p.trip(kind).toArgb()

    fun status(level: TimeLevel): Int = p.status(level).toArgb()

    fun countdown(status: TimeStatus): Int = p.status(status).toArgb()
}
