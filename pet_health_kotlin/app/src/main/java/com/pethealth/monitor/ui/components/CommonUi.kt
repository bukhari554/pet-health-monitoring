package com.pethealth.monitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pethealth.monitor.ui.theme.Ink
import com.pethealth.monitor.ui.theme.Mist

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    subtitle: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Mist, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(label, fontSize = 12.sp, color = Ink.copy(alpha = 0.65f))
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 11.sp, color = Ink.copy(alpha = 0.55f))
        }
    }
}

@Composable
fun MetricRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}
