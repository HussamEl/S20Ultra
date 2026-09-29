package se.eldebosh.nastastopp.util

import android.content.Context
import se.eldebosh.nastastopp.R
import kotlin.math.abs

/** Short texts for the next trip's time status. */
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
}
