package se.eldebosh.nastastopp.core.geo


/**
 * Which road the vehicle is on, from the offline street map: the nearest named road, with
 * the vehicle's heading deciding between roads that are nearly as close (at a crossing the road
 * you drive along wins over the one you cross). Only a good GPS position is used, and only a road
 * close enough to be the one you are on: no road means no name, never a guess.
 */
object StreetMatcher {
    /** Positions less exact than this are not used for the street. */
    const val MAX_ACCURACY_M = 25f

    /** A road farther away than this is not the road you are on. */
    const val MAX_DISTANCE_M = 30.0

    /** Below this speed the heading is not trusted (standing still, turning in a car park). */
    const val HEADING_MIN_SPEED_MPS = 3f

    /** A road across your heading counts as this much farther away. */
    const val CROSSING_PENALTY_M = 25.0

    fun match(map: StreetMap, fix: Fix): String? {
        val acc = fix.accuracyM
        if (acc != null && acc > MAX_ACCURACY_M) return null
        val heading = fix.bearingDeg?.takeIf { (fix.speedMps ?: 0f) >= HEADING_MIN_SPEED_MPS }
        return map.near(fix.lat, fix.lng, MAX_DISTANCE_M).minByOrNull { near ->
            near.distanceM + if (heading == null) 0.0 else StreetMap.angleToRoad(heading.toDouble(), near.bearingDeg) / 90.0 * CROSSING_PENALTY_M
        }?.name
    }
}

/**
 * Keeps the street name steady: a new street is taken only when it comes out the same in
 * [confirmations] readings in a row, so one stray position at a crossing never changes the name
 * (or makes the app say a wrong one). Without any road for a while ([holdMs]) the name is cleared.
 */
class StreetTracker(private val confirmations: Int = 2, private val holdMs: Long = 20_000) {
    var current: String? = null
        private set
    private var candidate: String? = null

    /** A new street was seen once and waits for its confirming reading. */
    val confirming: Boolean get() = candidate != null
    private var seen = 0
    private var lastMatchMs = 0L

    /** One reading: the matched road ([name], or null for none). Returns the street to show. */
    fun onReading(name: String?, nowMs: Long): String? {
        if (name == null) {
            candidate = null
            seen = 0
            if (current != null && nowMs - lastMatchMs > holdMs) current = null
            return current
        }
        lastMatchMs = nowMs
        if (name == current) {
            candidate = null
            seen = 0
            return current
        }
        if (name == candidate) seen++ else { candidate = name; seen = 1 }
        if (seen >= confirmations) {
            current = name
            candidate = null
            seen = 0
        }
        return current
    }

    fun reset() {
        current = null
        candidate = null
        seen = 0
    }
}
