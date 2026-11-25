package com.zirakusa.brujula

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.zirakusa.brujula.mcp.McpMode
import com.zirakusa.brujula.ui.AppActions
import com.zirakusa.brujula.ui.BrujulaApp
import com.zirakusa.brujula.ui.CompassState
import com.zirakusa.brujula.ui.Page
import com.zirakusa.brujula.ui.theme.BrujulaTheme
import com.zirakusa.brujula.ui.theme.CompassColors

/** Feeds [CompassState] from the compass and the position while the screen is visible; starts Modo MCP. */
class MainActivity : ComponentActivity() {
    private lateinit var state: CompassState
    private val compass by lazy { BrujulaApplication.of(this).compass }
    private val location by lazy { BrujulaApplication.of(this).location }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.locationAllowed = location.allowed()
        location.restart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppIcon.load(this)
        enableEdgeToEdge()
        state = CompassState(getSharedPreferences("ajustes", Context.MODE_PRIVATE), model = { fix ->
            // Android's own World Magnetic Model: declination and expected field for this place, without internet.
            val g = android.hardware.GeomagneticField(fix.latitude.toFloat(), fix.longitude.toFloat(), (fix.altitude ?: 0.0).toFloat(), System.currentTimeMillis())
            com.zirakusa.brujula.ui.Magnetic(g.declination, g.fieldStrength / 1000f)
        })
        state.hasCompass = compass.available
        McpMode.load(this)
        setContent {
            BrujulaTheme(state.themeMode, state.amoled, state.accent) {
                val lightBars = CompassColors.isLight
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).run {
                        isAppearanceLightStatusBars = lightBars; isAppearanceLightNavigationBars = lightBars
                    }
                }
                BackHandler(state.page != Page.COMPASS) { state.back() }
                BrujulaApp(state, AppActions(askLocation = ::askLocation, copy = ::copy, goToHub = ::goToHub, copyText = ::copyCoordinates))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The screen stays on while the compass is in use, like the phone's own.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        compass.displayRotation = ::rotation
        compass.onHeading = state::onHeading
        compass.onAccuracy = { runOnUiThread { state.onAccuracy(it) } }
        compass.onField = state::onField
        compass.start(this)
        state.locationAllowed = location.allowed()
        location.onFix = state::onFix
        location.onSatellites = state::onSatellites
        location.latest?.let(state::onFix)
        location.start(this)
    }

    override fun onStop() {
        compass.stop(this); location.stop(this)
        compass.onHeading = {}; compass.onAccuracy = {}; compass.onField = { _, _, _ -> }; location.onFix = {}; location.onSatellites = { _, _ -> }
        super.onStop()
    }

    /** Asked only when the user taps «Mostrar mi ubicación»; refusing keeps the compass working. */
    private fun askLocation() =
        permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

    private fun rotation(): Int =
        if (Build.VERSION.SDK_INT >= 30) display?.rotation ?: Surface.ROTATION_0
        else @Suppress("DEPRECATION") windowManager.defaultDisplay.rotation

    private fun copy(link: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Brújula MCP", link))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun copyCoordinates(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Brújula", text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    /** «Ir al Hub»: copies the hub's link (in case it must be pasted) and opens the Gateway Hub app. */
    private fun goToHub() {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Brújula › Gateway Hub", McpMode.hubLink()))
        val open = packageManager.getLaunchIntentForPackage("com.zirakusa.gatewayhub")
        if (open != null) startActivity(open) else Toast.makeText(this, R.string.mcp_paste_in_hub, Toast.LENGTH_LONG).show()
    }
}
