package com.zirakusa.brujula

import android.app.Application
import android.content.Context
import com.zirakusa.brujula.sensors.CompassSensor
import com.zirakusa.brujula.sensors.LocationWatcher

/** One compass and one location for the whole app: the screen and Modo MCP share them. */
class BrujulaApplication : Application() {
    val compass by lazy { CompassSensor(this) }
    val location by lazy { LocationWatcher(this) }

    companion object {
        fun of(context: Context) = context.applicationContext as BrujulaApplication
    }
}
