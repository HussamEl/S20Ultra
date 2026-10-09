package se.eldebosh.nastastopp.geo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.eldebosh.nastastopp.core.display.CarStillness

/**
 * The tablet's accelerometer, while the passenger display is in sight ([start], [stop]): how much
 * the car shakes ([level], for the display's motion band) and whether it moved in the last two
 * minutes ([awake], with the GPS speed given to [onSpeed], kept as [speed] for the band). No
 * permission of its own; nothing is stored.
 */
class CarMotion(context: Context) {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val stillness = CarStillness()
    private val _level = MutableStateFlow(0f)
    private val _awake = MutableStateFlow(true)
    private val _speed = MutableStateFlow<Float?>(null)

    /** How much the car shakes now (0–1). */
    val level: StateFlow<Float> = _level.asStateFlow()

    /** The car moved within the last two minutes (always, without an accelerometer). */
    val awake: StateFlow<Boolean> = _awake.asStateFlow()

    /** The tablet's last GPS speed (m/s), or null without a position. */
    val speed: StateFlow<Float?> = _speed.asStateFlow()

    /** The tablet has an accelerometer: its motion sign shows. */
    val available: Boolean get() = sensor != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val now = SystemClock.elapsedRealtime()
            stillness.onAcceleration(event.values[0], event.values[1], event.values[2], now)
            _level.value = stillness.level
            _awake.value = stillness.awake(now)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        val s = sensor ?: return
        manager?.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        manager?.unregisterListener(listener)
    }

    /** The tablet's GPS speed (m/s), when a position comes. */
    fun onSpeed(speedMps: Float?) {
        val now = SystemClock.elapsedRealtime()
        _speed.value = speedMps
        stillness.onSpeed(speedMps, now)
        _awake.value = sensor == null || stillness.awake(now)
    }
}
