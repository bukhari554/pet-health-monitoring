package com.pethealth.monitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pethealth.monitor.ui.theme.LocalPhColors

val SoftCardShape = RoundedCornerShape(20.dp)

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val c = LocalPhColors.current
    Column(
        modifier
            .background(c.surface, SoftCardShape)
            .border(1.dp, c.border, SoftCardShape)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    val c = LocalPhColors.current
    Text(
        title,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = c.text,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun PillarBadge(label: String, bg: Color = Color.Unspecified, fg: Color = Color.Unspecified) {
    val c = LocalPhColors.current
    Text(
        text = label.uppercase(),
        modifier = Modifier
            .background(if (bg == Color.Unspecified) c.surfaceSoft else bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = if (fg == Color.Unspecified) c.muted else fg,
        letterSpacing = 0.6.sp
    )
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    subtitle: String? = null,
    accent: Color = Color.Unspecified,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    iconBg: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val c = LocalPhColors.current
    val valueColor = if (accent == Color.Unspecified) c.text else accent
    Column(
        modifier
            .background(c.surface, SoftCardShape)
            .border(1.dp, c.border, SoftCardShape)
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.muted)
            if (icon != null) {
                val tint = if (iconTint == Color.Unspecified) c.muted else iconTint
                Box(
                    Modifier
                        .size(28.dp)
                        .background(
                            if (iconBg == Color.Unspecified) c.surfaceSoft else iconBg,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = valueColor, lineHeight = 32.sp)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 12.sp, color = c.muted)
        }
    }
}

@Composable
fun MetricRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
