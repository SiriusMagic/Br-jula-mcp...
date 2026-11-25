package com.zirakusa.brujula.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.brujula.R
import com.zirakusa.brujula.data.Geo
import com.zirakusa.brujula.ui.CompassState
import com.zirakusa.brujula.ui.theme.CompassColors
import kotlin.math.roundToInt

/** «Mis lugares»: each with its distance and direction from here; «Ir» points the compass there. Coordinates can be typed. */
@Composable fun PlacesCard(state: CompassState) {
    var typing by remember { mutableStateOf(false) }
    Group {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.places), Modifier.weight(1f), fontSize = 17.sp)
                TextButton({ typing = true }) { Text(stringResource(R.string.place_type), color = CompassColors.Accent) }
            }
            if (state.places.isEmpty()) Text(stringResource(R.string.places_empty), Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                color = CompassColors.Muted, fontSize = 14.sp)
            state.places.forEachIndexed { i, place ->
                if (i > 0) HorizontalDivider(color = CompassColors.Chip)
                val from = state.fix?.at
                val active = state.target == i
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(place.name, fontSize = 16.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) CompassColors.Guide else CompassColors.Text)
                        Text(from?.let { stringResource(R.string.place_from_here, Geo.formatDistance(Geo.distance(it, place.at)), Geo.bearing(it, place.at).roundToInt() % 360) }
                            ?: Geo.decimal(place.at), color = CompassColors.Muted, fontSize = 13.sp)
                    }
                    TextButton({ state.goTo(i) }) { Text(stringResource(if (active) R.string.place_stop else R.string.place_go), color = CompassColors.Guide) }
                    TextButton({ state.removePlace(i) }) { Text(stringResource(R.string.place_remove), color = CompassColors.Danger) }
                }
            }
        }
    }
    if (typing) CoordinatesDialog(state.places.size + 1, { typing = false }) { name, at -> state.addPlace(name, at); typing = false }
}

/** Names the place saved from where the phone is. */
@Composable fun NameDialog(number: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismiss, confirmButton = { TextButton({ onSave(name) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.place_save_here)) },
        text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.place_name)) },
            placeholder = { Text(stringResource(R.string.place_default, number)) }) })
}

/** «Escribir coordenadas»: decimal or degrees-minutes-seconds; «Guardar» only when they are valid. */
@Composable private fun CoordinatesDialog(number: Int, onDismiss: () -> Unit, onSave: (String, com.zirakusa.brujula.data.LatLon) -> Unit) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val at = Geo.parse(text)
    AlertDialog(onDismiss, confirmButton = { TextButton({ at?.let { onSave(name, it) } }, enabled = at != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.place_type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.place_name)) },
                    placeholder = { Text(stringResource(R.string.place_default, number)) })
                OutlinedTextField(text, { text = it.take(60) }, singleLine = true, label = { Text(stringResource(R.string.place_coordinates)) },
                    placeholder = { Text("-0.2299, -78.5249") }, isError = text.isNotBlank() && at == null,
                    supportingText = { Text(stringResource(if (text.isNotBlank() && at == null) R.string.place_coordinates_bad else R.string.place_coordinates_hint)) })
            }
        })
}
