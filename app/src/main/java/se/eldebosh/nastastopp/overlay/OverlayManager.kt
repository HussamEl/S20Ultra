package se.eldebosh.nastastopp.overlay

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

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
 * countdown to the trip (tap to expand); "×" closes it, and it comes back from the notification
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

    /** Views that are updated while the panel (or bubble) is shown. */
    private class Views {
        var street: TextView? = null
        var sayStreet: ImageView? = null
        var speed: TextView? = null
        var area: TextView? = null
        var back: View? = null
        var clock: TextView? = null
        var progress: TextView? = null
        var time: TextView? = null
        var name: TextView? = null
        var status: TextView? = null
        var statusDot: GradientDrawable? = null
        var stripe: GradientDrawable? = null
        var address: TextView? = null
        var town: TextView? = null
        var bubble: LinearLayout? = null
        var bubbleDotView: View? = null
        var bubbleMain: TextView? = null
        var bubbleSub: TextView? = null
        var bubbleRing: GradientDrawable? = null
        var bubbleDot: GradientDrawable? = null
    }

    private val tick = object : Runnable {
        override fun run() {
            updateTimes()
            handler.postDelayed(this, 1_000L - System.currentTimeMillis() % 1_000L + 20L)
        }
    }

    init {
        scope.launch {
            combine(source.changes, settings.state) { _, _ -> }.collect { refresh() }
        }
    }

    val canShow: Boolean get() = Settings.canDrawOverlays(context)

    /** Screens the panel must never cover (the passenger display), while they are shown. */
    private val suppressedBy = HashSet<String>()

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

    private fun bind() {
        val v = views ?: return
        val t = source.trip() ?: return
        val info = source.street?.state?.value
        // Without location the street bar shows the next stop's area instead.
        v.street?.text = info?.street ?: info?.area ?: t.area.orEmpty()
        v.area?.apply {
            val area = info?.area.takeIf { info?.street != null }
            text = area.orEmpty()
            visibility = if (area.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        // At the first stop Back does nothing: dim only its icon and caption, so the button's glass
        // stays as it is over the map and its reference number stays readable.
        val backAlpha = if (t.done == 0) 0.35f else 1f
        (v.back as? ViewGroup)?.let { b -> for (i in 0 until b.childCount) b.getChildAt(i).alpha = backAlpha }
        v.progress?.text = progress(t.done, t.left)
        // The speaker on the street bar: is the street said by itself when it changes?
        v.sayStreet?.apply {
            val on = settings.current.sayStreetChanges
            setImageResource(if (on) R.drawable.ic_speaker else R.drawable.ic_speaker_off)
            contentDescription = ui.getString(if (on) R.string.overlay_say_street_on else R.string.overlay_say_street_off)
            imageAlpha = if (on) 255 else 170
        }
        // The stop's street and number beside the time; its town below.
        v.address?.text = t.street
        v.town?.apply {
            text = t.town.orEmpty()
            visibility = if (t.town.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        // The passenger's first + last name level with the time, as on YouDrive's card: only on
        // the phone, whose panel is the driver's own.
        v.name?.apply {
            text = t.name.orEmpty()
            visibility = if (t.name == null) View.GONE else View.VISIBLE
        }
        // The trip's kind in YouDrive's card colour, as a stripe (no word: the time says enough).
        v.stripe?.setColor(pc.trip(t.kind))
        v.time?.apply {
            text = t.time.orEmpty()
            visibility = if (t.time == null) View.GONE else View.VISIBLE
        }
        updateTimes()
    }

    /** Clock, speed, on-time status and the capsule's countdown (every second). */
    private fun updateTimes() {
        val v = views ?: return
        val t = source.trip() ?: return
        val now = LocalTime.now()
        v.clock?.text = now.format(clockFormat)
        // The vehicle's speed: just the number (km/h), in Western digits in every language like the
        // clock; "–" until a recent position has one. Only with location allowed.
        v.speed?.apply {
            text = source.street?.speedNow()?.toString() ?: "–"
            visibility = if (SystemIntents.hasLocation(context)) View.VISIBLE else View.GONE
        }
        val until = TripTimes.minutesUntil(t.time, now.hour * 60 + now.minute)
        val color = until?.let { pc.status(TripTimes.level(it)) }
        v.status?.apply {
            text = until?.let { TimeLabels.until(ui, it) }.orEmpty()
            visibility = if (until == null) View.GONE else View.VISIBLE
            if (color != null) setTextColor(color)
        }
        if (color != null) v.statusDot?.setColor(color)
        // The capsule counts down to the trip's time, in the display clock's colours: green, orange
        // within five minutes (beating at its minute), red once late (beating from five minutes).
        val status = TimeStatus.of(until)
        val countdown = until?.let { pc.countdown(status) }
        v.bubbleMain?.apply {
            text = if (until != null) TimeStatus.countdown(until * 60 - now.second) else progress(t.done, t.left)
            setTextColor(countdown ?: pc.text)
        }
        v.bubbleSub?.apply {
            text = t.time ?: ""
            visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        v.bubbleRing?.setStroke(dp(BUBBLE_RING_DP * bubbleScale * swell), countdown ?: pc.edgeLight)
        v.bubbleDot?.setColor(countdown ?: pc.muted)
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

    /** Lays the capsule out at [f] times its base size: its height, text, dot, ring and rounding. */
    private fun sizeBubble(v: Views, f: Float) {
        val bubble = v.bubble ?: return
        bubble.background = bubbleBackground(BUBBLE_H_DP * f, v.bubbleRing)
        bubble.setPadding(dp(14f * f), 0, dp(16f * f), 0)
        bubble.layoutParams = bubble.layoutParams?.apply { height = dp(BUBBLE_H_DP * f) }
        v.bubbleMain?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(BUBBLE_MAIN_SP * f).toFloat())
            (layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(8f * f)
        }
        v.bubbleSub?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(BUBBLE_SUB_SP * f).toFloat())
            (layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(6f * f)
        }
        v.bubbleDotView?.layoutParams = v.bubbleDotView?.layoutParams?.apply {
            width = dp(10f * f)
            height = dp(10f * f)
        }
        v.bubbleRing?.cornerRadius = dp(BUBBLE_H_DP * f / 2).toFloat()
        bubble.requestLayout()
    }

    /** The capsule's glass, the deeper glass under the countdown (so its colours read on any map), and its ring. */
    private fun bubbleBackground(heightDp: Float, ring: GradientDrawable?): Drawable =
        LayerDrawable(listOfNotNull(glass(heightDp / 2), rounded(pc.well, heightDp / 2), ring).toTypedArray())

    /** The capsule's ring and dot beat while the trip is due or long late. */
    private fun beat(v: Views, on: Boolean) {
        if (on == (beat != null)) return
        if (!on) {
            beat?.cancel()
            beat = null
            v.bubbleRing?.alpha = 255
            v.bubbleDot?.alpha = 255
            return
        }
        beat = ValueAnimator.ofInt(255, (255 * BEAT_LOW).roundToInt()).apply {
            duration = BEAT_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val a = it.animatedValue as Int
                v.bubbleRing?.alpha = a
                v.bubbleDot?.alpha = a
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
        val content = if (minimized) buildBubble(lp, v) else buildPanel(lp, v)
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
     * Minimised: a small glass capsule counting down to the next trip ("7:42", "+3:10" once late),
     * with the trip's time beside it; the countdown, its ring and dot in the display clock's
     * colours. Tap = expand.
     */
    // TouchHandler calls performClick on a tap, so clicks stay accessible.
    @SuppressLint("ClickableViewAccessibility")
    private fun buildBubble(lp: WindowManager.LayoutParams, v: Views): View {
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(pc.muted)
        }
        val ring = GradientDrawable().apply {
            cornerRadius = dp(BUBBLE_H_DP / 2).toFloat()
            setColor(pc.clear)
            setStroke(dp(BUBBLE_RING_DP * bubbleScale), pc.edgeLight)
        }
        val main = text(BUBBLE_MAIN_SP, pc.text, bold = true).apply { fontFeatureSettings = "tnum" }
        val sub = text(BUBBLE_SUB_SP, pc.muted, bold = true)
        val dotView = View(ui).apply { background = dot }
        val bubble = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(fx.panelShadow.value).toFloat()
            contentDescription = ui.getString(R.string.overlay_expand_desc)
            addView(dotView, LinearLayout.LayoutParams(dp(10f), dp(10f)))
            addView(main, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(sub, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        val frame = FrameLayout(ui).apply {
            shadowRoom()
            addView(bubble, FrameLayout.LayoutParams(WRAP, dp(BUBBLE_H_DP)))
        }
        bubble.ref(17)
        bubble.setOnClickListener { settings.update { it.copy(overlayMinimized = false) } }
        bubble.setOnTouchListener(TouchHandler(lp, frame, onLongPress = null, press = true))
        v.bubble = bubble
        v.bubbleDotView = dotView
        v.bubbleMain = main
        v.bubbleSub = sub
        v.bubbleRing = ring
        v.bubbleDot = dot
        sizeBubble(v, bubbleScale * swell)
        return frame
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel(lp: WindowManager.LayoutParams, v: Views): View {
        val card = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(CARD_RADIUS_DP)
            elevation = dp(fx.panelShadow.value).toFloat()
            setPadding(dp(8f), dp(6f), dp(8f), dp(8f))
        }
        val window = FrameLayout(ui).apply {
            shadowRoom()
            addView(card, FrameLayout.LayoutParams(dp(PANEL_W_DP), WRAP))
        }
        fun drag(view: View, press: Boolean = false, onLongPress: (() -> Unit)? = null) =
            view.setOnTouchListener(TouchHandler(lp, window, onLongPress, press))

        // Header: clock, trip n/total, minimise and close.
        val clockView = text(17f, pc.text, bold = true)
        val progressView = text(12f, pc.text, bold = true).apply {
            background = rounded(pc.control, 20f)
            setPadding(dp(8f), dp(1f), dp(8f), dp(1f))
        }
        val minimize = roundControl("–", R.string.overlay_minimize_desc)
        val close = roundControl("×", R.string.overlay_close_desc)
        val header = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(6f), 0, 0, dp(6f))
            addView(clockView)
            addView(progressView, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8f) })
            addView(View(ui), LinearLayout.LayoutParams(0, 1, 1f))
            addView(minimize, LinearLayout.LayoutParams(dp(CONTROL_DP), dp(CONTROL_DP)))
            addView(close, LinearLayout.LayoutParams(dp(CONTROL_DP), dp(CONTROL_DP)).apply { marginStart = dp(8f) })
        }

        // The street we are on: a wide bar, so the name stays on one line at a large size
        // (shrinking to fit, never broken inside a word).
        val streetView = text(22f, pc.text, bold = true).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            // Aligned with the panel's side (the right in Arabic), whatever the script.
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setAutoSizeTextTypeUniformWithConfiguration(sp(12f), sp(22f), 1, TypedValue.COMPLEX_UNIT_PX)
        }
        val areaView = text(12f, pc.muted).apply {
            gravity = Gravity.START
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            // Aligned with the panel's side (the right in Arabic), whatever the script.
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        val streetText = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            addView(streetView, LinearLayout.LayoutParams(MATCH, dp(32f)))
            addView(areaView, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        // The quick switch: say the street by itself whenever it changes (on) or only on a tap (off).
        val sayStreet = ImageView(ui).apply {
            imageTintList = ColorStateList.valueOf(pc.text)
            val pad = dp(7f)
            setPadding(pad, pad, pad, pad)
            background = oval(pc.control)
        }
        // Tap anywhere else on the bar = say the street.
        val streetBar = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(pc.well, WELL_RADIUS_DP)
            setPaddingRelative(dp(12f), dp(7f), dp(10f), dp(7f))
            contentDescription = ui.getString(R.string.overlay_street_desc)
            addView(streetText, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(sayStreet, LinearLayout.LayoutParams(dp(36f), dp(36f)).apply { marginStart = dp(6f) })
        }
        // The vehicle's speed: a big circle of its own beside the street, the number only.
        val speedView = text(26f, pc.text, bold = true).apply {
            background = LayerDrawable(arrayOf(oval(pc.well), GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pc.clear)
                setStroke(dp(3f), pc.next)
            }))
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(sp(14f), sp(26f), 1, TypedValue.COMPLEX_UNIT_PX)
            setPadding(dp(6f), 0, dp(6f), 0)
            contentDescription = ui.getString(R.string.overlay_speed_desc)
            text = "–"
        }
        val streetRow = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(speedView, LinearLayout.LayoutParams(dp(SPEED_DP), dp(SPEED_DP)))
            addView(streetBar, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(8f) })
        }

        // The next trip, with a stripe in its kind's colour (the time says the rest, so no
        // "Pick-up" word): the time with the stop's street and number beside it, large (tap =
        // say them); the passenger and how late or early; the town. Tap elsewhere = open the app.
        val stripe = GradientDrawable().apply {
            cornerRadius = dp(2f).toFloat()
            setColor(pc.trip(null))
        }
        val timeView = text(17f, pc.onNext, bold = true).apply {
            background = rounded(pc.next, 9f)
            setPadding(dp(8f), dp(1f), dp(8f), dp(1f))
        }
        val addressView = text(STOP_STREET_SP, pc.text, bold = true).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            // Aligned with the panel's side (the right in Arabic), whatever the script.
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setAutoSizeTextTypeUniformWithConfiguration(sp(13f), sp(STOP_STREET_SP), 1, TypedValue.COMPLEX_UNIT_PX)
            contentDescription = ui.getString(R.string.overlay_stop_street_desc)
        }
        val nameView = text(15f, pc.text, bold = true).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        val statusDot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize(dp(8f), dp(8f))
            setColor(pc.muted)
        }
        val statusView = text(13f, pc.text, bold = true).apply {
            maxLines = 1
            setCompoundDrawablesRelativeWithIntrinsicBounds(statusDot, null, null, null)
            compoundDrawablePadding = dp(5f)
        }
        val townView = text(12f, pc.muted).apply {
            gravity = Gravity.START
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        val tripLines = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(ui).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(timeView)
                    addView(addressView, LinearLayout.LayoutParams(0, dp(28f), 1f).apply { marginStart = dp(8f) })
                },
            )
            addView(
                LinearLayout(ui).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(nameView, LinearLayout.LayoutParams(0, WRAP, 1f))
                    addView(statusView, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8f) })
                },
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(5f) },
            )
            addView(townView, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2f) })
        }
        val trip = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(pc.well, WELL_RADIUS_DP)
            setPaddingRelative(dp(8f), dp(8f), dp(10f), dp(8f))
            contentDescription = ui.getString(R.string.overlay_open_app_desc)
            addView(View(ui).apply { background = stripe }, LinearLayout.LayoutParams(dp(4f), MATCH))
            addView(tripLines, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(9f) })
        }

        // Actions: a compact Back on glass and Next, the one bright thing on the card. Repeat is
        // a long-press on Next (or on the street bar).
        val back = actionButton(R.drawable.ic_previous, R.string.overlay_back, rounded(pc.well, ACTION_RADIUS_DP), pc.text)
        val next = actionButton(R.drawable.ic_next, R.string.overlay_next, glow(pc.next, ACTION_RADIUS_DP), pc.onNext)
        val actions = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(back, LinearLayout.LayoutParams(WRAP, dp(ACTION_H_DP)))
            addView(next, LinearLayout.LayoutParams(0, dp(ACTION_H_DP), 1f).apply { marginStart = dp(8f) })
        }

        card.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
        // The street bar and speed come from this phone's location: never on a tablet.
        if (source.street != null) card.addView(streetRow, LinearLayout.LayoutParams(MATCH, WRAP))
        card.addView(trip, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8f) })
        card.addView(actions, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8f) })

        // Reference numbers (listed in the README), so each part can be named; 12 and 14 are unused.
        back.ref(1)
        streetBar.ref(2)
        areaView.ref(3)
        sayStreet.ref(4)
        next.ref(5)
        minimize.ref(6, padText = false)
        close.ref(7, padText = false)
        clockView.ref(8)
        progressView.ref(9)
        speedView.ref(15)
        timeView.ref(10)
        statusView.ref(11)
        addressView.ref(13)
        trip.ref(16, bottomEnd = true)
        nameView.ref(18)
        townView.ref(19)

        next.setOnClickListener { source.next() }
        drag(next, press = true) { source.repeat() }
        back.setOnClickListener { if (!source.back()) toast(R.string.overlay_no_previous) }
        drag(back, press = true)
        streetBar.setOnClickListener { source.sayStreet() }
        drag(streetBar, press = true) { source.repeat() }
        sayStreet.setOnClickListener { settings.update { it.copy(sayStreetChanges = !it.sayStreetChanges) } }
        drag(sayStreet, press = true)
        addressView.setOnClickListener { source.sayStop() }
        drag(addressView, press = true)
        // × and – only react to a real tap: dragging from them moves the panel like elsewhere.
        close.setOnClickListener { hideByUser() }
        drag(close, press = true)
        minimize.setOnClickListener { settings.update { it.copy(overlayMinimized = true) } }
        drag(minimize, press = true)
        trip.setOnClickListener { openApp() }
        drag(trip)
        drag(header)
        drag(card)

        if (source.street != null) {
            v.street = streetView
            v.sayStreet = sayStreet
            v.speed = speedView
            v.area = areaView
        }
        v.back = back
        v.clock = clockView
        v.progress = progressView
        v.time = timeView
        v.name = nameView
        v.status = statusView
        v.statusDot = statusDot
        v.stripe = stripe
        v.address = addressView
        v.town = townView
        return window
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

    /** A solid button with the glass's sheen on top (Next). */
    private fun glow(fill: Int, radiusDp: Float): Drawable {
        val r = dp(radiusDp).toFloat()
        val sheen = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(pc.sheen, pc.clear)).apply { cornerRadius = r }
        return LayerDrawable(arrayOf(rounded(fill, radiusDp), sheen))
    }

    /** A compact pill with an icon beside its label (Back, Next). */
    private fun actionButton(@DrawableRes icon: Int, label: Int, bg: Drawable, fg: Int): View {
        val image = ImageView(ui).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(fg)
        }
        val caption = text(15f, fg, bold = true).apply {
            text = ui.getString(label)
            maxLines = 1
        }
        return LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = bg
            setPaddingRelative(dp(14f), 0, dp(16f), 0)
            contentDescription = ui.getString(label)
            addView(image, LinearLayout.LayoutParams(dp(20f), dp(20f)))
            addView(caption, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(6f) })
        }
    }

    /** A small round glass control with a symbol (– and ×). */
    private fun roundControl(symbol: String, description: Int) = text(18f, pc.text, bold = true).apply {
        text = symbol
        background = oval(pc.control)
        contentDescription = ui.getString(description)
    }

    private fun oval(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
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
        private const val PANEL_W_DP = 300f
        private const val CARD_RADIUS_DP = 24f
        private const val WELL_RADIUS_DP = 16f
        private const val ACTION_H_DP = 44f
        private const val ACTION_RADIUS_DP = 14f

        /** The capsule's ring and dot beat between full and this, as the display clock's colon does. */
        private const val BEAT_LOW = 0.2f
        private const val BEAT_MS = 700L

        /** The capsule: its base size, and how it swells and blinks in the trip's last minute. */
        private const val BUBBLE_MAIN_SP = 17f
        private const val BUBBLE_SUB_SP = 11f
        private const val BUBBLE_RING_DP = 2.5f
        private const val SWELL = 2f
        private const val SWELL_MS = 500L
        private const val BLINK_LOW = 0.35f
        private const val BLINK_MS = 450L

        /** The next stop's street beside its time, large enough to read at a glance. */
        private const val STOP_STREET_SP = 17f
        private const val CONTROL_DP = 34f
        private const val SPEED_DP = 60f
        private const val BUBBLE_H_DP = 52f

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

    /** The trip's stripe and kind chip, in its YouDrive card colour. */
    fun trip(kind: TripKind?): Int = p.trip(kind).toArgb()

    fun status(level: TimeLevel): Int = p.status(level).toArgb()

    fun countdown(status: TimeStatus): Int = p.status(status).toArgb()
}
