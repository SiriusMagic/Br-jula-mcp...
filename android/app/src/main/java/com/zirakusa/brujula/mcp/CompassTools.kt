package com.zirakusa.brujula.mcp

import com.zirakusa.brujula.data.Directions
import com.zirakusa.brujula.data.Dms
import com.zirakusa.brujula.data.Fix
import com.zirakusa.brujula.data.Heading
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.roundToLong

/** Where the phone is, as the tools see it. */
sealed interface Place {
    data object NoPermission : Place
    data object NoFix : Place
    data class At(val fix: Fix) : Place
}

/** What the tools read; the app gives the phone's sensors (see LiveCompass), tests a fake. Nothing here can change anything. */
interface CompassData {
    val hasCompass: Boolean
    /** A fresh heading, waiting a moment for the sensor if the screen is closed; null when none came in time. */
    suspend fun heading(): Heading?
    /** True while the magnetometer asks to be calibrated (accuracy low or unreliable). */
    val calibrating: Boolean
    fun place(): Place
    fun now(): Long = System.currentTimeMillis()
}

/** An expected failure the AI can read (structuredContent.error.code), never a crash. */
class ToolFailure(val code: String, message: String) : Exception(message)

/**
 * The compass's MCP tools, all read-only: the heading and the position. Arguments are checked one by one (an unknown
 * one is refused); nothing is ever run as code.
 */
object CompassTools {
    class Tool(val name: String, val description: String, val schema: JSONObject)

    private fun schema(vararg props: Pair<String, JSONObject>) = JSONObject()
        .put("type", "object").put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
        .put("required", JSONArray()).put("additionalProperties", false)

    val tools = listOf(
        Tool("compass_heading", "Where the phone points: degrees clockwise from magnetic north (and true north when the position " +
            "is known), the direction's name, the sensor's accuracy and whether it needs calibrating (figure-8).",
            schema("points" to JSONObject().put("type", "integer").put("enum", JSONArray(listOf(8, 16)))
                .put("description", "Direction names with 8 (Noroeste) or 16 (Nornoroeste) points. Default 8."))),
        Tool("compass_location", "The phone's latitude and longitude, decimal and in degrees-minutes-seconds, with the accuracy " +
            "in metres. Fails with NO_PERMISSION if the user has not allowed location, NO_FIX if there is no position yet.", schema()),
    )

    suspend fun call(name: String, args: JSONObject, data: CompassData): JSONObject {
        val tool = tools.firstOrNull { it.name == name } ?: throw ToolFailure("UNKNOWN_TOOL", "No tool called «$name».")
        val allowed = tool.schema.getJSONObject("properties").keys().asSequence().toSet()
        args.keys().forEach { if (it !in allowed) throw ToolFailure("BAD_ARGUMENT", "Unknown argument «$it».") }
        return when (name) {
            "compass_heading" -> heading(points(args), data)
            else -> location(data)
        }
    }

    private fun points(args: JSONObject): Int {
        if (!args.has("points") || args.isNull("points")) return 8
        return (args.opt("points") as? Int)?.takeIf { it == 8 || it == 16 }
            ?: throw ToolFailure("BAD_ARGUMENT", "«points» must be 8 or 16.")
    }

    private suspend fun heading(points: Int, data: CompassData): JSONObject {
        if (!data.hasCompass) throw ToolFailure("NO_SENSOR", "This phone has no compass (magnetometer).")
        val h = data.heading() ?: throw ToolFailure("NO_READING", "The compass gave no reading. Try again in a moment.")
        return JSONObject().put("degrees", round(h.degrees.toDouble(), 1)).put("reference", "magnetic north")
            .put("true_degrees", h.trueDegrees?.let { round(it.toDouble(), 1) } ?: JSONObject.NULL)
            .put("declination", h.declination?.let { round(it.toDouble(), 1) } ?: JSONObject.NULL)
            .put("direction", Directions.spanish(h.degrees, points)).put("direction_short", Directions.short(h.degrees, points))
            .put("points", points).put("accuracy", h.accuracy.key).put("calibrating", data.calibrating)
    }

    private fun location(data: CompassData): JSONObject = when (val p = data.place()) {
        Place.NoPermission -> throw ToolFailure("NO_PERMISSION", "The user has not allowed location for Brújula.")
        Place.NoFix -> throw ToolFailure("NO_FIX", "No position yet (no GPS or network fix). Try again outdoors in a moment.")
        is Place.At -> {
            val f = p.fix
            JSONObject().put("latitude", round(f.latitude, 6)).put("longitude", round(f.longitude, 6))
                .put("latitude_dms", Dms.format(f.latitude) + if (Dms.isNorth(f.latitude)) " N" else " S")
                .put("longitude_dms", Dms.format(f.longitude) + if (Dms.isEast(f.longitude)) " E" else " W")
                .put("accuracy_m", f.accuracyM?.let { round(it.toDouble(), 1) } ?: JSONObject.NULL)
                .put("time", if (f.time > 0) Instant.ofEpochMilli(f.time).toString() else JSONObject.NULL)
                .put("age_seconds", if (f.time > 0) ((data.now() - f.time) / 1000).coerceAtLeast(0) else JSONObject.NULL)
        }
    }

    private fun round(value: Double, decimals: Int): Double {
        val scale = Math.pow(10.0, decimals.toDouble())
        return (value * scale).roundToLong() / scale
    }
}
