package com.zirakusa.brujula.data


import kotlin.math.sqrt

/**
 * «Calibrar»: how much of the needed movement is done, and whether the magnetic field looks like Earth's (or a magnet,
 * a speaker or metal is near). Pure, with tests.
 */
object Calibration {
    /** The 26 directions of a cube (faces, edges and corners): the phone has to point the magnetometer through them. */
    private val DIRECTIONS: List<Triple<Float, Float, Float>> = buildList {
        for (x in -1..1) for (y in -1..1) for (z in -1..1) if (x != 0 || y != 0 || z != 0) {
            val n = sqrt((x * x + y * y + z * z).toFloat()); add(Triple(x / n, y / n, z / n))
        }
    }
    const val BINS = 26
    /** Enough of the figure-8 is done when this many directions were seen. */
    const val ENOUGH = 18

    /** Which of the 26 directions the field [x], [y], [z] (any units) is closest to; -1 when it is zero. */
    fun bin(x: Float, y: Float, z: Float): Int {
        val n = sqrt(x * x + y * y + z * z)
        if (n == 0f) return -1
        return DIRECTIONS.indices.maxBy { i -> DIRECTIONS[i].let { (a, b, c) -> (a * x + b * y + c * z) / n } }
    }

    /** 0..1 of the figure-8 done, from the directions seen so far. */
    fun progress(seen: Set<Int>) = (seen.count { it in 0 until BINS }.toFloat() / ENOUGH).coerceAtMost(1f)

    enum class Field { NORMAL, WEAK, STRONG }

    /**
     * Is the field (µT) Earth's? Earth's is 25–65 µT; with the expected strength for this place (the phone's own
     * magnetic model) the margin is ±30 %. Too strong: a magnet or metal nearby; too weak: the sensor is off.
     */
    fun field(microtesla: Float, expected: Float? = null): Field {
        val (low, high) = expected?.let { it * 0.7f to it * 1.3f } ?: (25f to 65f)
        return when { microtesla > high -> Field.STRONG; microtesla < low -> Field.WEAK; else -> Field.NORMAL }
    }

    fun strength(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)

    /** The compass can be trusted: the sensor says so, or the move was done and the field is Earth's. */
    fun good(accuracy: Accuracy, seen: Set<Int>, field: Field) =
        field == Field.NORMAL && (accuracy == Accuracy.HIGH || (progress(seen) >= 1f && accuracy != Accuracy.UNRELIABLE))

}
