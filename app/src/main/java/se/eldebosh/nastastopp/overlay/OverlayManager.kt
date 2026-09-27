package se.eldebosh.nastastopp.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.route.RouteController
import se.eldebosh.nastastopp.settings.SettingsStore
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Optional floating round button over Google Maps (needs SYSTEM_ALERT_WINDOW).
 * Tap = "Nästa" (advance + announce), long-press = "Upprepa". Shows the next stop's spoken name,
 * remembers its position and is hidden whenever no route is active.
 */
class OverlayManager(
    private val context: Context,
    private val controller: RouteController,
    private val settings: SettingsStore,
    scope: CoroutineScope,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var label: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    init {
        scope.launch {
            combine(controller.route, settings.state) { r, _ -> r }.collect { refresh() }
        }
    }

    val canShow: Boolean get() = Settings.canDrawOverlays(context)

    /** Shows/updates/hides the button according to the current route state and permission. */
    fun refresh() {
        val r = controller.route.value
        val current = r?.stops?.firstOrNull()
        if (r == null || !r.active || current == null || !canShow) {
            hide()
            return
        }
        if (root == null) show()
        label?.text = controller.spokenName(current)
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).roundToInt()

    // Gravity.LEFT on purpose: overlay x/y are absolute screen coordinates, also in RTL.
    @SuppressLint("ClickableViewAccessibility", "RtlHardcoded")
    private fun show() {
        val size = dp(112f)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xF0FFC400.toInt())
            setStroke(dp(3f), 0xFF000000.toInt())
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.action_next_sv)
            setTextColor(0xFF000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        }
        val small = TextView(context).apply {
            setTextColor(0xFF000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = bg
            elevation = dp(6f).toFloat()
            contentDescription = context.getString(R.string.overlay_description)
            addView(title)
            addView(small)
        }
        val (x, y) = settings.overlayPosition() ?: (dp(16f) to dp(220f))
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            this.x = x
            this.y = y
        }
        layout.setOnClickListener { controller.next(auto = false) }
        layout.setOnLongClickListener { controller.repeat(); true }
        layout.setOnTouchListener(TouchHandler(lp))
        try {
            wm?.addView(layout, lp)
            root = layout
            label = small
            params = lp
        } catch (_: Exception) {
            root = null
        }
    }

    fun hide() {
        val v = root ?: return
        runCatching { wm?.removeView(v) }
        root = null
        label = null
        params = null
    }

    /** Distinguishes drag, tap and long-press. */
    private inner class TouchHandler(private val lp: WindowManager.LayoutParams) : View.OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
        private val handler = Handler(Looper.getMainLooper())
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private val longPress = Runnable {
            longPressed = true
            root?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            controller.repeat()
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
                    handler.postDelayed(longPress, longPressMs)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging && !longPressed) {
                        lp.x = (startX + dx).roundToInt()
                        lp.y = (startY + dy).roundToInt()
                        runCatching { wm?.updateViewLayout(v, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (dragging) {
                        settings.setOverlayPosition(lp.x, lp.y)
                    } else if (!longPressed) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        v.performClick() // → controller.next()
                    }
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
            }
            return true
        }
    }
}
