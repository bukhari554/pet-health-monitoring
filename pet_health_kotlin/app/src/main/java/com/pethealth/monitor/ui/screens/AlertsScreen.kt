package com.pethealth.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pethealth.monitor.data.AlertSeverity
import com.pethealth.monitor.data.HealthAlert
import com.pethealth.monitor.ui.theme.Amber
import com.pethealth.monitor.ui.theme.Coral
import com.pethealth.monitor.ui.theme.Forest
import com.pethealth.monitor.ui.theme.Ink
import com.pethealth.monitor.ui.theme.Sky
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsScreen(
    alerts: List<HealthAlert>,
    onAck: (String) -> Unit,
    onAckAll: () -> Unit
) {
    val active = alerts.filter { !it.isAcknowledged }
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Alerts", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Pillar B · context-aware fusion", fontSize = 13.sp, color = Ink.copy(alpha = 0.6f))
            }
            if (active.isNotEmpty()) {
                TextButton(onClick = onAckAll) { Text("Ack all") }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (active.isEmpty()) {
            Text("No active alerts — looking good.", color = Ink.copy(alpha = 0.65f))
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(active, key = { it.id }) { alert ->
                    val accent = when (alert.severity) {
                        AlertSeverity.CRITICAL -> Coral
                        AlertSeverity.WARNING -> Amber
                        AlertSeverity.INFO -> Sky
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(alert.severity.name, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(alert.reason.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(alert.description, fontSize = 13.sp, color = Ink.copy(alpha = 0.8f))
                        Spacer(Modifier.height(4.dp))
                        Text(alert.contextNote, fontSize = 12.sp, color = Ink.copy(alpha = 0.55f))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(fmt.format(Date(alert.timestampMs)), fontSize = 11.sp, color = Ink.copy(alpha = 0.5f))
                            Button(
                                onClick = { onAck(alert.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Forest),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Acknowledge") }
                        }
                    }
                }
            }
        }
    }
}
