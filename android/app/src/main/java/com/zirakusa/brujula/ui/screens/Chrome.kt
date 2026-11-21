package com.zirakusa.brujula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.brujula.R
import com.zirakusa.brujula.ui.theme.CompassColors

/** Round button with one icon (settings, back), as in Notas. */
@Composable fun RoundButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Box(modifier.size(52.dp).clip(CircleShape).background(CompassColors.Card).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = CompassColors.Text)
    }

/** ← and a big title, at the top of Ajustes, Modo MCP and Calibrar. */
@Composable fun TitleBar(title: String, onBack: () -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = onBack)
    Text(title, Modifier.padding(start = 16.dp), fontSize = 24.sp)
}

/** «Sistema | Claro | Oscuro» and «8 | 16». */
@Composable fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) =
    Row(Modifier.clip(CircleShape).background(CompassColors.Chip).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            Text(
                label,
                Modifier.clip(CircleShape).background(if (i == selected) CompassColors.Card else CompassColors.Chip)
                    .clickable { onSelect(i) }.padding(horizontal = 18.dp, vertical = 10.dp),
                color = if (i == selected) CompassColors.Accent else CompassColors.Text,
                fontSize = 16.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }

/** A rounded card of rows in Ajustes. */
@Composable fun Group(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CompassColors.Card), content = content)

/** One row: title, optional subtitle, and › when it opens something. */
@Composable fun Item(title: String, subtitle: String?, onClick: (() -> Unit)?) =
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp)
            if (subtitle != null) Text(subtitle, color = CompassColors.Muted, fontSize = 14.sp)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = CompassColors.Muted)
    }
