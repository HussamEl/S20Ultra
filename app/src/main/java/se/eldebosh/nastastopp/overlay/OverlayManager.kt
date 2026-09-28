package se.eldebosh.nastastopp.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.MainActivity
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.parse.TripTimes
import se.eldebosh.nastastopp.geo.CurrentStreet
import se.eldebosh.nastastopp.route.RouteController
import se.eldebosh.nastastopp.service.Notifications
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.util.LocaleHelper
import se.eldebosh.nastastopp.util.TimeLabels
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Optional floating panel over Google Maps (needs SYSTEM_ALERT_WINDOW), shown while a route is
 * active:
 *
 *     [Back]   ( street we are on )   [Next]      ← follows the UI language (Arabic: Back on the right)
 *                   (speaker)                     ← says the street name
 *     clock · trip n/total · next trip: time, on-time status, distance · address · waiting timer
 *
 * Next: tap = "Nästa", long-press = repeat. Back: undo the last "Nästa". The street circle: tap =
 * say the street, long-press = repeat. "–" shrinks it to a small bubble (tap to expand); "×"
 * closes it, and it comes back from the notification that appears, the Quick Settings tile or the
 * app. Everything can be dragged; the position is remembered and kept on screen.
 */
class OverlayManager(
    private val context: Context,
    private val controller: RouteController,
    private val settings: SettingsStore,
    private val street: CurrentStreet,
    scope: CoroutineScope,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
    private var root: View? = null
    private var views: Views? = null
    private var layoutKey: String? = null
    private var ui: Context = context
    private var reminderShown: Boolean? = null

    /** Views that are updated while the panel (or bubble) is shown. */
    private class Views {
        var street: TextView? = null
        var area: TextView? = null
        var back: View? = null
        var clock: TextView? = null
        var progress: TextView? = null
        var time: TextView? = null
        var status: TextView? = null
        var distance: TextView? = null
        var address: TextView? = null
        var wait: TextView? = null
        var bubbleMain: TextView? = null
        var bubbleSub: TextView? = null
        var bubbleBg: GradientDrawable? = null
    }

    private val tick = object : Runnable {
        override fun run() {
            updateTimes()
            handler.postDelayed(this, 1_000L - System.currentTimeMillis() % 1_000L + 20L)
        }
    }

    init {
        scope.launch {
            combine(controller.route, settings.state, controller.tracking, street.state) { _, _, _, _ -> }.collect { refresh() }
        }
    }

    val canShow: Boolean get() = Settings.canDrawOverlays(context)

    /** Shows/updates/hides the panel according to the route, the settings and the permission. */
    fun refresh() {
        val r = controller.route.value
        val s = settings.current
        val routeOn = r != null && r.active && r.stops.isNotEmpty() && canShow
        setReminder(routeOn && s.overlayHidden)
        if (!routeOn || s.overlayHidden) {
            street.want(WANT_KEY, false)
            hide()
            return
        }
        street.want(WANT_KEY, !s.overlayMinimized)
        val key = "${s.overlayMinimized}|${s.uiLanguage}|${s.showRefNumbers}"
        if (root == null || key != layoutKey) {
            hide()
            show(s.overlayMinimized)
            layoutKey = key
        }
        bind()
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).roundToInt()

    // ------------------------------------------------------------------------------------------
    // Content

    private fun bind() {
        val v = views ?: return
        val r = controller.route.value ?: return
        val current = r.stops.firstOrNull() ?: return
        val info = street.state.value
        // No location (the driver's choice): the circle shows the next stop's area instead.
        v.street?.text = info?.street ?: info?.area ?: controller.spokenName(current)
        v.area?.apply {
            val area = info?.area.takeIf { info?.street != null }
            text = area.orEmpty()
            visibility = if (area.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        v.back?.alpha = if (r.completed.isEmpty()) 0.35f else 1f
        v.progress?.text = progress(r.completedCount, r.stops.size)
        v.address?.text = current.displayText
        v.time?.apply {
            text = current.time.orEmpty()
            visibility = if (current.time == null) View.GONE else View.VISIBLE
        }
        v.distance?.apply {
            val d = controller.tracking.value.distanceM
            text = d?.let { TimeLabels.distance(ui, it) }.orEmpty()
            visibility = if (d == null) View.GONE else View.VISIBLE
        }
        updateTimes()
    }

    /** Clock, on-time status and waiting timer (every second). */
    private fun updateTimes() {
        val v = views ?: return
        val r = controller.route.value ?: return
        val current = r.stops.firstOrNull() ?: return
        val now = LocalTime.now()
        v.clock?.text = now.format(clockFormat)
        val until = TripTimes.minutesUntil(current.time, now.hour * 60 + now.minute)
        val color = until?.let { TimeLabels.color(TripTimes.level(it)) }
        v.status?.apply {
            text = until?.let { TimeLabels.until(ui, it) }.orEmpty()
            visibility = if (until == null) View.GONE else View.VISIBLE
            if (color != null) setTextColor(color)
        }
        val arrivedAt = controller.tracking.value.arrivedAtMs
        val waited = arrivedAt?.let { TimeLabels.duration(System.currentTimeMillis() - it) }
        v.wait?.apply {
            text = waited?.let { ui.getString(R.string.wait_at_stop, it) }.orEmpty()
            visibility = if (waited == null) View.GONE else View.VISIBLE
        }
        v.bubbleMain?.text = current.time ?: progress(r.completedCount, r.stops.size)
        v.bubbleSub?.apply {
            text = waited ?: if (current.time != null) progress(r.completedCount, r.stops.size) else ""
            visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        v.bubbleBg?.setStroke(dp(4f), color ?: BLACK)
    }

    private fun progress(done: Int, remaining: Int) = "${done + 1}/${done + remaining}"

    // ------------------------------------------------------------------------------------------
    // Views

    // Gravity.LEFT on purpose: overlay x/y are absolute screen coordinates, also in RTL.
    @SuppressLint("RtlHardcoded")
    private fun show(minimized: Boolean) {
        ui = LocaleHelper.wrap(context, settings.current.uiLanguage)
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

    /** Small bubble: next trip's time (ring colour = on time / soon / late). Tap = expand. */
    // TouchHandler calls performClick on a tap, so clicks stay accessible.
    @SuppressLint("ClickableViewAccessibility")
    private fun buildBubble(lp: WindowManager.LayoutParams, v: Views): View {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(YELLOW)
            setStroke(dp(4f), BLACK)
        }
        val main = text(15f, BLACK, bold = true)
        val sub = text(11f, BLACK)
        val bubble = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = bg
            elevation = dp(6f).toFloat()
            contentDescription = ui.getString(R.string.overlay_expand_desc)
            addView(main)
            addView(sub)
        }
        val frame = FrameLayout(ui).apply {
            val m = dp(4f)
            addView(bubble, FrameLayout.LayoutParams(dp(68f), dp(68f)).apply { setMargins(m, m, m, m) })
        }
        bubble.ref(17)
        bubble.setOnClickListener { settings.update { it.copy(overlayMinimized = false) } }
        bubble.setOnTouchListener(TouchHandler(lp, frame, onLongPress = null))
        v.bubbleMain = main
        v.bubbleSub = sub
        v.bubbleBg = bg
        return frame
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel(lp: WindowManager.LayoutParams, v: Views): View {
        val container = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        fun drag(view: View, onLongPress: (() -> Unit)? = null) = view.setOnTouchListener(TouchHandler(lp, container, onLongPress))

        // Current street in the big circle.
        val streetView = text(18f, WHITE, bold = true).apply {
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            setAutoSizeTextTypeUniformWithConfiguration(10, 18, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        val areaView = text(11f, 0xCCFFFFFF.toInt()).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(92f)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }
        val circle = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xF0102030.toInt())
                setStroke(dp(3f), YELLOW)
            }
            elevation = dp(6f).toFloat()
            contentDescription = ui.getString(R.string.overlay_street_desc)
            addView(streetView, LinearLayout.LayoutParams(dp(96f), dp(58f)))
            addView(areaView)
        }
        val speaker = roundIcon(R.drawable.ic_speaker, 40f, WHITE, BLACK, R.string.overlay_speak_street_desc)
        val circleFrame = FrameLayout(ui).apply {
            addView(circle, FrameLayout.LayoutParams(dp(CIRCLE_DP), dp(CIRCLE_DP), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            addView(speaker, FrameLayout.LayoutParams(dp(40f), dp(40f), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }

        // Back (with ×) and Next (with –) on either side.
        val back = labeledButton(R.drawable.ic_previous, R.string.overlay_back, 60f, 0xF0303030.toInt(), WHITE)
        val next = labeledButton(R.drawable.ic_next, R.string.overlay_next, 66f, YELLOW, BLACK)
        val close = smallControl("×", R.string.overlay_close_desc)
        val minimize = smallControl("–", R.string.overlay_minimize_desc)
        val row = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sideColumn(close, back))
            addView(circleFrame, LinearLayout.LayoutParams(dp(CIRCLE_DP + 8f), dp(CIRCLE_DP + 20f)))
            addView(sideColumn(minimize, next))
        }

        // Info: clock, trip n/total, next trip's time + status + distance, address, waiting timer.
        val clockView = text(18f, WHITE, bold = true)
        val progressView = text(13f, 0xFFB0B0B0.toInt()).apply { setPadding(dp(8f), 0, dp(8f), 0) }
        val timeView = text(15f, YELLOW, bold = true)
        val statusView = text(14f, WHITE, bold = true).apply { setPadding(dp(8f), 0, dp(8f), 0) }
        val distanceView = text(13f, 0xFFB0B0B0.toInt())
        val addressView = text(13f, WHITE).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }
        val waitView = text(14f, YELLOW, bold = true).apply { visibility = View.GONE }
        val lines = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            addView(line(clockView, progressView))
            addView(line(timeView, statusView, distanceView))
            addView(addressView)
            addView(waitView)
        }
        val repeat = roundIcon(R.drawable.ic_repeat, 40f, 0xFF3A3A3A.toInt(), WHITE, R.string.overlay_repeat_desc)
        val info = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(6f), dp(8f), dp(6f))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(0xD9000000.toInt())
            }
            contentDescription = ui.getString(R.string.overlay_open_app_desc)
            addView(lines, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(repeat, LinearLayout.LayoutParams(dp(40f), dp(40f)).apply { marginStart = dp(6f) })
        }
        container.addView(row)
        container.addView(
            info,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) },
        )

        // Reference numbers 1–16 (listed in the README), so the driver can name each part.
        back.ref(1)
        circle.ref(2)
        areaView.ref(3)
        speaker.ref(4)
        next.ref(5)
        minimize.ref(6, padText = false)
        close.ref(7, padText = false)
        clockView.ref(8)
        progressView.ref(9)
        timeView.ref(10)
        statusView.ref(11)
        distanceView.ref(12)
        addressView.ref(13)
        waitView.ref(14)
        repeat.ref(15)
        info.ref(16, bottomEnd = true)

        next.setOnClickListener { controller.next(auto = false) }
        drag(next) { controller.repeat() }
        back.setOnClickListener { if (!controller.back()) toast(R.string.overlay_no_previous) }
        drag(back)
        circle.setOnClickListener { speakStreet() }
        drag(circle) { controller.repeat() }
        speaker.setOnClickListener { speakStreet() }
        drag(speaker)
        // × and – only react to a real tap: dragging from them moves the panel like elsewhere.
        close.setOnClickListener { hideByUser() }
        drag(close)
        minimize.setOnClickListener { settings.update { it.copy(overlayMinimized = true) } }
        drag(minimize)
        repeat.setOnClickListener { controller.repeat() }
        drag(repeat)
        info.setOnClickListener { openApp() }
        drag(info)

        v.street = streetView
        v.area = areaView
        v.back = back
        v.clock = clockView
        v.progress = progressView
        v.time = timeView
        v.status = statusView
        v.distance = distanceView
        v.address = addressView
        v.wait = waitView
        return container
    }

    private fun text(sp: Float, color: Int, bold: Boolean = false) = TextView(ui).apply {
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * FONT_SCALE)
        gravity = Gravity.CENTER
        if (bold) paint.isFakeBoldText = true
    }

    private fun line(vararg children: View) = LinearLayout(ui).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        children.forEach { addView(it) }
    }

    private fun sideColumn(control: View, button: View) = LinearLayout(ui).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(control, LinearLayout.LayoutParams(dp(34f), dp(34f)).apply { bottomMargin = dp(8f) })
        addView(button)
    }

    private fun oval(fill: Int, stroke: Int, strokeDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke(dp(strokeDp), stroke)
    }

    /** Round action button with an icon and a short label (Back / Next). */
    private fun labeledButton(@DrawableRes icon: Int, label: Int, sizeDp: Float, fill: Int, fg: Int): View {
        val image = ImageView(ui).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(fg)
        }
        val caption = text(11f, fg, bold = true).apply {
            text = ui.getString(label)
            maxLines = 1
        }
        return LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = oval(fill, if (fill == YELLOW) BLACK else WHITE, 2f)
            elevation = dp(6f).toFloat()
            contentDescription = ui.getString(label)
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            addView(image, LinearLayout.LayoutParams(dp(28f), dp(28f)))
            addView(caption)
        }
    }

    private fun roundIcon(@DrawableRes icon: Int, sizeDp: Float, fill: Int, fg: Int, description: Int) = ImageView(ui).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(fg)
        val pad = dp(sizeDp / 5f)
        setPadding(pad, pad, pad, pad)
        background = oval(fill, if (fill == WHITE) BLACK else WHITE, 1.5f)
        elevation = dp(8f).toFloat()
        contentDescription = ui.getString(description)
    }

    /**
     * Small translucent reference number in the top-start (or bottom-end) corner of [this]
     * (setting). Texts get a little start padding so the number does not cover them.
     */
    private fun View.ref(n: Int, bottomEnd: Boolean = false, padText: Boolean = true) {
        if (!settings.current.showRefNumbers) return
        if (padText && this is TextView) setPaddingRelative(maxOf(paddingStart, dp(REF_TEXT_PAD_DP)), paddingTop, paddingEnd, paddingBottom)
        val rtl = ui.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val badge = RefBadge(n.toString(), rtl, bottomEnd)
        addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> badge.setBounds(0, 0, r - l, b - t) }
        overlay.add(badge)
    }

    private inner class RefBadge(private val text: String, private val rtl: Boolean, private val bottomEnd: Boolean) : Drawable() {
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9f, context.resources.displayMetrics)
            isFakeBoldText = true
        }
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59000000 }

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

    private fun smallControl(symbol: String, description: Int) = text(18f, WHITE, bold = true).apply {
        text = symbol
        background = oval(0xE0202020.toInt(), WHITE, 1.5f)
        elevation = dp(8f).toFloat()
        contentDescription = ui.getString(description)
    }

    // ------------------------------------------------------------------------------------------
    // Actions

    /** Says the current street when it is known, otherwise repeats the next-stop announcement. */
    private fun speakStreet() {
        if (!controller.speakStreet(street.state.value)) controller.repeat()
    }

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

    /** Moves the window back inside the screen (after a size change or a drag). */
    private fun keepOnScreen(view: View, lp: WindowManager.LayoutParams) {
        if (root != view) return
        val dm = context.resources.displayMetrics
        val nx = lp.x.coerceIn(0, (dm.widthPixels - view.width).coerceAtLeast(0))
        val ny = lp.y.coerceIn(0, (dm.heightPixels - view.height).coerceAtLeast(0))
        if (nx == lp.x && ny == lp.y) return
        lp.x = nx
        lp.y = ny
        runCatching { wm?.updateViewLayout(view, lp) }
        settings.setOverlayPosition(nx, ny)
    }

    fun hide() {
        handler.removeCallbacks(tick)
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

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    longPressed = false
                    v.isPressed = true
                    if (onLongPress != null) handler.postDelayed(longPress, longPressMs)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        v.isPressed = false
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging && !longPressed) {
                        lp.x = (startX + dx).roundToInt()
                        lp.y = (startY + dy).roundToInt()
                        runCatching { wm?.updateViewLayout(window, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    v.isPressed = false
                    if (dragging) {
                        settings.setOverlayPosition(lp.x, lp.y)
                        keepOnScreen(window, lp)
                    } else if (!longPressed) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        v.performClick() // → the view's click listener
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    v.isPressed = false
                }
            }
            return true
        }
    }

    companion object {
        private const val WANT_KEY = "overlay"
        private const val CIRCLE_DP = 120f
        private const val REF_TEXT_PAD_DP = 17f

        /** All panel texts 10 % smaller than first designed (driver's request). */
        private const val FONT_SCALE = 0.9f
        private const val YELLOW = 0xF0FFC400.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val BLACK = 0xFF000000.toInt()
    }
}
