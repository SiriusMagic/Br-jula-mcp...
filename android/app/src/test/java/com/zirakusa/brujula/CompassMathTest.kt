package com.zirakusa.brujula

import com.zirakusa.brujula.data.Accuracy
import com.zirakusa.brujula.data.AngleFilter
import com.zirakusa.brujula.data.Angles
import com.zirakusa.brujula.data.Directions
import com.zirakusa.brujula.data.Dms
import com.zirakusa.brujula.data.Heading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The compass's pure rules: angles on the circle, smoothing, direction names, degrees-minutes-seconds, accuracy. */
class CompassMathTest {
    @Test fun anglesWrapAndTakeTheShortWay() {
        assertEquals(0f, Angles.wrap(360f), 0f)
        assertEquals(350f, Angles.wrap(-10f), 1e-4f)
        assertEquals(10f, Angles.wrap(730f), 1e-4f)
        assertEquals(2f, Angles.diff(359f, 1f), 1e-4f)
        assertEquals(-2f, Angles.diff(1f, 359f), 1e-4f)
        assertEquals(180f, Angles.diff(0f, 180f), 1e-4f)
        assertEquals(-90f, Angles.diff(90f, 0f), 1e-4f)
    }

    @Test fun filterSmoothsAcrossNorthWithoutSpinning() {
        val f = AngleFilter(alpha = 0.5f, deadband = 0f)
        assertEquals(350f, f.update(350f), 0f)           // the first reading is taken as it is
        assertEquals(355f, f.update(0f), 1e-3f)          // half of the 10° turn, through north
        assertEquals(357.5f, f.update(0f), 1e-3f)
        val g = AngleFilter(alpha = 0.5f, deadband = 0f).apply { update(359f) }
        assertEquals(0f, g.update(1f), 1e-3f)            // 359 → 1 goes 1° forward, never 179° back
        val still = AngleFilter(alpha = 0.5f, deadband = 0.5f).apply { update(100f) }
        assertEquals(100f, still.update(100.3f), 0f)     // tiny jitter ignored
        still.reset(); assertNull(still.value)
    }

    @Test fun eightAndSixteenPointNames() {
        assertEquals("Norte", Directions.spanish(0f))
        assertEquals("Norte", Directions.spanish(22.4f))
        assertEquals("Noreste", Directions.spanish(22.5f))
        assertEquals("Norte", Directions.spanish(337.5f))
        assertEquals("Noroeste", Directions.spanish(337.4f))
        assertEquals("Noroeste", Directions.spanish(288f + 20f))
        assertEquals("Oeste", Directions.spanish(288f))
        assertEquals("Sur", Directions.spanish(180f))
        assertEquals("Oestenoroeste", Directions.spanish(288f, 16))
        assertEquals("Nornoroeste", Directions.spanish(340f, 16))
        assertEquals("Norte", Directions.spanish(355f, 16))
        assertEquals("WNW", Directions.short(288f, 16))
        assertEquals("NW", Directions.short(315f))
        assertEquals(14, Directions.index16(315f, 8))
        assertEquals(7, Directions.index(-45f, 8))
    }

    @Test fun degreesMinutesSeconds() {
        assertEquals("3°36′23″", Dms.format(-3.606389))
        assertEquals("79°05′07″", Dms.format(-79.085278))
        assertEquals("0°00′00″", Dms.format(0.0))
        assertEquals("4°00′00″", Dms.format(3.99999))      // 59.96″ carries into the minute and the degree
        assertEquals(Dms.Parts(12, 30, 0), Dms.parts(12.5))
        assertFalse(Dms.isNorth(-3.6)); assertTrue(Dms.isNorth(0.0))
        assertFalse(Dms.isEast(-79.0)); assertTrue(Dms.isEast(2.35))
    }

    @Test fun accuracyAndTrueNorth() {
        assertEquals(Accuracy.HIGH, Accuracy.fromStatus(3))
        assertEquals(Accuracy.MEDIUM, Accuracy.fromStatus(2))
        assertEquals(Accuracy.LOW, Accuracy.fromStatus(1))
        assertEquals(Accuracy.UNRELIABLE, Accuracy.fromStatus(0))
        assertEquals(Accuracy.UNRELIABLE, Accuracy.fromStatus(-1))
        assertTrue(Accuracy.LOW.needsCalibration); assertTrue(Accuracy.UNRELIABLE.needsCalibration)
        assertFalse(Accuracy.MEDIUM.needsCalibration); assertFalse(Accuracy.HIGH.needsCalibration)
        assertNull(Heading(10f, Accuracy.HIGH).trueDegrees)
        assertEquals(355f, Heading(10f, Accuracy.HIGH, declination = -15f).trueDegrees!!, 1e-4f)
    }
}
