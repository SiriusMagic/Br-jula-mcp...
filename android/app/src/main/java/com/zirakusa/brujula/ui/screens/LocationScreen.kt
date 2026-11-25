package com.zirakusa.brujula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.brujula.R
import com.zirakusa.brujula.data.Directions
import com.zirakusa.brujula.data.Dms
import com.zirakusa.brujula.data.Geo
import com.zirakusa.brujula.data.Sun
import com.zirakusa.brujula.ui.CompassState
import com.zirakusa.brujula.ui.theme.CompassColors
import kotlin.math.roundToInt

/**
 * «Ubicación sin internet»: the GPS position (it only needs to see the sky), how good it is, true north and the sun
 * worked out on the phone, and the saved places («Ir» makes the compass point there). [now] is the clock (tests fix it).
 */
@Composable fun LocationScreen(state: CompassState, askLocation: () -> Unit, copy: (String) -> Unit, now: () -> Long, onBack: () -> Unit) {
    var saving by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(CompassColors.Background).systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TitleBar(stringResource(R.string.location_title), onBack)
        Text(stringResource(R.string.location_offline_hint), color = CompassColors.Muted, fontSize = 15.sp, lineHeight = 21.sp)
        val fix = state.fix
        Group {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    !state.locationAllowed -> Button(askLocation, colors = ButtonDefaults.buttonColors(CompassColors.Accent, CompassColors.OnAccent)) {
                        Text(stringResource(R.string.location_allow))
                    }
                    fix == null -> Text(stringResource(R.string.location_searching_gps), color = CompassColors.Muted, fontSize = 16.sp)
                    else -> {
                        Text(Geo.decimal(fix.at), fontSize = 26.sp, fontWeight = FontWeight.Light)
                        Text("${Dms.format(fix.latitude)} ${stringResource(if (Dms.isNorth(fix.latitude)) R.string.north_short else R.string.south_short)}  " +
                            "${Dms.format(fix.longitude)} ${stringResource(if (Dms.isEast(fix.longitude)) R.string.east_short else R.string.west_short)}",
                            color = CompassColors.Muted, fontSize = 15.sp)
                        Line(R.string.fix_source, fixLine(state))
                        fix.altitude?.let { Line(R.string.fix_altitude, "${it.roundToInt()} m") }
                        state.satellites?.let { (used, seen) -> Line(R.string.fix_satellites, stringResource(R.string.satellites_of, used, seen)) }
                        Line(R.string.fix_age, ago(now() - fix.time))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                            Button({ copy(Geo.decimal(fix.at)) }, colors = ButtonDefaults.buttonColors(CompassColors.Chip, CompassColors.Text)) { Text(stringResource(R.string.copy)) }
                            Button({ saving = true }, colors = ButtonDefaults.buttonColors(CompassColors.Accent, CompassColors.OnAccent)) { Text(stringResource(R.string.place_save_here)) }
                        }
                    }
                }
            }
        }
        NorthCard(state, now)
        PlacesCard(state)
    }
    if (saving) NameDialog(state.places.size + 1, onDismiss = { saving = false }) { name -> state.saveHere(name); saving = false }
}

/** True north (the phone's magnetic model, no internet) and where the sun is, a second way to find north. */
@Composable private fun NorthCard(state: CompassState, now: () -> Long) = Group {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.north_true), fontSize = 17.sp)
                Text(stringResource(R.string.north_true_hint), color = CompassColors.Muted, fontSize = 14.sp)
            }
            Switch(state.trueNorth, state::changeTrueNorth, colors = SwitchDefaults.colors(checkedTrackColor = CompassColors.Accent, checkedThumbColor = CompassColors.OnAccent))
        }
        state.magnetic?.let { m ->
            Line(R.string.declination, Geo.formatDeclination(m.declination, stringResource(R.string.east_short), stringResource(R.string.west_short)))
        }
        val names = stringArrayResource(R.array.directions_16)
        state.fix?.let { f ->
            val sun = Sun.at(f.at, now())
            Text(if (sun.up) stringResource(R.string.sun_at, sun.azimuth.roundToInt() % 360, names[Directions.index16(sun.azimuth, 16)], sun.elevation.roundToInt())
                else stringResource(R.string.sun_down), color = CompassColors.Muted, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable fun Line(label: Int, value: String) = Row(Modifier.fillMaxWidth()) {
    Text(stringResource(label), Modifier.weight(1f), color = CompassColors.Muted, fontSize = 15.sp)
    Text(value, fontSize = 15.sp)
}

@Composable private fun ago(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> stringResource(R.string.ago_seconds, s.toInt())
        s < 3600 -> stringResource(R.string.ago_minutes, (s / 60).toInt())
        else -> stringResource(R.string.ago_hours, (s / 3600).toInt())
    }
}
