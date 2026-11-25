package com.zirakusa.brujula.data

import kotlin.math.abs
import kotlin.math.roundToLong

/** How much the phone trusts its magnetometer (Android's SENSOR_STATUS_ACCURACY_* values). */
enum class Accuracy(val key: String) {
    UNRELIABLE("unreliable"), LOW("low"), MEDIUM("medium"), HIGH("high");

    /** Low or unreliable: the figure-8 calibration is shown. */
    val needsCalibration get() = this == UNRELIABLE || this == LOW

    companion object {
        /** SENSOR_STATUS_UNRELIABLE (0), ACCURACY_LOW (1), MEDIUM (2), HIGH (3); anything else (NO_CONTACT -1) is unreliable. */
        fun fromStatus(status: Int) = when (status) { 3 -> HIGH; 2 -> MEDIUM; 1 -> LOW; else -> UNRELIABLE }
    }
}

/** One heading: degrees clockwise from magnetic north (0 ≤ d < 360). [declination] is east-positive, when known. */
data class Heading(val degrees: Float, val accuracy: Accuracy, val declination: Float? = null) {
    val trueDegrees get() = declination?.let { Angles.wrap(degrees + it) }
}

/** One position from the phone (no Google services). [accuracyM] is the radius in metres; [time] in milliseconds. */
data class Fix(val latitude: Double, val longitude: Double, val accuracyM: Float? = null, val time: Long = 0,
    /** Metres above sea level (GPS), when known. */
    val altitude: Double? = null,
    /** «gps» works without internet; «network» needs Wi‑Fi or mobile data. */
    val provider: String? = null) {
    val at get() = LatLon(latitude, longitude)
}

/** Angle arithmetic on the circle: wrap, shortest difference and smoothing that never spins the long way round. */
object Angles {
    fun wrap(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    /** Shortest signed turn from [from] to [to], in (-180, 180]. */
    fun diff(from: Float, to: Float): Float {
        val d = wrap(to - from)
        return if (d > 180f) d - 360f else d
    }
}

/**
 * Low-pass filter for the heading: each reading moves the shown angle a share ([alpha]) of the shortest way to it, so
 * 359° → 1° turns 2°, not 358°. Small jitters (under [deadband]) are ignored so the dial stays still on a table.
 */
class AngleFilter(private val alpha: Float = 0.15f, private val deadband: Float = 0.2f) {
    var value: Float? = null; private set

    fun update(target: Float): Float {
        val now = value ?: return Angles.wrap(target).also { value = it }
        val d = Angles.diff(now, target)
        if (abs(d) < deadband) return now
        return Angles.wrap(now + alpha * d).also { value = it }
    }

    fun reset() { value = null }
}

/** Names of the directions (8 or 16 points). The screen uses string resources; these Spanish ones go out by MCP. */
object Directions {
    val ES_16 = listOf("Norte", "Nornoreste", "Noreste", "Estenoreste", "Este", "Estesureste", "Sureste", "Sursureste",
        "Sur", "Sursuroeste", "Suroeste", "Oestesuroeste", "Oeste", "Oestenoroeste", "Noroeste", "Nornoroeste")
    val SHORT_16 = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

    /** Which of [points] (8 or 16) sectors the heading falls in; 0 = north, centred on it (337.5°–22.5° with 8). */
    fun index(degrees: Float, points: Int): Int {
        require(points == 8 || points == 16) { "points must be 8 or 16" }
        val sector = 360.0 / points
        return (((Angles.wrap(degrees) + sector / 2) / sector).toInt()) % points
    }

    /** Position in the 16-point lists of the [points]-point name: with 8 points only every second one is used. */
    fun index16(degrees: Float, points: Int) = index(degrees, points) * (16 / points)

    fun spanish(degrees: Float, points: Int = 8) = ES_16[index16(degrees, points)]
    fun short(degrees: Float, points: Int = 8) = SHORT_16[index16(degrees, points)]
}

/** Latitude and longitude as degrees, minutes and seconds (3°36′23″), with the hemisphere apart. */
object Dms {
    data class Parts(val degrees: Int, val minutes: Int, val seconds: Int)

    /** Rounded to the whole second; 59.6″ carries into the minute (and the degree) instead of showing 60″. */
    fun parts(value: Double): Parts {
        val total = (abs(value) * 3600).roundToLong()
        return Parts((total / 3600).toInt(), ((total % 3600) / 60).toInt(), (total % 60).toInt())
    }

    fun format(value: Double): String = parts(value).let { "${it.degrees}°${two(it.minutes)}′${two(it.seconds)}″" }

    private fun two(n: Int) = n.toString().padStart(2, '0')

    fun isNorth(latitude: Double) = latitude >= 0
    fun isEast(longitude: Double) = longitude >= 0
}
