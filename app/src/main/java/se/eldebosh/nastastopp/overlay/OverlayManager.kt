package se.eldebosh.nastastopp.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.MainActivity
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.route.RouteController
import se.eldebosh.nastastopp.settings.SettingsStore
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Optional floating round button over Google Maps (needs SYSTEM_ALERT_WINDOW).
 * Tap = "Nästa" (advance + announce), long-press = "Upprepa". Shows the next stop's spoken name;
 * under it the current time and the next trip's address. A small × hides it (it can be shown
 * again from the app). Remembers its position and is hidden whenever no route is active.
 */
class OverlayManager(
    private val context: Context,
    private val controller: RouteController,
    private val settings: SettingsStore,
    scope: CoroutineScope,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
    private var root: LinearLayout? = null
    private var label: TextView? = null
    private var clock: TextView? = null
    private var address: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    private val clockTick = object : Runnable {
        override fun run() {
            clock?.text = LocalTime.now().format(clockFormat)
            // Next update just after the minute changes.
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }

    init {
        scope.launch {
            combine(controller.route, settings.state) { r, _ -> r }.collect { refresh() }
        }
    }

    val canShow: Boolean get() = Settings.canDrawOverlays(context)

    /** Shows/updates/hides the button according to the route, the setting and the permission. */
    fun refresh() {
        val r = controller.route.value
        val current = r?.stops?.firstOrNull()
        if (r == null || !r.active || current == null || !canShow || settings.current.overlayHidden) {
            hide()
            return
        }
        if (root == null) show()
        label?.text = controller.spokenName(current)
        address?.text = listOfNotNull(current.time, current.displayText).joinToString("  ·  ")
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).roundToInt()

    // Gravity.LEFT on purpose: overlay x/y are absolute screen coordinates, also in RTL.
    @SuppressLint("ClickableViewAccessibility", "RtlHardcoded")
    private fun show() {
        val size = dp(112f)
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
        val button = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xF0FFC400.toInt())
                setStroke(dp(3f), 0xFF000000.toInt())
            }
            elevation = dp(6f).toFloat()
            contentDescription = context.getString(R.string.overlay_description)
            addView(title)
            addView(small)
        }
        // Small × in the corner: hides the button until it is shown again from the app.
        val close = TextView(context).apply {
            text = "×"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            contentDescription = context.getString(R.string.overlay_hide_desc)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE0202020.toInt())
                setStroke(dp(1.5f), 0xFFFFFFFF.toInt())
            }
            elevation = dp(8f).toFloat()
            setOnClickListener { settings.update { it.copy(overlayHidden = true) } }
        }
        val buttonFrame = FrameLayout(context).apply {
            addView(button, FrameLayout.LayoutParams(size, size))
            addView(close, FrameLayout.LayoutParams(dp(32f), dp(32f), Gravity.TOP or Gravity.RIGHT))
        }
        // Under the button: current time + next trip's address.
        val clockView = TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        }
        val addressView = TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            maxLines = 2
            maxWidth = dp(200f)
            ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10f), dp(4f), dp(10f), dp(6f))
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(0xCC000000.toInt())
            }
            addView(clockView)
            addView(addressView)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(buttonFrame)
            addView(info, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) })
        }
        val (x, y) = settings.overlayPosition() ?: (dp(16f) to dp(220f))
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
        button.setOnClickListener { controller.next(auto = false) }
        button.setOnLongClickListener { controller.repeat(); true }
        button.setOnTouchListener(TouchHandler(lp, container, onLongPress = { controller.repeat() }))
        info.setOnClickListener { openApp() }
        info.setOnTouchListener(TouchHandler(lp, container, onLongPress = null))
        try {
            wm?.addView(container, lp)
            root = container
            label = small
            clock = clockView
            address = addressView
            params = lp
            handler.removeCallbacks(clockTick)
            clockTick.run()
        } catch (_: Exception) {
            root = null
        }
    }

    private fun openApp() {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    fun hide() {
        handler.removeCallbacks(clockTick)
        val v = root ?: return
        runCatching { wm?.removeView(v) }
        root = null
        label = null
        clock = null
        address = null
        params = null
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
            window.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
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
                    if (onLongPress != null) handler.postDelayed(longPress, longPressMs)
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
                        runCatching { wm?.updateViewLayout(window, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (dragging) {
                        settings.setOverlayPosition(lp.x, lp.y)
                    } else if (!longPressed) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        v.performClick() // → the view's click listener
                    }
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
            }
            return true
        }
    }
}
