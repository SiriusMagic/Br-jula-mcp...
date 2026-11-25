package com.zirakusa.brujula

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.brujula.data.Accuracy
import com.zirakusa.brujula.data.Fix
import com.zirakusa.brujula.data.Heading
import com.zirakusa.brujula.ui.BrujulaApp
import com.zirakusa.brujula.ui.CompassState
import com.zirakusa.brujula.ui.Page
import com.zirakusa.brujula.ui.theme.Accent
import com.zirakusa.brujula.ui.theme.BrujulaTheme
import com.zirakusa.brujula.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private fun state() = CompassState(prefs).apply {
        onHeading(Heading(288f, Accuracy.HIGH))
        locationAllowed = true
        onFix(Fix(-3.606389, -79.085278, 12f))
    }

    @Test fun compassCalibrationSettingsAndMcp() {
        val state = state()
        compose.setContent { BrujulaTheme(state.themeMode, state.amoled, state.accent) { BrujulaApp(state) } }
        compose.onNodeWithText("288°").assertExists()
        compose.onNodeWithText("Oeste").assertExists()
        compose.onNodeWithText("Latitud sur").assertExists()
        compose.onNodeWithText("Longitud oeste").assertExists()
        onView(isRoot()).captureRoboImage("build/screenshots/brujula-compass.png")
        state.onAccuracy(Accuracy.LOW)
        compose.onNodeWithText("Calibrar la Brújula").assertExists()
        onView(isRoot()).captureRoboImage("build/screenshots/brujula-calibration.png")
        state.onAccuracy(Accuracy.HIGH)
        compose.onNodeWithText("288°").assertExists()
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithText("Temas").assertExists()
        onView(isRoot()).captureRoboImage("build/screenshots/brujula-settings.png")
        compose.onNodeWithText("16").performClick()
        assertEquals(16, state.points)
        compose.onNodeWithText("Modo MCP").performClick()
        compose.onNodeWithText("Redireccionar a Hub").assertExists()
        onView(isRoot()).captureRoboImage("build/screenshots/brujula-mcp.png")
        compose.onNodeWithText("Crear token").performClick()
        compose.onNodeWithText("Crear y copiar enlace").assertExists()
        onView(isRoot()).captureRoboImage("build/screenshots/brujula-mcp-token.png")
        compose.onNodeWithText("Cancelar").performClick()
        state.page = Page.COMPASS
        compose.onNodeWithText("Oestenoroeste").assertExists()
    }

    /** Offline position, true north, «Ir a» a saved place, the sun on the dial, and calibration with its progress. */
    @Test fun locationPlacesAndCalibration() {
        prefs.edit().clear().commit()
        val noon = java.time.ZonedDateTime.parse("2026-11-21T19:30:00Z").toInstant().toEpochMilli()        // 14:30 in Cuenca
        val state = CompassState(prefs, model = { com.zirakusa.brujula.ui.Magnetic(-3.2f, 28f) }, clock = { noon }).apply {
            onHeading(Heading(300f, Accuracy.HIGH)); locationAllowed = true
            onFix(Fix(-2.9001, -79.0059, 4f, noon - 3_000, 2560.0, "gps")); onSatellites(11, 27)
            addPlace("Coche", com.zirakusa.brujula.data.LatLon(-2.8950, -79.0102)); addPlace("Campamento", com.zirakusa.brujula.data.LatLon(-2.95, -79.2))
            goTo(0)
        }
        compose.setContent { BrujulaTheme(state.themeMode, state.amoled, state.accent) { BrujulaApp(state, com.zirakusa.brujula.ui.AppActions(now = { noon })) } }
        compose.onNodeWithText("Norte verdadero · declinación 3,2° O").assertExists()
        compose.onNodeWithText("Ir a Coche", substring = true).assertExists()
        compose.waitForIdle(); onView(isRoot()).captureRoboImage("build/screenshots/brujula-goto.png")
        state.page = Page.LOCATION
        compose.onNodeWithText("-2.90010, -79.00590").assertExists()
        compose.onNodeWithText("11 en uso de 27").assertExists()
        compose.onNodeWithText("Parar").assertExists()
        compose.waitForIdle(); onView(isRoot()).captureRoboImage("build/screenshots/brujula-location.png")
        state.openCalibration()
        state.onField(30f, 0f, 0f); state.onField(0f, 28f, 0f); state.onField(0f, 0f, 29f); state.onField(20f, 20f, 0f)
        state.onField(90f, 0f, 0f)                                                         // a magnet nearby
        compose.onNodeWithText("Movimiento", substring = true).assertExists()
        compose.waitForIdle(); onView(isRoot()).captureRoboImage("build/screenshots/brujula-calibration-progress.png")
    }

    @Test fun askLocationPolitely() {
        val state = CompassState(prefs).apply { onHeading(Heading(3f, Accuracy.HIGH)) }
        var asked = 0
        compose.setContent { BrujulaTheme { BrujulaApp(state, com.zirakusa.brujula.ui.AppActions(askLocation = { asked++ })) } }
        compose.onNodeWithText("Norte").assertExists()
        onView(isRoot()).captureRoboImage("build/screenshots/brujula-no-location.png")
        compose.onNodeWithText("Mostrar mi ubicación").performClick()
        assertEquals(1, asked)
    }

    @Test fun lightThemeWithBlueAccent() {
        val state = state()
        state.changeTheme(ThemeMode.LIGHT); state.changeAccent(Accent.AZUL)
        compose.setContent { BrujulaTheme(state.themeMode, state.amoled, state.accent) { BrujulaApp(state) } }
        compose.onNodeWithText("288°").assertExists()
        onView(isRoot()).captureRoboImage("build/screenshots/brujula-light.png")
        state.page = Page.SETTINGS
        compose.onNodeWithText("Oscuro").performClick()
        assertEquals(ThemeMode.DARK, state.themeMode)
        compose.onNodeWithContentDescription("Verde").performClick()
        assertEquals(Accent.VERDE, state.accent)
        compose.waitForIdle()
        onView(isRoot()).captureRoboImage("build/screenshots/brujula-settings-green.png")
    }
}
