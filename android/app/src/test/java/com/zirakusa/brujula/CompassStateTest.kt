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
}
