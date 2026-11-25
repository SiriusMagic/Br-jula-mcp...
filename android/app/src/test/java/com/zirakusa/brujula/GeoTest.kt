package com.zirakusa.brujula

import com.zirakusa.brujula.data.Accuracy
import com.zirakusa.brujula.data.Calibration
import com.zirakusa.brujula.data.Geo
import com.zirakusa.brujula.data.LatLon
import com.zirakusa.brujula.data.Place
import com.zirakusa.brujula.data.Places
import com.zirakusa.brujula.data.Sun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.util.Locale

/** Offline position maths: distance, direction, typed coordinates, the sun, saved places and calibration. */
class GeoTest {
    private val quito = LatLon(-0.1807, -78.4678)
    private val guayaquil = LatLon(-2.1894, -79.8891)
    private val es = Locale.forLanguageTag("es")

    @Test fun distanceAndBearing() {
        assertEquals(270_000.0, Geo.distance(quito, guayaquil), 5_000.0)             // about 270 km
        assertEquals(215f, Geo.bearing(quito, guayaquil), 3f)                         // south-west
        assertEquals(0.0, Geo.distance(quito, quito), 1e-6)
        assertEquals(0f, Geo.bearing(LatLon(0.0, 0.0), LatLon(1.0, 0.0)), 1e-3f)       // due north
        assertEquals(90f, Geo.bearing(LatLon(0.0, 0.0), LatLon(0.0, 1.0)), 1e-3f)      // due east
        assertEquals("850 m", Geo.formatDistance(850.2, es))
        assertEquals("1,2 km", Geo.formatDistance(1_240.0, es))
        assertEquals("35 km", Geo.formatDistance(35_400.0, es))
    }

    @Test fun typedCoordinates() {
        assertEquals(LatLon(-0.2299, -78.5249), Geo.parse("-0.2299, -78.5249"))
        assertEquals(LatLon(-0.2299, -78.5249), Geo.parse("-0,2299 -78,5249"))
        val dms = Geo.parse("0°13′47″S 78°31′29″O")!!
        assertEquals(-0.22972, dms.latitude, 1e-4); assertEquals(-78.52472, dms.longitude, 1e-4)
        val west = Geo.parse("40 26 46 N 79 58 56 W")!!
        assertEquals(40.4461, west.latitude, 1e-3); assertEquals(-79.9822, west.longitude, 1e-3)
        assertNull(Geo.parse("")); assertNull(Geo.parse("hola")); assertNull(Geo.parse("95, 10")); assertNull(Geo.parse("1, 2, 3"))
        assertEquals("-0.22990, -78.52490", Geo.decimal(LatLon(-0.2299, -78.5249)))
    }

    @Test fun trueNorthAndDeclination() {
        assertEquals(2f, Geo.trueHeading(358f, 4f), 1e-4f)
        assertEquals("2,1° E", Geo.formatDeclination(2.1f, "E", "O", es))
        assertEquals("3,4° O", Geo.formatDeclination(-3.4f, "E", "O", es))
    }

    @Test fun theSun() {
        // Quito, equinox at local noon (17:00 UTC): nearly overhead. 07:00 local: low in the east; 17:00 local: west.
        val noon = Sun.at(quito, ZonedDateTime.parse("2026-03-20T17:14:00Z").toInstant().toEpochMilli())
        assertTrue(noon.elevation > 80)
        val morning = Sun.at(quito, ZonedDateTime.parse("2026-03-20T12:00:00Z").toInstant().toEpochMilli())
        assertTrue(morning.up); assertEquals(90f, morning.azimuth, 10f)
        val evening = Sun.at(quito, ZonedDateTime.parse("2026-03-20T22:00:00Z").toInstant().toEpochMilli())
        assertEquals(270f, evening.azimuth, 10f)
        assertFalse(Sun.at(quito, ZonedDateTime.parse("2026-03-21T05:00:00Z").toInstant().toEpochMilli()).up)   // midnight
        // Madrid, June at 12:00 UTC (about solar noon): the sun is south.
        val madrid = Sun.at(LatLon(40.4168, -3.7038), ZonedDateTime.parse("2026-06-21T12:15:00Z").toInstant().toEpochMilli())
        assertEquals(180f, madrid.azimuth, 15f); assertEquals(73f, madrid.elevation, 3f)
    }

    @Test fun placesRoundTrip() {
        val list = listOf(Place("Casa", quito), Place("Coche\tcon\ttab", guayaquil))
        val back = Places.read(Places.write(list))
        assertEquals(listOf("Casa", "Coche con tab"), back.map { it.name })
        assertEquals(quito, back[0].at)
        assertEquals(emptyList<Place>(), Places.read(null))
        assertEquals(1, Places.read("bien\t1\t2\nmal\t200\t2\nroto").size)            // bad lines are skipped
        assertEquals("Lugar 3", Places.clean("   ", 3))
    }

    @Test fun calibration() {
        val seen = mutableSetOf<Int>()
        // Turning the phone through many directions fills the progress.
        for (x in -1..1) for (y in -1..1) for (z in -1..1) if (x != 0 || y != 0 || z != 0) seen += Calibration.bin(x * 40f, y * 40f, z * 40f)
        assertEquals(Calibration.BINS, seen.size)
        assertEquals(1f, Calibration.progress(seen))
        assertEquals(0f, Calibration.progress(emptySet()))
        assertEquals(-1, Calibration.bin(0f, 0f, 0f))
        assertEquals(Calibration.Field.NORMAL, Calibration.field(45f))
        assertEquals(Calibration.Field.STRONG, Calibration.field(120f))
        assertEquals(Calibration.Field.WEAK, Calibration.field(10f))
        assertEquals(Calibration.Field.STRONG, Calibration.field(40f, expected = 28f))     // the place says 28 µT
        assertEquals(50f, Calibration.strength(30f, 40f, 0f), 1e-4f)
        assertTrue(Calibration.good(Accuracy.HIGH, emptySet(), Calibration.Field.NORMAL))
        assertTrue(Calibration.good(Accuracy.MEDIUM, seen, Calibration.Field.NORMAL))
        assertFalse(Calibration.good(Accuracy.HIGH, seen, Calibration.Field.STRONG))        // a magnet nearby
        assertFalse(Calibration.good(Accuracy.LOW, setOf(1, 2), Calibration.Field.NORMAL))
    }
}
