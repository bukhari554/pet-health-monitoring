package com.pethealth.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pethealth.monitor.data.HealthScore
import com.pethealth.monitor.data.Pet
import com.pethealth.monitor.data.PetBaseline
import com.pethealth.monitor.data.SensorReading
import com.pethealth.monitor.ui.components.MetricRow
import com.pethealth.monitor.ui.components.MetricTile
import com.pethealth.monitor.ui.components.SectionHeader
import com.pethealth.monitor.ui.theme.Amber
import com.pethealth.monitor.ui.theme.Coral
import com.pethealth.monitor.ui.theme.Forest
import com.pethealth.monitor.ui.theme.Ink
import com.pethealth.monitor.ui.theme.Leaf
import com.pethealth.monitor.ui.theme.Mist
import com.pethealth.monitor.ui.theme.Sky

@Composable
fun DashboardScreen(
    pets: List<Pet>,
    selectedPetId: String,
    latest: SensorReading?,
    score: HealthScore?,
    baseline: PetBaseline?,
    unreadAlerts: Int,
    onSelectPet: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Pet Health", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(
            "MPU6050 + Pulse · live context AI",
            fontSize = 13.sp,
            color = Ink.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pets.forEach { pet ->
                val selected = pet.id == selectedPetId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Mist else Color.White)
                        .clickable { onSelectPet(pet.id) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (selected) Forest else Leaf),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(pet.name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(pet.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("${pet.breed} · ${pet.age}y", fontSize = 11.sp, color = Ink.copy(alpha = 0.55f))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (score != null) {
            SectionHeader("Health score", "Explainable risk from vitals + activity")
            val accent = when {
                score.score >= 75 -> Forest
                score.score >= 50 -> Amber
                else -> Coral
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${score.score}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = accent)
                    Spacer(Modifier.size(8.dp))
                    Text(score.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = accent)
                    Spacer(Modifier.weight(1f))
                    if (unreadAlerts > 0) {
                        Text("$unreadAlerts alerts", color = Coral, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                score.reasons.take(4).forEach {
                    Text("• $it", fontSize = 13.sp, color = Ink.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (baseline != null) {
            SectionHeader("Personal baseline", "Alert thresholds adapt to this pet")
            MetricRow {
                MetricTile(
                    "Resting avg",
                    "%.0f".format(baseline.restingHrAvg),
                    "thr ${baseline.highRestingHrThreshold}",
                    accent = Forest,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    "HRV thr",
                    "${baseline.lowHrvThreshold}",
                    "samples ${baseline.samples}",
                    accent = Sky,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        if (latest != null) {
            SectionHeader("Live snapshot", latest.activity.label)
            MetricRow {
                MetricTile(
                    "Heart rate",
                    if (latest.heartRateBpm > 0) "${latest.heartRateBpm}" else "--",
                    if (latest.artifactRejected) "Artifact rejected" else "bpm",
                    accent = Coral,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    "HRV",
                    if (latest.hrvRmssdMs > 0) "${latest.hrvRmssdMs.toInt()}" else "--",
                    "ms RMSSD",
                    accent = Sky,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            val qualityAccent = when {
                latest.signalQuality >= 0.7f -> Leaf
                latest.signalQuality >= 0.4f -> Amber
                else -> Coral
            }
            MetricRow {
                MetricTile(
                    "Signal",
                    "${(latest.signalQuality * 100).toInt()}%",
                    if (latest.artifactRejected) "weak / artifact" else "pulse quality",
                    accent = qualityAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    "Activity",
                    latest.activity.label,
                    "move ${latest.movementCount}",
                    accent = Forest,
                    modifier = Modifier.weight(1f)
                )
            }
            val highRestingThr = baseline?.highRestingHrThreshold ?: 130
            if (latest.isResting && latest.heartRateBpm >= highRestingThr) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Context: high HR while resting (thr $highRestingThr) — fusion alert expected",
                    fontSize = 12.sp,
                    color = Coral
                )
            }
        } else {
            Text("Waiting for sensor stream…", color = Ink.copy(alpha = 0.6f))
        }
    }
}
