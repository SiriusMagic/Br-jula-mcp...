package com.zirakusa.brujula.ui.screens

import com.zirakusa.brujula.IconCard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.brujula.BuildConfig
import com.zirakusa.brujula.R
import com.zirakusa.brujula.mcp.McpMode
import com.zirakusa.brujula.mcp.RelayLink
import com.zirakusa.brujula.ui.CompassState
import com.zirakusa.brujula.ui.theme.Accent
import com.zirakusa.brujula.ui.theme.CompassColors
import com.zirakusa.brujula.ui.theme.ThemeMode

/** «Ajustes»: Temas (Sistema/Claro/Oscuro, negro puro, acento), Rumbos (8 o 16), Modo MCP, Calibrar, privacy and version. */
@Composable fun SettingsScreen(state: CompassState, onMcp: () -> Unit, onCalibrate: () -> Unit, onBack: () -> Unit) {
    var privacy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(CompassColors.Background).systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TitleBar(stringResource(R.string.settings), onBack)
        Group {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.theme), fontSize = 18.sp)
                Segmented(listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                    state.themeMode.ordinal) { state.changeTheme(ThemeMode.entries[it]) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.amoled), fontSize = 16.sp)
                        Text(stringResource(R.string.amoled_hint), color = CompassColors.Muted, fontSize = 14.sp)
                    }
                    Switch(state.amoled, state::changeAmoled, colors = SwitchDefaults.colors(checkedTrackColor = CompassColors.Accent,
                        checkedThumbColor = CompassColors.OnAccent))
                }
                Text(stringResource(R.string.accent), fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Accent.entries.forEach { AccentDot(it, it == state.accent, state::changeAccent) } }
            }
        }
        Group {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.points), fontSize = 18.sp)
                Text(stringResource(R.string.points_hint), color = CompassColors.Muted, fontSize = 14.sp)
                Segmented(listOf("8", "16"), if (state.points == 16) 1 else 0) { state.changePoints(if (it == 1) 16 else 8) }
            }
        }
        Group {
            val status = when {
                !McpMode.active -> stringResource(R.string.mcp_status_off)
                McpMode.hubRedirect -> stringResource(R.string.mcp_redirect)
                McpMode.state == RelayLink.State.ONLINE -> stringResource(R.string.mcp_online)
                McpMode.state == RelayLink.State.CONNECTING -> stringResource(R.string.mcp_connecting)
                else -> stringResource(R.string.mcp_offline)
            }
            Item(stringResource(R.string.mcp_title), "$status · ${stringResource(R.string.mcp_settings_hint)}", onMcp)
            HorizontalDivider(color = CompassColors.Chip)
            Item(stringResource(R.string.calibrate), stringResource(R.string.calibrate_hint), onCalibrate)
        }
        IconCard(CompassColors.Card, CompassColors.Text, CompassColors.Muted, CompassColors.Accent, androidx.compose.ui.graphics.Color(0xFF000000))
        Group {
            Item(stringResource(R.string.privacy), null) { privacy = true }
            HorizontalDivider(color = CompassColors.Chip)
            Item(stringResource(R.string.about), stringResource(R.string.version, BuildConfig.VERSION_NAME), null)
        }
    }
    if (privacy) AlertDialog({ privacy = false }, confirmButton = { TextButton({ privacy = false }) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.privacy)) }, text = { Text(stringResource(R.string.privacy_text)) })
}

@Composable private fun AccentDot(accent: Accent, selected: Boolean, onPick: (Accent) -> Unit) {
    val name = stringResource(accentNames.getValue(accent))
    Box(Modifier.size(40.dp).clip(CircleShape).background(accent.color)
        .border(3.dp, if (selected) CompassColors.Text else Color.Transparent, CircleShape)
        .clickable(onClickLabel = name) { onPick(accent) }.semantics { contentDescription = name },
        contentAlignment = Alignment.Center) { if (selected) Icon(Icons.Filled.Check, null, Modifier.size(20.dp), tint = accent.ink) }
}

private val accentNames = mapOf(
    Accent.ROJO to R.string.accent_red, Accent.NARANJA to R.string.accent_orange, Accent.AMARILLO to R.string.accent_yellow,
    Accent.VERDE to R.string.accent_green, Accent.AZUL to R.string.accent_blue, Accent.MORADO to R.string.accent_purple,
)
