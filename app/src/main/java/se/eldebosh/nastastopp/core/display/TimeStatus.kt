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
         * A countdown to the stop from [secondsUntil] (negative = late): "12:05", "1:04:30" from an
         * hour on, and "+3:10" once it has passed. Western digits in every language.
         */
        fun countdown(secondsUntil: Int): String {
            val late = secondsUntil < 0
            val s = kotlin.math.abs(secondsUntil)
            val h = s / 3600
            val m = s % 3600 / 60
            val sec = s % 60
            val body = if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(java.util.Locale.ROOT, "%d:%02d", m, sec)
            return if (late) "+$body" else body
        }

        private const val NEAR_MIN = 5
    }
}
