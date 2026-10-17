package com.siddesh.smsalerts.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * Reliable shake detector using the device accelerometer.
 * Detects deliberate shakes within a sliding time window with cooldown and debouncing.
 * Prevents false positives from single motions or normal phone handling.
 */
class ShakeDetector(
    private val context: Context,
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"
        // Acceleration threshold in m/s^2 (excluding earth gravity: normal is ~9.8)
        // High enough to ignore gentle movements, sensitive enough for deliberate shaking
        private const val SHAKE_THRESHOLD_G_FORCE = 2.7f
        // Minimum time between registered shake movements to prevent micro-oscillations counting as multiple
        private const val SHAKE_DEBOUNCE_MS = 220L
        // Maximum time window within which consecutive shakes must occur to count as an intentional trigger
        private const val SHAKE_WINDOW_MS = 1800L
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var isListening: Boolean = false
        private set

    var requiredShakes: Int = 3
    var cooldownMs: Long = 30_000L

    fun configure(shakes: Int, cooldown: Long) {
        this.requiredShakes = shakes
        this.cooldownMs = cooldown
    }

    private var shakeCount = 0
    private var lastShakeTimestamp = 0L
    private var lastTriggerTimestamp = 0L

    fun isSupported(): Boolean = accelerometer != null

    fun startListening() {
        if (isListening || accelerometer == null || sensorManager == null) return
        val registered = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )
        isListening = registered
        Log.d(TAG, "ShakeDetector started (registered=$registered, requiredShakes=$requiredShakes)")
    }

    fun stopListening() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
        shakeCount = 0
        Log.d(TAG, "ShakeDetector stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val now = System.currentTimeMillis()

        // Respect post-trigger cooldown window
        if (lastTriggerTimestamp > 0L && now - lastTriggerTimestamp < cooldownMs) {
            return
        }

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate G-Force (normalized by Earth's gravity 9.80665)
        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // Net g-force vector magnitude minus 1G of resting gravity
        val netGForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat() - 1.0f

        if (netGForce >= SHAKE_THRESHOLD_G_FORCE) {
            // Check debounce to avoid counting the return swing of the exact same shake
            if (now - lastShakeTimestamp < SHAKE_DEBOUNCE_MS) {
                return
            }

            // If time between shakes exceeded window, reset count to 1
            if (now - lastShakeTimestamp > SHAKE_WINDOW_MS) {
                shakeCount = 1
            } else {
                shakeCount++
            }
            lastShakeTimestamp = now

            Log.d(TAG, "Shake registered: count=$shakeCount / $requiredShakes (gForce=$netGForce)")

            if (shakeCount >= requiredShakes) {
                shakeCount = 0
                lastTriggerTimestamp = now
                Log.i(TAG, "EMERGENCY SHAKE TRIGGER REACHED ($requiredShakes shakes)")
                onShakeDetected()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
