package com.zirakusa.brujula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.brujula.ui.theme.CompassColors
import com.zirakusa.brujula.R
import com.zirakusa.brujula.mcp.Kind
import com.zirakusa.brujula.mcp.McpMode
import com.zirakusa.brujula.mcp.RelayLink
import com.zirakusa.brujula.mcp.Token

/** Ajustes › Modo MCP, as in Stone, Luma, Calculadora and Notas: Activar MCP · Redireccionar a Hub · Crear token (LLM o Hub, solo lectura). */
@Composable fun McpScreen(copy: (String) -> Unit, goToHub: () -> Unit, onBack: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(CompassColors.Background).systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = onBack)
            Text(stringResource(R.string.mcp_title), Modifier.padding(start = 16.dp), fontSize = 22.sp)
        }
        Card(stringResource(R.string.mcp_enable)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (McpMode.hubRedirect) R.string.mcp_enable_by_hub else R.string.mcp_enable_hint), Modifier.weight(1f), color = CompassColors.Muted)
                Switch(McpMode.active, McpMode::enable, enabled = !McpMode.hubRedirect, colors = accent())
            }
            if (McpMode.active) Text(stringResource(when (McpMode.state) {
                RelayLink.State.ONLINE -> R.string.mcp_online; RelayLink.State.CONNECTING -> R.string.mcp_connecting
                RelayLink.State.OFFLINE -> R.string.mcp_offline
            }), color = CompassColors.Accent)
        }
        Card(stringResource(R.string.mcp_redirect)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.mcp_redirect_hint), Modifier.weight(1f), color = CompassColors.Muted)
                Switch(McpMode.hubRedirect, McpMode::redirect, colors = accent())
            }
            if (McpMode.hubRedirect) Button(goToHub, colors = ButtonDefaults.buttonColors(CompassColors.Accent, CompassColors.OnAccent)) { Text(stringResource(R.string.mcp_go_to_hub)) }
        }
        if (McpMode.hubRedirect) return@Column
        Card(stringResource(R.string.mcp_tokens)) {
            if (creating) Editor({ creating = false }) { copy(McpMode.linkFor(it.secret)); creating = false }
            else Button({ creating = true }, colors = ButtonDefaults.buttonColors(CompassColors.Accent, CompassColors.OnAccent)) { Text(stringResource(R.string.mcp_create)) }
            McpMode.listed.forEach { TokenRow(it, copy) }
        }
    }
}

@Composable private fun accent() = SwitchDefaults.colors(checkedTrackColor = CompassColors.Accent)

@Composable private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CompassColors.Card).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 18.sp)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Editor(onCancel: () -> Unit, onCreated: (Token) -> Unit) {
    var kind by remember { mutableStateOf(Kind.LLM) }
    var name by remember { mutableStateOf("") }
    var usage by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(0) }
    Text(stringResource(R.string.mcp_what), color = CompassColors.Muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(kind == Kind.LLM, R.string.mcp_kind_llm) { kind = Kind.LLM }
        Chip(kind == Kind.HUB, R.string.mcp_kind_hub) { kind = Kind.HUB }
    }
    OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.mcp_name)) })
    OutlinedTextField(usage, { usage = it.take(Token.USAGE_MAX) }, Modifier.fillMaxWidth(), minLines = 2,
        placeholder = { Text(stringResource(R.string.mcp_usage)) }, supportingText = { Text("${usage.length} / ${Token.USAGE_MAX}") })
    Text(stringResource(R.string.mcp_read_only), color = CompassColors.Muted, fontSize = 14.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 7, 30, 90, 365).forEach { d ->
            FilterChip(days == d, { days = d }, { Text(if (d == 0) stringResource(R.string.mcp_forever) else stringResource(R.string.mcp_days, d)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CompassColors.Accent, selectedLabelColor = CompassColors.OnAccent))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onCancel) { Text(stringResource(R.string.cancel), color = CompassColors.Text) }
        Button({ onCreated(McpMode.create(name, kind, usage, days)) }, colors = ButtonDefaults.buttonColors(CompassColors.Accent, CompassColors.OnAccent)) {
            Text(stringResource(R.string.mcp_create_copy))
        }
    }
}

@Composable private fun Chip(on: Boolean, label: Int, onClick: () -> Unit) = FilterChip(on, onClick, { Text(stringResource(label)) },
    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CompassColors.Accent, selectedLabelColor = CompassColors.OnAccent))

@Composable private fun TokenRow(token: Token, copy: (String) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    HorizontalDivider(color = CompassColors.Chip)
    Text(token.name, fontSize = 17.sp)
    Text(listOf(stringResource(if (token.kind == Kind.HUB) R.string.mcp_kind_hub else R.string.mcp_kind_llm),
        stringResource(R.string.mcp_read),
        stringResource(when { token.paused -> R.string.mcp_paused; token.expired -> R.string.mcp_expired; else -> R.string.mcp_active })
    ).joinToString(" · "), color = CompassColors.Muted)
    if (token.usage.isNotBlank()) Text(token.usage, color = CompassColors.Muted, fontSize = 13.sp)
    Text(McpMode.linkFor(token.secret).replace(token.secret, token.secret.take(8) + "•••"), fontFamily = FontFamily.Monospace,
        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = CompassColors.Muted)
    Row {
        TextButton({ copy(McpMode.linkFor(token.secret)) }) { Text(stringResource(R.string.mcp_copy), color = CompassColors.Accent) }
        TextButton({ McpMode.setPaused(token.id, !token.paused) }) { Text(stringResource(if (token.paused) R.string.mcp_resume else R.string.mcp_pause), color = CompassColors.Text) }
        TextButton({ confirm = true }) { Text(stringResource(R.string.mcp_revoke), color = CompassColors.Danger) }
    }
    if (confirm) AlertDialog({ confirm = false }, title = { Text(stringResource(R.string.mcp_revoke_title, token.name)) },
        text = { Text(stringResource(R.string.mcp_revoke_text)) },
        confirmButton = { TextButton({ confirm = false; McpMode.revoke(token.id) }) { Text(stringResource(R.string.mcp_revoke), color = CompassColors.Danger) } },
        dismissButton = { TextButton({ confirm = false }) { Text(stringResource(R.string.cancel)) } })
}
