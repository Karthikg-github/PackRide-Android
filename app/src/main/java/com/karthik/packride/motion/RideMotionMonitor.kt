package com.karthik.packride.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * G-force + lean for GPX during rides — Android port of ActiveSoloRideView motion.
 * Accel ~5–10 Hz; device orientation via accelerometer roll estimate (no gyro fusion required).
 */
class RideMotionMonitor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile var currentGForce: Double = 1.0
        private set
    @Volatile var maxGForce: Double = 1.0
        private set
    @Volatile var currentLeanDegrees: Double = 0.0
        private set
    @Volatile var maxLeanDegrees: Double = 0.0
        private set
    @Volatile var isLeanCalibrated: Boolean = false
        private set

    private var leanZeroOffset: Double? = null
    private val calibrationSamples = mutableListOf<Double>()
    private val calibrationCount = 5

    fun start() {
        leanZeroOffset = null
        isLeanCalibrated = false
        calibrationSamples.clear()
        maxGForce = 1.0
        maxLeanDegrees = 0.0
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        val g = sqrt((ax * ax + ay * ay + az * az).toDouble()) / SensorManager.GRAVITY_EARTH
        currentGForce = g
        if (g > maxGForce) maxGForce = g

        // Roll-ish estimate from gravity vector (phone mounted upright on bars)
        val rollDeg = Math.toDegrees(atan2(ax.toDouble(), az.toDouble()))
        if (leanZeroOffset == null) {
            calibrationSamples.add(rollDeg)
            if (calibrationSamples.size >= calibrationCount) {
                leanZeroOffset = calibrationSamples.average()
                isLeanCalibrated = true
            }
            return
        }
        val lean = (rollDeg - leanZeroOffset!!).coerceIn(-65.0, 65.0)
        currentLeanDegrees = lean
        if (kotlin.math.abs(lean) > kotlin.math.abs(maxLeanDegrees)) {
            maxLeanDegrees = lean
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
