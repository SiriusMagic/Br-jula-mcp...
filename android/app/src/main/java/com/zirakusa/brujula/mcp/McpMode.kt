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
 * Ajustes › Modo MCP, as in Stone, Luma, Calculadora and Notas: «Activar MCP» with the user's tokens (LLM or Hub), or
 * «Redireccionar a Hub», where only the hub's own token is served and the hub manages who may do what. Off by default.
 * Every token is read-only: the compass has nothing to change. Runs while the app is alive.
 */
object McpMode {
    var enabled by mutableStateOf(false); private set
    var hubRedirect by mutableStateOf(false); private set
    var tokens by mutableStateOf(emptyList<Token>()); private set
    var state by mutableStateOf(RelayLink.State.OFFLINE); private set

    private var prefs: android.content.SharedPreferences? = null
    private var link: RelayLink? = null
    private var device = ""
    private var relaySecret = ""
    internal var relayUrl: String = BuildConfig.RELAY_URL
    val active get() = enabled || hubRedirect
    /** The tokens the user manages (the redirect token is never listed). */
    val listed get() = tokens.filter { it.kind != Kind.REDIRECT }

    fun load(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences("mcp", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", false); hubRedirect = p.getBoolean("redirect", false)
        tokens = Token.parse(SecretBox.open(p.getString("tokens", "") ?: "").ifBlank { "[]" })
        device = p.getString("device", null) ?: Token.id().repeat(2).also { p.edit { putString("device", it) } }
        relaySecret = SecretBox.open(p.getString("relay", "") ?: "").ifBlank {
            Token.secret().also { p.edit { putString("relay", SecretBox.seal(it)) } }
        }
        data = BrujulaApplication.of(app).let { LiveCompass(it.compass, it.location) }
        apply()
    }

    fun enable(on: Boolean) { enabled = on; prefs?.edit { putBoolean("enabled", on) }; apply() }
    fun redirect(on: Boolean) { hubRedirect = on; prefs?.edit { putBoolean("redirect", on) }; if (on) redirectToken(); apply() }

    fun create(name: String, kind: Kind, usage: String, days: Int): Token {
        val now = System.currentTimeMillis()
        return Token(Token.id(), name.trim().take(40).ifEmpty { "Agente ${listed.size + 1}" }, Token.secret(), Access.READ, kind,
            usage.trim().take(Token.USAGE_MAX), now, if (days > 0) now + days * 86_400_000L else 0).also { save(tokens + it) }
    }
    fun setPaused(id: String, paused: Boolean) = save(tokens.map { if (it.id == id) it.copy(paused = paused) else it })
    fun revoke(id: String) = save(tokens.filterNot { it.id == id })

    /** The hub's token while redirecting: read-only like every other, never expires, never listed. */
    fun redirectToken(): Token = tokens.firstOrNull { it.kind == Kind.REDIRECT }
        ?: Token(Token.id(), "Gateway Hub", Token.secret(), Access.READ, Kind.REDIRECT, "Redireccionar a Hub", System.currentTimeMillis())
            .also { save(tokens + it) }

    fun served(): List<Token> = tokens.filter { (it.kind == Kind.REDIRECT) == hubRedirect }
    fun linkFor(secret: String) = "${relayUrl.trimEnd('/')}/d/$device/mcp?token=$secret"
    fun hubLink() = linkFor(redirectToken().secret)

    private fun save(list: List<Token>) { tokens = list; prefs?.edit { putString("tokens", SecretBox.seal(Token.serialize(list))) } }

    private var data: CompassData? = null

    /** «Conectar todas» in Gateway Hub (the user's tap there): turns «Redireccionar a Hub» on here. */
    fun connectFromHub() { if (!hubRedirect) redirect(true) }

    /**
     * Lucía (Gateway Hub's voice on this phone) runs one tool without internet, with the hub's own token: the same checks
     * and rules as through the relay. Only while «Redireccionar a Hub» is on. [body] is a JSON-RPC request, so is the answer.
     */
    fun local(body: String): String? {
        val d = data ?: return null
        if (!hubRedirect) return null
        val token = redirectToken()
        return kotlinx.coroutines.runBlocking {
            McpHandler(::served, d, BuildConfig.VERSION_NAME, onUsed = ::touch).handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${token.secret}"), emptyMap(), body).body
        }
    }

    private fun apply() {
        val d = data ?: return
        if (active && relayUrl.isNotBlank()) {
            if (link == null) link = RelayLink(relayUrl, device, relaySecret,
                McpHandler(::served, d, BuildConfig.VERSION_NAME, onUsed = ::touch)) { state = it }.also { it.start() }
        } else { link?.stop(); link = null; state = RelayLink.State.OFFLINE }
    }

    private fun touch(token: Token) {
        val now = System.currentTimeMillis()
        if (now - token.lastUsedAt > 60_000) save(tokens.map { if (it.id == token.id) it.copy(lastUsedAt = now) else it })
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
