package com.pethealth.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pethealth.monitor.data.AlertSeverity
import com.pethealth.monitor.data.HealthAlert
import com.pethealth.monitor.ui.components.PillarBadge
import com.pethealth.monitor.ui.components.SoftCard
import com.pethealth.monitor.ui.components.SoftCardShape
import com.pethealth.monitor.ui.theme.LocalPhColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsScreen(
    alerts: List<HealthAlert>,
    onAck: (String) -> Unit,
    onAckAll: () -> Unit
) {
    val c = LocalPhColors.current
    val active = alerts.filter { !it.isAcknowledged }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var filter by remember { mutableStateOf("all") }

    val filtered = when (filter) {
        "critical" -> active.filter { it.severity == AlertSeverity.CRITICAL }
        "warning" -> active.filter { it.severity == AlertSeverity.WARNING }
        else -> active
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                PillarBadge("Pillar B")
                Spacer(Modifier.height(4.dp))
                Text("Alerts", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.text)
            }
            if (active.isNotEmpty()) {
                Text(
                    "Ack all",
                    modifier = Modifier
                        .background(c.accent, RoundedCornerShape(16.dp))
                        .clickable(onClick = onAckAll)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.accentOn
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip("All (${active.size})", filter == "all") { filter = "all" }
            FilterChip("Critical", filter == "critical") { filter = "critical" }
            FilterChip("Warning", filter == "warning") { filter = "warning" }
        }

        Spacer(Modifier.height(16.dp))

        if (filtered.isEmpty()) {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .background(c.okBg, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = c.okFg)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("All clear!", fontWeight = FontWeight.Bold, color = c.text)
                    Text("No unacknowledged alerts", fontSize = 12.sp, color = c.muted)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.id }) { alert ->
                    AlertCard(alert, fmt.format(Date(alert.timestampMs))) {
                        onAck(alert.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalPhColors.current
    Text(
        label,
        modifier = Modifier
            .background(if (selected) c.text else c.surface, RoundedCornerShape(999.dp))
            .border(1.dp, if (selected) c.text else c.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        color = if (selected) c.background else c.muted
    )
}

@Composable
private fun AlertCard(alert: HealthAlert, time: String, onAck: () -> Unit) {
    val c = LocalPhColors.current
    val critical = alert.severity == AlertSeverity.CRITICAL
    val accent = if (critical) c.coral else c.amber
    val badgeBg = if (critical) c.dangerBg else c.warnBg
    val badgeFg = if (critical) c.dangerFg else c.warnFg

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, SoftCardShape)
            .border(1.dp, c.border, SoftCardShape)
            .border(
                width = 4.dp,
                color = accent,
                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
            )
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                alert.severity.name,
                modifier = Modifier
                    .background(badgeBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = badgeFg
            )
            Text(time, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = c.muted)
        }
        Spacer(Modifier.height(8.dp))
        Text(alert.reason.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(alert.description, fontSize = 12.sp, color = c.muted, lineHeight = 18.sp)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                alert.contextNote,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = c.muted,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Acknowledge",
                modifier = Modifier
                    .background(c.accent, RoundedCornerShape(16.dp))
                    .clickable(onClick = onAck)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = c.accentOn
            )
        }
    }
}
