package com.zirakusa.brujula.ui

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.zirakusa.brujula.data.Accuracy
import com.zirakusa.brujula.data.Angles
import com.zirakusa.brujula.data.Calibration
import com.zirakusa.brujula.data.Geo
import com.zirakusa.brujula.data.LatLon
import com.zirakusa.brujula.data.Place
import com.zirakusa.brujula.data.Places
import com.zirakusa.brujula.data.Sun
import com.zirakusa.brujula.data.Fix
import com.zirakusa.brujula.data.Heading
import com.zirakusa.brujula.ui.theme.Accent
import com.zirakusa.brujula.ui.theme.ThemeMode

enum class Page { COMPASS, LOCATION, CALIBRATION, SETTINGS, MCP }

/** What the phone's magnetic model (no internet) says for a place: declination (east positive) and the field (µT). */
data class Magnetic(val declination: Float, val expectedMicrotesla: Float)

/**
 * The only owner of what the screens show: the heading, the sensor's accuracy, the position and the page. The activity
 * feeds it from the sensors; tests set it by hand. «Calibrar la Brújula» opens by itself when the accuracy drops to low
 * or unreliable and closes by itself when it is high again. Ajustes (Temas, Rumbos) are kept in [prefs].
 */
class CompassState(
    private val prefs: SharedPreferences,
    /** The phone's World Magnetic Model for a position (GeomagneticField); null in tests without one. */
    private val model: (Fix) -> Magnetic? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var heading by mutableFloatStateOf(0f); private set
    var hasReading by mutableStateOf(false); private set
    var hasCompass by mutableStateOf(true)
    var accuracy by mutableStateOf(Accuracy.HIGH); private set
    var fix by mutableStateOf<Fix?>(null); private set
    var locationAllowed by mutableStateOf(false)
    var page by mutableStateOf(Page.COMPASS)
    /** Satellites used in the position and seen (GPS, no internet needed); null until the GPS answers. */
    var satellites by mutableStateOf<Pair<Int, Int>?>(null); private set
    var magnetic by mutableStateOf<Magnetic?>(null); private set
    /** North from the map (true) instead of the magnetic one, when the position is known. On by default. */
    var trueNorth by mutableStateOf(prefs.getBoolean(TRUE_NORTH, true)); private set
    var places by mutableStateOf(Places.read(prefs.getString(PLACES, null))); private set
    /** The place the compass points to («Ir»), by index in [places]. */
    var target by mutableStateOf<Int?>(prefs.getInt(TARGET, -1).takeIf { it >= 0 }); private set
    // «Calibrar»: the field now (µT) and the directions the phone has been turned through.
    var field by mutableFloatStateOf(0f); private set
    var seen by mutableStateOf(emptySet<Int>()); private set
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
    fun onFix(next: Fix) { fix = next; magnetic = model(next) }
    fun onSatellites(used: Int, total: Int) { satellites = used to total }

    /** Raw magnetometer reading: its strength, and (while «Calibrar» is open) one more direction seen. */
    fun onField(x: Float, y: Float, z: Float) {
        field = Calibration.strength(x, y, z)
        if (page == Page.CALIBRATION) Calibration.bin(x, y, z).takeIf { it >= 0 && it !in seen }?.let { seen = seen + it }
    }

    val fieldStatus get() = Calibration.field(field, magnetic?.expectedMicrotesla)
    val calibrated get() = Calibration.good(accuracy, seen, fieldStatus)

    /** True north is used when it is chosen and the declination is known (it needs the position, not internet). */
    val usingTrueNorth get() = trueNorth && magnetic != null
    /** The heading shown: from true north when [usingTrueNorth], else magnetic. */
    val shownHeading get() = magnetic?.takeIf { trueNorth }?.let { Geo.trueHeading(heading, it.declination) } ?: heading

    /** A direction from true north, turned into the frame the dial uses (magnetic when the declination is unknown). */
    fun shown(trueBearing: Float) = if (usingTrueNorth) trueBearing else Angles.wrap(trueBearing - (magnetic?.declination ?: 0f))

    val targetPlace get() = target?.let { places.getOrNull(it) }
    /** Distance (m) and direction (degrees, frame of the dial) to the chosen place, when the position is known. */
    val toTarget: Pair<Double, Float>? get() {
        val from = fix?.at ?: return null; val to = targetPlace?.at ?: return null
        return Geo.distance(from, to) to shown(Geo.bearing(from, to))
    }
    /** The sun's direction (frame of the dial) while it is up: north without trusting the magnetometer. */
    val sun: Float? get() = fix?.let { f -> Sun.at(f.at, clock()).takeIf { it.up }?.let { shown(it.azimuth) } }

    fun changeTrueNorth(on: Boolean) { trueNorth = on; prefs.edit { putBoolean(TRUE_NORTH, on) } }

    /** Saves where the phone is now; false when there is no position yet. */
    fun saveHere(name: String): Boolean {
        val at = fix?.at ?: return false
        return addPlace(name, at)
    }

    fun addPlace(name: String, at: LatLon): Boolean {
        if (places.size >= Places.MAX) return false
        putPlaces(places + Place(Places.clean(name, places.size + 1), at)); return true
    }

    fun removePlace(index: Int) {
        if (index !in places.indices) return
        putPlaces(places.filterIndexed { i, _ -> i != index })
        target = target?.let { t -> when { t == index -> null; t > index -> t - 1; else -> t } }
        prefs.edit { putInt(TARGET, target ?: -1) }
    }

    /** «Ir»: the compass points to it (again: stop). */
    fun goTo(index: Int?) { target = index?.takeIf { it in places.indices && it != target }; prefs.edit { putInt(TARGET, target ?: -1) } }

    private fun putPlaces(next: List<Place>) { places = next; prefs.edit { putString(PLACES, Places.write(next)) } }

    fun onAccuracy(next: Accuracy) {
        val was = accuracy
        accuracy = next
        when {
            next.needsCalibration && !was.needsCalibration && page == Page.COMPASS -> openCalibration()
            next == Accuracy.HIGH && page == Page.CALIBRATION && fieldStatus != Calibration.Field.STRONG -> closeCalibration()
        }
    }

    fun openCalibration(from: Page = page) { calibrationBack = if (from == Page.SETTINGS) Page.SETTINGS else Page.COMPASS; seen = emptySet(); page = Page.CALIBRATION }
    fun closeCalibration() { page = calibrationBack }

    /** The system back button: true when it was used here (the app stays open). */
    fun back(): Boolean {
        page = when (page) {
            Page.COMPASS -> return false
            Page.CALIBRATION -> calibrationBack
            Page.LOCATION -> Page.COMPASS
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
        const val TRUE_NORTH = "true_north"
        const val PLACES = "places"
        const val TARGET = "target"
    }
}
