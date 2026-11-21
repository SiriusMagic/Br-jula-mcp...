package com.zirakusa.brujula.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import com.zirakusa.brujula.data.Accuracy
import com.zirakusa.brujula.data.AngleFilter
import com.zirakusa.brujula.data.Angles
import com.zirakusa.brujula.data.Heading

/**
 * The phone's heading: the rotation vector sensor when there is one (the phone fuses gyroscope, accelerometer and
 * magnetometer), else accelerometer + magnetometer. Smoothed with [AngleFilter] and turned for the screen's rotation.
 * The magnetometer's accuracy says when to calibrate. It only listens while someone uses it ([start]/[stop]).
 */
class CompassSensor(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val rotationVector = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    /** False on phones without a magnetometer: no compass at all. */
    val available get() = magnetometer != null && (rotationVector != null || accelerometer != null)
    val source get() = if (rotationVector != null) "rotation_vector" else "accelerometer_magnetometer"

    /** The screen's rotation (Surface.ROTATION_*), read on every reading; the activity sets it. */
    var displayRotation: () -> Int = { Surface.ROTATION_0 }
    var onHeading: (Heading) -> Unit = {}
    var onAccuracy: (Accuracy) -> Unit = {}

    @Volatile var latest: Heading? = null; private set
    @Volatile var latestAt = 0L; private set
    @Volatile var accuracy = Accuracy.UNRELIABLE; private set
    val running get() = synchronized(users) { users.isNotEmpty() }

    private val users = mutableSetOf<Any>()
    private val filter = AngleFilter()
    private val gravity = FloatArray(3)
    private val field = FloatArray(3)
    private var haveGravity = false
    private var haveField = false
    private val matrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val angles = FloatArray(3)

    fun start(user: Any) = synchronized(users) {
        if (!users.add(user) || users.size > 1 || manager == null) return@synchronized
        filter.reset()
        magnetometer?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        if (rotationVector != null) manager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI)
        else accelerometer?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop(user: Any) = synchronized(users) {
        if (users.remove(user) && users.isEmpty()) { manager?.unregisterListener(this); haveGravity = false; haveField = false }
    }

    override fun onAccuracyChanged(sensor: Sensor, status: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) noteAccuracy(status)
    }

    /** Some phones only tell the accuracy inside the readings, so both ways are heard. */
    private fun noteAccuracy(status: Int) {
        val next = Accuracy.fromStatus(status)
        if (next != accuracy) { accuracy = next; onAccuracy(next) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> SensorManager.getRotationMatrixFromVector(matrix, event.values)
            Sensor.TYPE_ACCELEROMETER -> { lowPass(event.values, gravity, haveGravity); haveGravity = true; if (!fused()) return }
            // With a rotation vector the magnetometer is only heard for its accuracy.
            Sensor.TYPE_MAGNETIC_FIELD -> { noteAccuracy(event.accuracy); lowPass(event.values, field, haveField); haveField = true; if (rotationVector != null || !fused()) return }
            else -> return
        }
        publish()
    }

    private fun fused() = haveGravity && haveField && SensorManager.getRotationMatrix(matrix, null, gravity, field)

    private fun publish() {
        val (x, y) = when (displayRotation()) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, x, y, remapped)
        SensorManager.getOrientation(remapped, angles)
        val degrees = filter.update(Angles.wrap(Math.toDegrees(angles[0].toDouble()).toFloat()))
        val heading = Heading(degrees, accuracy)
        latest = heading; latestAt = System.currentTimeMillis()
        onHeading(heading)
    }

    private fun lowPass(input: FloatArray, output: FloatArray, warm: Boolean) {
        for (i in 0..2) output[i] = if (warm) output[i] + 0.25f * (input[i] - output[i]) else input[i]
    }
}
