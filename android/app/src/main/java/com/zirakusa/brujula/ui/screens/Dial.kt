package com.zirakusa.brujula.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.brujula.ui.theme.CompassColors

/**
 * The dial, like the phone's compass: a mark every 2°, a longer one every 30° with its number outside, the letters
 * N (with a small triangle in the accent) E S O inside, all turning with the heading so north stays north. The accent line
 * at the top stays still: it is where the phone points. Optional: the sun (yellow) and the place of «Ir a» (green arrow),
 * as directions in the same frame as [heading].
 */
@Composable fun Dial(heading: Float, letters: List<String>, modifier: Modifier = Modifier, target: Float? = null, sun: Float? = null) {
    val measurer = rememberTextMeasurer()
    val text = CompassColors.Text
    val tick = CompassColors.Tick
    val accent = CompassColors.Accent
    val numbers = remember(measurer) {
        (0 until 360 step 30).map { measurer.measure(it.toString(), TextStyle(fontSize = 14.sp)) }
    }
    val names = remember(measurer, letters) {
        letters.map { measurer.measure(it, TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium)) }
    }
    Canvas(modifier.aspectRatio(1f)) {
        val r = size.minDimension / 2
        val c = center
        rotate(-heading, c) {
            for (deg in 0 until 360 step 2) {
                val long = deg % 30 == 0
                rotate(deg.toFloat(), c) {
                    drawLine(if (long) text else tick, Offset(c.x, c.y - r * 0.88f), Offset(c.x, c.y - r * (if (long) 0.74f else 0.79f)),
                        strokeWidth = (if (long) 2.4f else 1.3f).dp.toPx(), cap = StrokeCap.Round)
                }
            }
            numbers.forEachIndexed { i, layout -> rotate(i * 30f, c) { centred(layout, text, c.y - r * 0.955f) } }
            names.forEachIndexed { i, layout -> rotate(i * 90f, c) { centred(layout, text, c.y - r * 0.56f) } }
            val tip = c.y - r * 0.715f
            val half = 7.dp.toPx()
            drawPath(Path().apply { moveTo(c.x, tip); lineTo(c.x - half, tip + half * 1.5f); lineTo(c.x + half, tip + half * 1.5f); close() }, accent)
            // The sun, where it is now: north without trusting the magnetometer.
            sun?.let { rotate(it, c) { drawCircle(SUN, 9.dp.toPx(), Offset(c.x, c.y - r * 0.64f)); drawCircle(SUN.copy(alpha = .3f), 15.dp.toPx(), Offset(c.x, c.y - r * 0.64f)) } }
            // «Ir a»: a big arrow towards the chosen place.
            target?.let {
                rotate(it, c) {
                    val t = c.y - r * 0.70f; val w = 13.dp.toPx()
                    drawPath(Path().apply { moveTo(c.x, t - w * 0.6f); lineTo(c.x - w, t + w * 1.4f); lineTo(c.x, t + w * 0.8f); lineTo(c.x + w, t + w * 1.4f); close() }, GOAL)
                }
            }
        }
        drawLine(accent, Offset(c.x, c.y - r * 0.995f), Offset(c.x, c.y - r * 0.70f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

private val SUN = Color(0xFFFFC53D)
private val GOAL = Color(0xFF34C759)

/** Text centred on the vertical through the dial's centre, its middle at [y] (the canvas is turned around it first). */
private fun DrawScope.centred(layout: TextLayoutResult, color: Color, y: Float) =
    drawText(layout, color, Offset(center.x - layout.size.width / 2f, y - layout.size.height / 2f))
