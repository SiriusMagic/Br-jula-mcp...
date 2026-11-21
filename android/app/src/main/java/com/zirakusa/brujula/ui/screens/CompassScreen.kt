package com.zirakusa.brujula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.brujula.R
import com.zirakusa.brujula.data.Directions
import com.zirakusa.brujula.data.Dms
import com.zirakusa.brujula.data.Fix
import com.zirakusa.brujula.ui.CompassState
import com.zirakusa.brujula.ui.theme.CompassColors
import kotlin.math.roundToInt

/** The compass: the direction's name, the turning dial with the degrees in the middle, and the position at the bottom. */
@Composable fun CompassScreen(state: CompassState, askLocation: () -> Unit, onSettings: () -> Unit) {
    val names = stringArrayResource(R.array.directions_16)
    Column(Modifier.fillMaxSize().background(CompassColors.Background).systemBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            RoundButton(Icons.Outlined.Settings, stringResource(R.string.settings), onClick = onSettings)
        }
        Text(
            when { !state.hasCompass -> stringResource(R.string.no_compass); !state.hasReading -> "—"
                else -> names[Directions.index16(state.heading, state.points)] },
            Modifier.fillMaxWidth(), fontSize = if (state.hasCompass) 40.sp else 20.sp, textAlign = TextAlign.Center,
            fontWeight = FontWeight.Light, color = if (state.hasCompass) CompassColors.Text else CompassColors.Muted,
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Dial(state.heading, listOf(stringResource(R.string.letter_n), stringResource(R.string.letter_e),
                stringResource(R.string.letter_s), stringResource(R.string.letter_w)), Modifier.fillMaxWidth())
            Text(if (state.hasReading) "${state.heading.roundToInt() % 360}°" else "—", fontSize = 60.sp, fontWeight = FontWeight.Light)
        }
        Position(state, askLocation)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable private fun Position(state: CompassState, askLocation: () -> Unit) {
    val fix = state.fix
    when {
        !state.locationAllowed -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.location_why), color = CompassColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
            Button(askLocation, colors = ButtonDefaults.buttonColors(CompassColors.Card, CompassColors.Text)) {
                Text(stringResource(R.string.location_allow))
            }
        }
        fix == null -> Text(stringResource(R.string.location_searching), color = CompassColors.Muted, fontSize = 15.sp)
        else -> Coordinates(fix)
    }
}

@Composable private fun Coordinates(fix: Fix) = Row(Modifier.fillMaxWidth()) {
    Coordinate(stringResource(if (Dms.isNorth(fix.latitude)) R.string.latitude_north else R.string.latitude_south),
        Dms.format(fix.latitude), Modifier.weight(1f))
    Coordinate(stringResource(if (Dms.isEast(fix.longitude)) R.string.longitude_east else R.string.longitude_west),
        Dms.format(fix.longitude), Modifier.weight(1f))
}

@Composable private fun Coordinate(label: String, value: String, modifier: Modifier) =
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = CompassColors.Muted, fontSize = 15.sp)
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Light)
    }
