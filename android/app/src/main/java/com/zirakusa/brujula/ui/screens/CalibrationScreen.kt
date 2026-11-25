package com.zirakusa.brujula.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.brujula.R
import com.zirakusa.brujula.data.Accuracy
import com.zirakusa.brujula.data.Calibration
import com.zirakusa.brujula.ui.CompassState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
import com.zirakusa.brujula.ui.theme.CompassColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * «Calibrar la Brújula»: a green figure-8 with an arrow running along it, the move that recalibrates the magnetometer.
 * It opens by itself when the accuracy is low and closes by itself when it is high again (see CompassState). Below it,
 * how much of the move is done (directions seen), the sensor's accuracy and the field's strength (a magnet or metal nearby).
 */
@Composable fun CalibrationScreen(state: CompassState, onBack: () -> Unit) {
    val phase by rememberInfiniteTransition(label = "eight").animateFloat(0.15f, 1.15f,
        infiniteRepeatable(tween(3_000, easing = LinearEasing), RepeatMode.Restart), label = "arrow")
    Column(Modifier.fillMaxSize().background(CompassColors.Background).systemBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        TitleBar(stringResource(R.string.calibrate_title), onBack)
        Spacer(Modifier.weight(1f))
        FigureEight(phase, Modifier.fillMaxWidth(0.85f))
        Text(stringResource(R.string.calibrate_text), Modifier.padding(horizontal = 16.dp), color = CompassColors.Muted,
            fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 24.sp)
        Progress(state)
        Spacer(Modifier.weight(1f))
        Button(onBack, colors = ButtonDefaults.buttonColors(if (state.calibrated) CompassColors.Guide else CompassColors.Card,
            if (state.calibrated) androidx.compose.ui.graphics.Color.Black else CompassColors.Text)) { Text(stringResource(R.string.done)) }
    }
}

/** How much of the move is done, what the sensor says, and whether a magnet or metal is disturbing it. */
@Composable private fun Progress(state: CompassState) = Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    val done = Calibration.progress(state.seen)
    LinearProgressIndicator(progress = { done }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
        color = CompassColors.Guide, trackColor = CompassColors.Chip, drawStopIndicator = {})
    Text(if (state.calibrated) stringResource(R.string.calibrate_ok) else stringResource(R.string.calibrate_progress, (done * 100).roundToInt()),
        Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = if (state.calibrated) CompassColors.Guide else CompassColors.Text, fontSize = 16.sp)
    Line(R.string.calibrate_sensor, stringResource(when (state.accuracy) {
        Accuracy.HIGH -> R.string.accuracy_high; Accuracy.MEDIUM -> R.string.accuracy_medium
        Accuracy.LOW -> R.string.accuracy_low; Accuracy.UNRELIABLE -> R.string.accuracy_unreliable }))
    val status = state.fieldStatus
    Line(R.string.calibrate_field, if (state.field <= 0f) "—" else stringResource(R.string.microtesla, state.field.roundToInt()))
    if (state.field > 0f && status != Calibration.Field.NORMAL) Text(stringResource(if (status == Calibration.Field.STRONG) R.string.field_strong else R.string.field_weak),
        color = CompassColors.Danger, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), lineHeight = 21.sp)
}

/** Bernoulli's lemniscate (∞), drawn whole, and an arrow at [phase] (0–1 of a lap) pointing the way to go. */
@Composable private fun FigureEight(phase: Float, modifier: Modifier) {
    val green = CompassColors.Guide
    Canvas(modifier.aspectRatio(2f)) {
        val a = size.width * 0.46f
        fun at(t: Double): Offset {
            val d = 1 + sin(t) * sin(t)
            return Offset(center.x + (a * cos(t) / d).toFloat(), center.y + (a * sin(t) * cos(t) / d).toFloat())
        }
        val path = Path().apply {
            moveTo(at(0.0).x, at(0.0).y)
            for (i in 1..120) at(i * 2 * PI / 120).let { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, green.copy(alpha = 0.35f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
        val t = phase * 2 * PI
        val p = at(t)
        val ahead = at(t + 0.02)
        val angle = Math.toDegrees(atan2((ahead.y - p.y).toDouble(), (ahead.x - p.x).toDouble())).toFloat()
        // The trail just behind the arrow, brighter, so the way round reads at a glance.
        val trail = Path().apply {
            val start = at(t - 1.2); moveTo(start.x, start.y)
            for (i in 1..24) at(t - 1.2 + i * 1.2 / 24).let { lineTo(it.x, it.y) }
        }
        drawPath(trail, green, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
        val s = 16.dp.toPx()
        rotate(angle, p) {
            drawPath(Path().apply { moveTo(p.x + s, p.y); lineTo(p.x - s * 0.7f, p.y - s * 0.8f); lineTo(p.x - s * 0.7f, p.y + s * 0.8f); close() }, green)
        }
    }
}
