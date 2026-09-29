package se.eldebosh.nastastopp.core.route

import se.eldebosh.nastastopp.core.geo.GeoLogic

/**
 * A location fix. Speed in m/s and heading in degrees from north (null if unknown), accuracy in
 * metres.
 */
data class Fix(
    val timeMs: Long,
    val lat: Double,
    val lng: Double,
    val speedMps: Float?,
    val accuracyM: Float?,
    val bearingDeg: Float? = null,
)

data class ArrivalConfig(
    /** Arrival radius (setting, 25–150 m, default 75). */
    val arrivalRadiusM: Double = 75.0,
    val closeRadiusM: Double = 25.0,
    val dwellSpeedMps: Float = 2f,
    val dwellMs: Long = 8_000,
    /** Must have been further than this from the stop since the previous stop ("arming"). */
    val armDistanceM: Double = 150.0,
    val departDistanceM: Double = 100.0,
    val departSpeedMps: Float = 5f,
    val minAutoAdvanceGapMs: Long = 30_000,
    val maxAccuracyM: Float = 50f,
) {
    companion object {
        /** Derives the other thresholds from the user's arrival radius so they stay consistent. */
        fun forRadius(radiusM: Int): ArrivalConfig {
            val r = radiusM.coerceIn(25, 150).toDouble()
            return ArrivalConfig(
                arrivalRadiusM = r,
                closeRadiusM = minOf(25.0, r),
                armDistanceM = maxOf(150.0, r + 75.0),
                departDistanceM = maxOf(100.0, r + 25.0),
            )
        }
    }
}

enum class DetectorPhase { IDLE, APPROACHING, ARRIVED }

sealed interface DetectorEvent {
    data object Arrived : DetectorEvent
    /** The vehicle left the stop it had arrived at → mark done, advance, announce. */
    data object Departed : DetectorEvent
}

/**
 * Arrival / departure state machine for the current stop (pure logic, unit-tested).
 *
 * Arrival (located stops only): distance ≤ radius AND (speed < 2 m/s for ≥ 8 s OR distance ≤ 25 m),
 * and only once armed (been > 150 m away since the previous stop).
 * Departure (after arrival): distance > 100 m OR speed > 5 m/s. No two automatic advances within 30 s.
 */
class ArrivalDetector(var config: ArrivalConfig = ArrivalConfig()) {

    var phase: DetectorPhase = DetectorPhase.IDLE
        private set
    var armed: Boolean = false
        private set
    var lastDistanceM: Double? = null
        private set

    private var targetLat = 0.0
    private var targetLng = 0.0
    private var lowSpeedSince: Long? = null
    private var lastAutoAdvanceAt: Long? = null
    private var prevFix: Fix? = null

    /** Sets the stop to watch (call on every stop change). Null disables automatic detection. */
    fun setTarget(lat: Double?, lng: Double?) {
        if (lat == null || lng == null) {
            phase = DetectorPhase.IDLE
        } else {
            targetLat = lat
            targetLng = lng
            phase = DetectorPhase.APPROACHING
        }
        armed = false
        lowSpeedSince = null
        lastDistanceM = null
    }

    fun onFix(fix: Fix): DetectorEvent? {
        if (phase == DetectorPhase.IDLE) return null
        val acc = fix.accuracyM
        if (acc != null && acc > config.maxAccuracyM) return null

        val speed = fix.speedMps ?: derivedSpeed(fix)
        prevFix = fix
        val distance = GeoLogic.distanceMeters(fix.lat, fix.lng, targetLat, targetLng)
        lastDistanceM = distance

        when (phase) {
            DetectorPhase.APPROACHING -> {
                if (distance > config.armDistanceM) armed = true
                if (!armed || distance > config.arrivalRadiusM) {
                    lowSpeedSince = null
                    return null
                }
                if (distance <= config.closeRadiusM) return arrive()
                if (speed != null && speed < config.dwellSpeedMps) {
                    val since = lowSpeedSince ?: fix.timeMs.also { lowSpeedSince = it }
                    if (fix.timeMs - since >= config.dwellMs) return arrive()
                } else {
                    lowSpeedSince = null
                }
                return null
            }
            DetectorPhase.ARRIVED -> {
                val leaving = distance > config.departDistanceM || (speed != null && speed > config.departSpeedMps)
                if (!leaving) return null
                val last = lastAutoAdvanceAt
                if (last != null && fix.timeMs - last < config.minAutoAdvanceGapMs) return null
                lastAutoAdvanceAt = fix.timeMs
                phase = DetectorPhase.IDLE // until the controller sets the next target
                return DetectorEvent.Departed
            }
            DetectorPhase.IDLE -> return null
        }
    }

    private fun arrive(): DetectorEvent {
        phase = DetectorPhase.ARRIVED
        lowSpeedSince = null
        return DetectorEvent.Arrived
    }

    private fun derivedSpeed(fix: Fix): Float? {
        val p = prevFix ?: return null
        val dt = (fix.timeMs - p.timeMs) / 1000.0
        if (dt <= 0.5 || dt > 30) return null
        return (GeoLogic.distanceMeters(p.lat, p.lng, fix.lat, fix.lng) / dt).toFloat()
    }
}
