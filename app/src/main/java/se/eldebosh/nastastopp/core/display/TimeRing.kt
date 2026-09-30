package se.eldebosh.nastastopp.core.display

/**
 * The ring around the display clock's minutes: how the next stop's scheduled time stands. None
 * while it is more than five minutes away; orange within five minutes, beating orange when it is
 * due; red once it has passed, beating red from five minutes late.
 */
enum class TimeRing(val late: Boolean, val beating: Boolean) {
    SOON(late = false, beating = false),
    DUE(late = false, beating = true),
    LATE(late = true, beating = false),
    VERY_LATE(late = true, beating = true),
    ;

    companion object {
        /** The ring for a stop due in [minutesUntil] minutes (negative = late), or null for none. */
        fun of(minutesUntil: Int?): TimeRing? = when {
            minutesUntil == null -> null
            minutesUntil > NEAR_MIN -> null
            minutesUntil > 0 -> SOON
            minutesUntil == 0 -> DUE
            minutesUntil > -NEAR_MIN -> LATE
            else -> VERY_LATE
        }

        private const val NEAR_MIN = 5
    }
}
