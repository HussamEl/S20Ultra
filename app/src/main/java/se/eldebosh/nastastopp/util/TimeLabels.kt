package se.eldebosh.nastastopp.util

import android.content.Context
import se.eldebosh.nastastopp.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Short texts for the next trip's time status, the waiting timer and distances. */
object TimeLabels {
    /**
     * "in 7 min" / "now" / "5 min late" for [minutes] until the scheduled time. Numbers are passed
     * as plain digits so they match the trip times ("12:48") in every language.
     */
    fun until(context: Context, minutes: Int): String {
        val m = abs(minutes)
        val h = (m / 60).toString()
        val min = (m % 60).toString()
        return when {
            minutes == 0 -> context.getString(R.string.time_now)
            minutes > 0 && m < 60 -> context.getString(R.string.time_in_min, m.toString())
            minutes > 0 -> context.getString(R.string.time_in_hm, h, min)
            m < 60 -> context.getString(R.string.time_late_min, m.toString())
            else -> context.getString(R.string.time_late_hm, h, min)
        }
    }

    /** Elapsed time as "mm:ss" (or "h:mm:ss"). */
    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        return if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", h, s % 3600 / 60, s % 60)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)
        }
    }

    /** Straight-line distance: "350 m" (rounded to 10 m) or "1.2 km". */
    fun distance(context: Context, meters: Double): String =
        if (meters < 1000) {
            context.getString(R.string.dist_m, ((meters / 10).roundToInt() * 10).coerceAtLeast(10).toString())
        } else {
            context.getString(R.string.dist_km, String.format(Locale.ROOT, "%.1f", meters / 1000))
        }
}
