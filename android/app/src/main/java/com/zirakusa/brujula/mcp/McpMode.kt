package com.zirakusa.brujula.mcp

import android.content.Context
import android.hardware.GeomagneticField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.zirakusa.brujula.BrujulaApplication
import com.zirakusa.brujula.BuildConfig
import com.zirakusa.brujula.data.Heading
import com.zirakusa.brujula.sensors.CompassSensor
import com.zirakusa.brujula.sensors.LocationWatcher
import kotlinx.coroutines.delay

/**
 * The public relay may run for MCP or for redirection. Only redirection serves the current Arka token.
 * The phone grants local access separately through the signature-protected provider.
 */
object McpMode {
    var remote by mutableStateOf(false); private set
    var enabled by mutableStateOf(false); private set
    var localMcp by mutableStateOf(false); private set
    val active get() = enabled || remote
    var state by mutableStateOf(RelayLink.State.OFFLINE); private set

    private var prefs: android.content.SharedPreferences? = null
    private var link: RelayLink? = null
    private var device = ""
    private var relaySecret = ""
    private var token = Token("")
    internal var relayUrl: String = BuildConfig.RELAY_URL

    fun load(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences("mcp", Context.MODE_PRIVATE)
        prefs = p
        remote = p.getBoolean("redirect", true)
        enabled = p.getBoolean("enabled", false)
        localMcp = p.getBoolean("localMcp", false)
        token = Token(SecretBox.open(p.getString("arka", "") ?: "").ifBlank {
            (Legacy.redirectSecret(SecretBox.open(p.getString("tokens", "") ?: "")) ?: Token.secret())
                .also { p.edit { putString("arka", SecretBox.seal(it)); remove("tokens") } }
        })
        device = p.getString("device", null) ?: Token.id().repeat(2).also { p.edit { putString("device", it) } }
        relaySecret = SecretBox.open(p.getString("relay", "") ?: "").ifBlank {
            Token.secret().also { p.edit { putString("relay", SecretBox.seal(it)) } }
        }
        data = BrujulaApplication.of(app).let { LiveCompass(it.compass, it.location) }
        apply()
    }

    fun enable(on: Boolean) { enabled = on; prefs?.edit { putBoolean("enabled", on) }; apply() }
    fun allowLocal(on: Boolean) { localMcp = on; prefs?.edit { putBoolean("localMcp", on) } }

    fun turnRemote(on: Boolean) { remote = on; prefs?.edit { putBoolean("redirect", on) }; apply() }

    fun hubLink() = "${relayUrl.trimEnd('/')}/d/$device/mcp?token=${token.secret}"

    private var data: CompassData? = null

    /** One JSON-RPC request from Lucía on this phone, through the same handler as the relay. Null before [load]. */
    fun local(body: String): String? {
        val d = data ?: return null
        return kotlinx.coroutines.runBlocking {
            McpHandler({ listOf(token) }, d, BuildConfig.VERSION_NAME).handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${token.secret}"), emptyMap(), body).body
        }
    }

    private fun apply() {
        val d = data ?: return
        if (active && relayUrl.isNotBlank()) {
            if (link == null) link = RelayLink(relayUrl, device, relaySecret,
                McpHandler({ if (remote) listOf(token) else emptyList() }, d, BuildConfig.VERSION_NAME)) { state = it }.also { it.start() }
        } else { link?.stop(); link = null; state = RelayLink.State.OFFLINE }
    }
}

/**
 * The phone's real compass and position for the tools. With the screen open the shown heading is used; with it closed the
 * sensor is turned on for a moment (and off again), and the position is the last one the phone remembers.
 */
class LiveCompass(private val compass: CompassSensor, private val location: LocationWatcher) : CompassData {
    override val hasCompass get() = compass.available
    override val calibrating get() = compass.accuracy.needsCalibration

    override suspend fun heading(): Heading? {
        val fresh = compass.latest?.takeIf { now() - compass.latestAt < FRESH_MS }
        return (fresh ?: wake())?.let(::withDeclination)
    }

    override fun place(): Place {
        if (!location.allowed()) return Place.NoPermission
        val fix = location.latest ?: with(location) { lastKnown()?.toFix() }
        return fix?.let { Place.At(it) } ?: Place.NoFix
    }

    private suspend fun wake(): Heading? {
        val user = Any()
        val since = now()
        compass.start(user)
        try {
            repeat(WAIT_STEPS) { delay(100); if (compass.latestAt > since) { delay(SETTLE_MS); return compass.latest } }
            return null
        } finally { compass.stop(user) }
    }

    /** True north too, from the Earth's magnetic model at the phone's position (when known). */
    private fun withDeclination(h: Heading): Heading {
        val fix = (place() as? Place.At)?.fix ?: return h
        val field = GeomagneticField(fix.latitude.toFloat(), fix.longitude.toFloat(), 0f, fix.time.takeIf { it > 0 } ?: now())
        return h.copy(declination = field.declination)
    }

    private companion object {
        const val FRESH_MS = 2_000L
        const val WAIT_STEPS = 20
        const val SETTLE_MS = 600L
    }
}
