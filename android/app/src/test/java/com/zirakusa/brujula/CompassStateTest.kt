package com.zirakusa.brujula

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.brujula.data.Accuracy
import com.zirakusa.brujula.data.Heading
import com.zirakusa.brujula.ui.CompassState
import com.zirakusa.brujula.ui.Page
import com.zirakusa.brujula.ui.theme.Accent
import com.zirakusa.brujula.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Calibration opens and closes by itself; back goes where it should; Ajustes are remembered. */
@RunWith(RobolectricTestRunner::class)
class CompassStateTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    @Test fun calibrationFollowsTheAccuracy() {
        val s = CompassState(prefs)
        s.onHeading(Heading(288f, Accuracy.HIGH))
        assertTrue(s.hasReading); assertEquals(288f, s.heading, 0f)
        s.onAccuracy(Accuracy.LOW)
        assertEquals(Page.CALIBRATION, s.page)
        s.onAccuracy(Accuracy.MEDIUM)
        assertEquals(Page.CALIBRATION, s.page)            // medium is not enough to close it
        s.onAccuracy(Accuracy.HIGH)
        assertEquals(Page.COMPASS, s.page)                // back by itself
        s.page = Page.SETTINGS
        s.onAccuracy(Accuracy.UNRELIABLE)
        assertEquals(Page.SETTINGS, s.page)               // never interrupts Ajustes
        s.page = Page.COMPASS
        s.onAccuracy(Accuracy.LOW)
        assertEquals(Page.COMPASS, s.page)                // only when it drops, not again while it stays low
    }

    @Test fun calibrateFromSettingsGoesBackThere() {
        val s = CompassState(prefs)
        s.page = Page.SETTINGS
        s.openCalibration(Page.SETTINGS)
        assertEquals(Page.CALIBRATION, s.page)
        assertTrue(s.back()); assertEquals(Page.SETTINGS, s.page)
        s.page = Page.MCP
        assertTrue(s.back()); assertEquals(Page.SETTINGS, s.page)
        assertTrue(s.back()); assertEquals(Page.COMPASS, s.page)
        assertFalse(s.back())
    }

    @Test fun settingsAreRemembered() {
        val s = CompassState(prefs)
        assertEquals(ThemeMode.DARK, s.themeMode); assertEquals(Accent.ROJO, s.accent); assertTrue(s.amoled); assertEquals(8, s.points)
        s.changeTheme(ThemeMode.LIGHT); s.changeAccent(Accent.AZUL); s.changeAmoled(false); s.changePoints(16)
        val again = CompassState(prefs)
        assertEquals(ThemeMode.LIGHT, again.themeMode); assertEquals(Accent.AZUL, again.accent)
        assertFalse(again.amoled); assertEquals(16, again.points)
    }

    private fun fresh(model: (com.zirakusa.brujula.data.Fix) -> com.zirakusa.brujula.ui.Magnetic? = { com.zirakusa.brujula.ui.Magnetic(-3f, 28f) }): CompassState {
        prefs.edit().clear().commit()
        return CompassState(prefs, model, clock = { java.time.ZonedDateTime.parse("2026-03-20T12:00:00Z").toInstant().toEpochMilli() })
    }

    @Test fun trueNorthNeedsOnlyThePosition() {
        val s = fresh()
        s.onHeading(Heading(10f, Accuracy.HIGH))
        assertEquals(10f, s.shownHeading, 1e-4f); assertFalse(s.usingTrueNorth)          // no position yet: magnetic
        s.onFix(com.zirakusa.brujula.data.Fix(-0.18, -78.47, 5f, 1, 2800.0, "gps"))
        assertTrue(s.usingTrueNorth); assertEquals(7f, s.shownHeading, 1e-4f)            // 10° − 3° of declination
        s.changeTrueNorth(false)
        assertEquals(10f, s.shownHeading, 1e-4f)
        assertFalse(CompassState(prefs).trueNorth)                                         // remembered
        s.onSatellites(9, 24); assertEquals(9 to 24, s.satellites)
    }

    @Test fun placesAndGoTo() {
        val s = fresh()
        assertFalse(s.saveHere("Coche"))                                                   // no position yet
        s.onFix(com.zirakusa.brujula.data.Fix(-0.18, -78.47, 5f, 1, null, "gps"))
        assertTrue(s.saveHere("Coche"))
        assertTrue(s.addPlace("", com.zirakusa.brujula.data.LatLon(-0.17, -78.47)))        // ~1.1 km north
        assertEquals(listOf("Coche", "Lugar 2"), s.places.map { it.name })
        s.goTo(1)
        val (metres, dir) = s.toTarget!!
        assertEquals(1112.0, metres, 20.0)
        assertEquals(0f, dir, 0.5f)                                                        // due north (true north is in use)
        assertEquals("Lugar 2", CompassState(prefs).targetPlace?.name)                     // remembered
        s.goTo(1); assertEquals(null, s.target)                                            // again: stop
        s.goTo(1); s.removePlace(0); assertEquals(0, s.target)                              // the index follows
        s.removePlace(0); assertEquals(null, s.target); assertTrue(s.places.isEmpty())
        assertTrue(s.sun != null)                                                           // 07:00 in Quito: the sun is up
    }

    @Test fun calibrationProgressAndInterference() {
        val s = fresh()
        s.openCalibration()
        assertTrue(s.seen.isEmpty())
        s.onField(40f, 0f, 0f); s.onField(0f, 40f, 0f); s.onField(0f, 0f, -40f)
        assertEquals(3, s.seen.size); assertEquals(40f, s.field, 1e-3f)
        s.onFix(com.zirakusa.brujula.data.Fix(-0.18, -78.47))
        assertEquals(com.zirakusa.brujula.data.Calibration.Field.STRONG, s.fieldStatus)    // 40 µT where 28 is expected
        s.onAccuracy(Accuracy.HIGH)
        assertEquals(Page.CALIBRATION, s.page)                                              // stays open: a magnet nearby
        s.onField(28f, 0f, 0f)
        assertTrue(s.calibrated)
    }
}
