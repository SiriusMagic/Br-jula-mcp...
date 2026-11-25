package com.zirakusa.brujula.data

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on Earth (degrees). */
data class LatLon(val latitude: Double, val longitude: Double)

/** A saved place («Casa», «Coche»…). Lives only on the phone. */
data class Place(val name: String, val at: LatLon)

/** Distances, directions and coordinates, all worked out on the phone (no internet). Pure, with tests. */
object Geo {
    private const val EARTH_M = 6_371_000.0

    /** Metres between [a] and [b] (haversine). */
    fun distance(a: LatLon, b: LatLon): Double {
        val dLat = rad(b.latitude - a.latitude); val dLon = rad(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.latitude)) * cos(rad(b.latitude)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** The direction to walk from [a] to reach [b], in degrees from true north (0–360). */
    fun bearing(a: LatLon, b: LatLon): Float {
        val φ1 = rad(a.latitude); val φ2 = rad(b.latitude); val dλ = rad(b.longitude - a.longitude)
        val y = sin(dλ) * cos(φ2); val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(dλ)
        return Angles.wrap(Math.toDegrees(atan2(y, x)).toFloat())
    }

    /** «850 m», «1,2 km», «35 km» (comma in Spanish). */
    fun formatDistance(metres: Double, locale: Locale = Locale.getDefault()): String = when {
        metres < 1000 -> "${metres.roundToInt()} m"
        metres < 10_000 -> "%.1f km".format(locale, metres / 1000)
        else -> "${(metres / 1000).roundToInt()} km"
    }

    /** «-0.2299, -78.5249» with 5 decimals (about a metre), dot as decimal mark, for copying anywhere. */
    fun decimal(at: LatLon) = "%.5f, %.5f".format(Locale.US, at.latitude, at.longitude)

    private val NUMBER = Regex("""[-+]?\d+(?:[.,]\d+)?""")

    /**
     * Coordinates typed by hand: «-0.2299, -78.5249», «-0,2299 -78,5249», or degrees-minutes-seconds with letters
     * («0°13′47″S 78°31′29″O»; S, O and W are negative). Null when it is not a valid point.
     */
    fun parse(text: String): LatLon? {
        val t = text.trim().uppercase(Locale.ROOT).replace("’", "'").replace("″", "\"").replace("′", "'")
        if (t.isEmpty()) return null
        if (t.any { it in "NSEOW" }) {
            val parts = Regex("""([^NSEOW]+)([NSEOW])""").findAll(t).map { it.groupValues[1] to it.groupValues[2] }.toList()
            if (parts.size != 2) return null
            val (lat, lon) = parts.map { (num, hemi) ->
                val n = NUMBER.findAll(num.replace(',', '.')).map { it.value.toDouble() }.toList()
                if (n.isEmpty() || n.size > 3 || n.any { it < 0 }) return null
                val v = n[0] + n.getOrElse(1) { 0.0 } / 60 + n.getOrElse(2) { 0.0 } / 3600
                hemi to if (hemi == "S" || hemi == "O" || hemi == "W") -v else v
            }.let { list -> list.firstOrNull { it.first in "NS" } to list.firstOrNull { it.first in "EOW" } }
            return check(lat?.second ?: return null, lon?.second ?: return null)
        }
        // Decimal: «-0.2299, -78.5249» or with decimal commas separated by a space or a semicolon.
        val cleaned = if (Regex("""\d,\d""").containsMatchIn(t) && (t.contains(' ') || t.contains(';'))) t.replace(',', '.') else t
        val n = NUMBER.findAll(cleaned).map { it.value.replace(',', '.').toDouble() }.toList()
        return if (n.size == 2) check(n[0], n[1]) else null
    }

    private fun check(lat: Double, lon: Double) = if (abs(lat) <= 90 && abs(lon) <= 180) LatLon(lat, lon) else null

    /** Heading from true north, given the magnetic one and the declination (east positive). */
    fun trueHeading(magnetic: Float, declination: Float) = Angles.wrap(magnetic + declination)

    /** «2,1° E» / «3,4° O» for the declination. */
    fun formatDeclination(declination: Float, east: String, west: String, locale: Locale = Locale.getDefault()) =
        "%.1f° %s".format(locale, abs(declination), if (declination >= 0) east else west)

    private fun rad(d: Double) = d * PI / 180
}

/**
 * Where the sun is (NOAA's simple formulas, good to about a degree): a second way to find north that needs no
 * magnetometer and no internet. [azimuth] from true north, [elevation] above the horizon (negative = night).
 */
object Sun {
    data class Position(val azimuth: Float, val elevation: Float) { val up get() = elevation > 0 }

    fun at(where: LatLon, epochMillis: Long): Position {
        val jd = epochMillis / 86_400_000.0 + 2_440_587.5
        val t = (jd - 2_451_545.0) / 36_525.0
        val l0 = (280.46646 + t * (36_000.76983 + t * 0.0003032)).mod(360.0)
        val m = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(r(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(r(2 * m)) * (0.019993 - 0.000101 * t) + sin(r(3 * m)) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val lambda = trueLong - 0.00569 - 0.00478 * sin(r(omega))
        val eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val eps = eps0 + 0.00256 * cos(r(omega))
        val decl = asin(sin(r(eps)) * sin(r(lambda)))
        val y = kotlin.math.tan(r(eps / 2)).let { it * it }
        val eqTime = 4 * Math.toDegrees(y * sin(2 * r(l0)) - 2 * e * sin(r(m)) + 4 * e * y * sin(r(m)) * cos(2 * r(l0)) -
            0.5 * y * y * sin(4 * r(l0)) - 1.25 * e * e * sin(2 * r(m)))
        val minutes = (epochMillis.mod(86_400_000L)) / 60_000.0
        val trueSolar = (minutes + eqTime + 4 * where.longitude).mod(1440.0)
        val hourAngle = trueSolar / 4 - 180
        val lat = r(where.latitude)
        val cosZenith = (sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(r(hourAngle))).coerceIn(-1.0, 1.0)
        val zenith = acos(cosZenith)
        val az = if (abs(sin(zenith)) < 1e-9) 0.0 else {
            val cosAz = ((sin(lat) * cos(zenith) - sin(decl)) / (cos(lat) * sin(zenith))).coerceIn(-1.0, 1.0)
            val a = Math.toDegrees(acos(cosAz))
            if (hourAngle > 0) (a + 180).mod(360.0) else (540 - a).mod(360.0)
        }
        return Position(az.toFloat(), (90 - Math.toDegrees(zenith)).toFloat())
    }

    private fun r(d: Double) = d * PI / 180
}

/** Saved places as plain text (one per line: name, latitude, longitude, separated by tabs). Pure, with tests. */
object Places {
    const val MAX = 50

    fun write(places: List<Place>): String =
        places.take(MAX).joinToString("\n") { "${clean(it.name)}\t${it.at.latitude}\t${it.at.longitude}" }

    fun read(text: String?): List<Place> = text.orEmpty().lines().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size != 3) return@mapNotNull null
        val lat = p[1].toDoubleOrNull() ?: return@mapNotNull null; val lon = p[2].toDoubleOrNull() ?: return@mapNotNull null
        if (abs(lat) > 90 || abs(lon) > 180 || p[0].isBlank()) null else Place(p[0], LatLon(lat, lon))
    }.take(MAX)

    /** A name without tabs or line breaks, at most 40 characters; empty names become «Lugar N». */
    fun clean(name: String, number: Int = 1) = name.replace(Regex("""[\t\r\n]"""), " ").trim().take(40).ifEmpty { "Lugar $number" }
}
