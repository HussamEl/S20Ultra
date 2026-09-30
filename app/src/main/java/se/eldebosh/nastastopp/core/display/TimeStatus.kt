package se.eldebosh.nastastopp.core.display

/**
 * How the next stop's scheduled time stands, for the colour of the display clock's colon and
 * the floating panel's countdown: green while it is more than five minutes away (or has no
 * time); orange within five minutes, beating orange when it is due; red once it has passed,
 * beating red from five minutes late.
 */
enum class TimeStatus(val late: Boolean, val beating: Boolean) {
    ON_TIME(late = false, beating = false),
    SOON(late = false, beating = false),
    DUE(late = false, beating = true),
    LATE(late = true, beating = false),
    VERY_LATE(late = true, beating = true),
    ;

    companion object {
        /** The status of a stop due in [minutesUntil] minutes (negative = late; null = no time). */
        fun of(minutesUntil: Int?): TimeStatus = when {
            minutesUntil == null -> ON_TIME
            minutesUntil > NEAR_MIN -> ON_TIME
            minutesUntil > 0 -> SOON
            minutesUntil == 0 -> DUE
            minutesUntil > -NEAR_MIN -> LATE
            else -> VERY_LATE
        }

        /**
         * A countdown to the stop from [secondsUntil] (negative = late), in the parts the clock
         * shows: hours from an hour on, minutes and seconds ("1", "04", "30"; "7", "42"), with
         * "+" before the first once it has passed ("+3", "10"). Western digits in every language.
         */
        fun countdown(secondsUntil: Int): Countdown {
            val sign = if (secondsUntil < 0) "+" else ""
            val s = kotlin.math.abs(secondsUntil)
            val h = s / 3600
            val m = s % 3600 / 60
            val sec = two(s % 60)
            return if (h > 0) Countdown("$sign$h", two(m), sec) else Countdown(null, "$sign$m", sec)
        }

        private fun two(n: Int) = String.format(java.util.Locale.ROOT, "%02d", n)

        private const val NEAR_MIN = 5
    }
}

/** A countdown as the clock shows it: [hours] (null under an hour), [minutes] and [seconds]. */
data class Countdown(val hours: String?, val minutes: String, val seconds: String)
