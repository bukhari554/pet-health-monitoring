package com.pethealth.monitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.pethealth.monitor.ui.components.SoftCard
import com.pethealth.monitor.ui.theme.LocalPhColors

@Composable
fun DashboardScreen(
    pets: List<Pet>,
    selectedPetId: String,
    latest: SensorReading?,
    score: HealthScore?,
    baseline: PetBaseline?,
    unreadAlerts: Int,
    onSelectPet: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenAlerts: () -> Unit = {}
) {
    val c = LocalPhColors.current
    val selected = pets.firstOrNull { it.id == selectedPetId }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.WaterDrop, null, tint = c.accentOn, modifier = Modifier.size(20.dp))
                }
                Text("PetHealth", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text, letterSpacing = (-0.2).sp)
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface)
                    .border(1.dp, c.border, RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Bluetooth, contentDescription = "Device link", tint = c.text, modifier = Modifier.size(20.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pets.forEach { pet ->
                PetProfileCard(
                    pet = pet,
                    selected = pet.id == selectedPetId,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectPet(pet.id) }
                )
            }
        }

        Column {
            SectionHeader("Health Score")
            SoftCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    ScoreRing(score = score?.score ?: 0)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val s = score?.score ?: 0
                        val healthy = s >= 85
                        val atRisk = s in 50..84
                        StatusPill(
                            label = score?.label ?: "—",
                            bg = when {
                                healthy -> c.okBg
                                atRisk -> c.warnBg
                                else -> c.dangerBg
                            },
                            fg = when {
                                healthy -> c.okFg
                                atRisk -> c.warnFg
                                else -> c.dangerFg
                            },
                            dot = when {
                                healthy -> c.accent
                                atRisk -> c.amber
                                else -> c.coral
                            }
                        )
                        Text(
                            selected?.let { "${it.name} · ${it.breed}" } ?: "—",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.text
                        )
                        if (latest != null) {
                            Text("Posture: ${latest.activity.label}", fontSize = 12.sp, color = c.muted)
                        }
                    }
                }

                if (!score?.reasons.isNullOrEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    score!!.reasons.take(2).forEach { reason ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .background(c.surfaceSoft, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(reason, fontSize = 13.sp, color = c.text)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(c.surfaceSoft, RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenAlerts)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Notifications, null, tint = c.coral, modifier = Modifier.size(18.dp))
                    Text("$unreadAlerts open alerts", fontSize = 13.sp, color = c.text, modifier = Modifier.weight(1f))
                    Text("View all", fontSize = 12.sp, color = c.muted)
                }
            }
        }

        Column {
            SectionHeader("Live Snapshot")
            if (latest == null) {
                Text("Waiting for sensor stream…", color = c.muted, fontSize = 13.sp)
            } else {
                val hasBpm = latest.heartRateBpm > 0
                val qPct = (latest.signalQuality * 100).toInt()
                val qAccent = when {
                    qPct >= 70 -> c.accent
                    qPct >= 40 -> c.amber
                    else -> c.coral
                }
                MetricRow {
                    MetricTile(
                        label = "Heart rate",
                        value = if (hasBpm) "${latest.heartRateBpm}" else "--",
                        subtitle = if (hasBpm) "bpm · reliable pulse" else "no reliable pulse",
                        accent = if (hasBpm) c.text else c.coral,
                        icon = Icons.Filled.Favorite,
                        iconTint = c.coral,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "HRV",
                        value = if (hasBpm && latest.hrvRmssdMs > 0) "${latest.hrvRmssdMs.toInt()}" else "--",
                        subtitle = "ms · RMSSD",
                        accent = c.sky,
                        icon = Icons.Outlined.WaterDrop,
                        iconTint = c.sky,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                MetricRow {
                    MetricTile(
                        label = "Signal",
                        value = "$qPct%",
                        subtitle = if (latest.artifactRejected || qPct < 40) "weak / artifact" else "pulse quality",
                        accent = qAccent,
                        icon = Icons.Filled.Wifi,
                        iconTint = c.amber,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "Activity",
                        value = latest.activity.label,
                        subtitle = "move ${latest.movementCount}",
                        accent = c.text,
                        icon = Icons.AutoMirrored.Outlined.DirectionsWalk,
                        iconTint = c.accent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (baseline != null) {
            Column {
                SectionHeader("Personal Baseline")
                MetricRow {
                    MetricTile(
                        label = "Resting avg",
                        value = "%.0f".format(baseline.restingHrAvg),
                        subtitle = "threshold ${baseline.highRestingHrThreshold} bpm",
                        icon = Icons.Filled.Shield,
                        iconTint = c.muted,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "HRV threshold",
                        value = "${baseline.lowHrvThreshold}",
                        subtitle = "samples ${baseline.samples}",
                        accent = c.sky,
                        icon = Icons.Filled.Storage,
                        iconTint = c.muted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PetProfileCard(
    pet: Pet,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = LocalPhColors.current
    val initial = pet.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(
                1.5.dp,
                if (selected) c.accent else c.border,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) c.accent else c.surfaceSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initial,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) c.accentOn else c.text
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(pet.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.text)
            Text("${pet.breed} · ${pet.age}y", fontSize = 12.sp, color = c.muted)
            Text(
                if (selected) "Active" else "Stand-by",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) c.accent else c.muted
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, bg: Color, fg: Color, dot: Color) {
    Row(
        Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun ScoreRing(score: Int) {
    val c = LocalPhColors.current
    val progress = (score.coerceIn(0, 100) / 100f)
    val ringColor = when {
        score >= 85 -> c.accent
        score >= 50 -> c.amber
        else -> c.coral
    }
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(112.dp)) {
            val stroke = 10.dp.toPx()
            drawArc(
                color = c.border,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.text, lineHeight = 34.sp)
            Text("of 100", fontSize = 11.sp, color = c.muted)
        }
    }
}
