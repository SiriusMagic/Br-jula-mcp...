package com.zirakusa.brujula.ui

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.zirakusa.brujula.data.Accuracy
import com.zirakusa.brujula.data.Fix
import com.zirakusa.brujula.data.Heading
import com.zirakusa.brujula.ui.theme.Accent
import com.zirakusa.brujula.ui.theme.ThemeMode

enum class Page { COMPASS, CALIBRATION, SETTINGS, MCP }

/**
 * The only owner of what the screens show: the heading, the sensor's accuracy, the position and the page. The activity
 * feeds it from the sensors; tests set it by hand. «Calibrar la Brújula» opens by itself when the accuracy drops to low
 * or unreliable and closes by itself when it is high again. Ajustes (Temas, Rumbos) are kept in [prefs].
 */
class CompassState(private val prefs: SharedPreferences) {
    var heading by mutableFloatStateOf(0f); private set
    var hasReading by mutableStateOf(false); private set
    var hasCompass by mutableStateOf(true)
    var accuracy by mutableStateOf(Accuracy.HIGH); private set
    var fix by mutableStateOf<Fix?>(null); private set
    var locationAllowed by mutableStateOf(false)
    var page by mutableStateOf(Page.COMPASS)
    /** Where «Calibrar» goes back to (the compass, or Ajustes when opened from there). */
    private var calibrationBack = Page.COMPASS

    var themeMode by mutableStateOf(prefs.getString(THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK)
        private set
    var accent by mutableStateOf(prefs.getString(ACCENT, null)?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.ROJO)
        private set
    var amoled by mutableStateOf(prefs.getBoolean(AMOLED, true)); private set
    /** Direction names with 8 (Noroeste) or 16 (Nornoroeste) points. */
    var points by mutableIntStateOf(prefs.getInt(POINTS, 8).takeIf { it == 16 } ?: 8); private set

    fun changeTheme(mode: ThemeMode) { themeMode = mode; prefs.edit { putString(THEME, mode.name) } }
    fun changeAccent(next: Accent) { accent = next; prefs.edit { putString(ACCENT, next.name) } }
    fun changeAmoled(on: Boolean) { amoled = on; prefs.edit { putBoolean(AMOLED, on) } }
    fun changePoints(next: Int) { points = if (next == 16) 16 else 8; prefs.edit { putInt(POINTS, points) } }

    fun onHeading(h: Heading) { heading = h.degrees; hasReading = true }
    fun onFix(next: Fix) { fix = next }

    fun onAccuracy(next: Accuracy) {
        val was = accuracy
        accuracy = next
        when {
            next.needsCalibration && !was.needsCalibration && page == Page.COMPASS -> openCalibration()
            next == Accuracy.HIGH && page == Page.CALIBRATION -> closeCalibration()
        }
    }

    fun openCalibration(from: Page = page) { calibrationBack = if (from == Page.SETTINGS) Page.SETTINGS else Page.COMPASS; page = Page.CALIBRATION }
    fun closeCalibration() { page = calibrationBack }

    /** The system back button: true when it was used here (the app stays open). */
    fun back(): Boolean {
        page = when (page) {
            Page.COMPASS -> return false
            Page.CALIBRATION -> calibrationBack
            Page.MCP -> Page.SETTINGS
            Page.SETTINGS -> Page.COMPASS
        }
        return true
    }

    private companion object {
        const val THEME = "theme"
        const val ACCENT = "accent"
        const val AMOLED = "amoled"
        const val POINTS = "points"
    }
}
