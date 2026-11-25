package com.zirakusa.brujula.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.zirakusa.brujula.data.Fix

/**
 * Latitude and longitude from Android's own LocationManager (no Google Play services): the last known position at once,
 * then updates every few seconds while someone uses it. Nothing is asked or read without the user's permission.
 */
class LocationWatcher(context: Context) : LocationListener {
    /** Only the application's context is kept (the watcher lives as long as the app). */
    private val app: Context = context.applicationContext
    private val manager = app.getSystemService(LocationManager::class.java)
    private val users = mutableSetOf<Any>()
    var onFix: (Fix) -> Unit = {}
    /** Satellites (used in the position, seen) while the GPS is on; it needs no internet. */
    var onSatellites: (Int, Int) -> Unit = { _, _ -> }
    private val gnss = object : android.location.GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
            val used = (0 until status.satelliteCount).count { status.usedInFix(it) }
            onSatellites(used, status.satelliteCount)
        }
    }
    @Volatile var latest: Fix? = null; private set

    fun allowed() = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun start(user: Any) = synchronized(users) {
        if (!users.add(user) || users.size > 1) return@synchronized
        listen()
    }

    fun stop(user: Any) = synchronized(users) {
        if (users.remove(user) && users.isEmpty()) { manager?.removeUpdates(this); manager?.unregisterGnssStatusCallback(gnss) }
    }

    /** After the permission is granted while the screen is open. */
    fun restart() = synchronized(users) { if (users.isNotEmpty()) { manager?.removeUpdates(this); listen() } }

    private fun listen() {
        val m = manager ?: return
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            lastKnown()?.let(::onLocationChanged)
            // The GPS first: it needs no internet. The network position (Wi‑Fi, mobile) only helps when there is one.
            m.getProviders(true).filter { it in PROVIDERS }.filter { it != LocationManager.PASSIVE_PROVIDER }.forEach {
                m.requestLocationUpdates(it, if (it == LocationManager.GPS_PROVIDER) 2_000L else 5_000L, 1f, this, Looper.getMainLooper())
            }
            if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                m.registerGnssStatusCallback(gnss, android.os.Handler(Looper.getMainLooper()))
        } catch (_: SecurityException) { } catch (_: IllegalArgumentException) { }
    }

    /** The newest position any provider remembers, without turning the GPS on (for Modo MCP while the screen is closed). */
    fun lastKnown(): Location? {
        val m = manager ?: return null
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            m.getProviders(true).filter { it in PROVIDERS }.mapNotNull { m.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (_: SecurityException) { null } catch (_: IllegalArgumentException) { null }
    }

    override fun onLocationChanged(location: Location) {
        val fix = location.toFix()
        val old = latest
        // A late answer from another provider never replaces a newer position.
        if (old != null && fix.time < old.time) return
        // A rough network position never replaces a recent, better GPS one.
        if (old != null && old.provider == LocationManager.GPS_PROVIDER && fix.provider != LocationManager.GPS_PROVIDER &&
            fix.time - old.time < 30_000 && (fix.accuracyM ?: Float.MAX_VALUE) > (old.accuracyM ?: Float.MAX_VALUE)) return
        latest = fix
        onFix(fix)
    }

    fun Location.toFix() = Fix(latitude, longitude, if (hasAccuracy()) accuracy else null, time,
        if (hasAltitude()) altitude else null, provider)

    private fun granted(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private val PROVIDERS = setOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    }
}
