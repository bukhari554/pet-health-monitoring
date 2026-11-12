package com.pethealth.monitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pethealth.monitor.data.SensorReading
import com.pethealth.monitor.ui.components.MetricRow
import com.pethealth.monitor.ui.components.MetricTile
import com.pethealth.monitor.ui.components.PillarBadge
import com.pethealth.monitor.ui.components.SectionHeader
import com.pethealth.monitor.ui.components.SoftCard
import com.pethealth.monitor.ui.components.SoftCardShape
import com.pethealth.monitor.ui.theme.LocalPhColors

@Composable
fun VitalsScreen(latest: SensorReading?, history: List<SensorReading>) {
    val c = LocalPhColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column {
            PillarBadge("Pillar A")
            Spacer(Modifier.height(4.dp))
            Text("Vitals & IMU", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.text)
        }

        if (latest == null) {
            Text("No live vitals yet", color = c.muted, fontSize = 13.sp)
            return
        }

        val hasBpm = latest.heartRateBpm > 0
        val qualityPct = if (hasBpm) (latest.signalQuality * 100).toInt() else 0

        Column {
            SectionHeader("Heart Metrics")
            MetricRow {
                MetricTile(
                    "BPM",
                    if (hasBpm) "${latest.heartRateBpm}" else "--",
                    if (hasBpm) "reliable pulse" else "place sensor on skin",
                    accent = if (hasBpm) c.text else c.coral,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    "Resting HR",
                    if (hasBpm) "${latest.restingHeartRate ?: "--"}" else "--",
                    "baseline",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            MetricRow {
                MetricTile(
                    "HRV RMSSD",
                    if (hasBpm && latest.hrvRmssdMs > 0) "${"%.0f".format(latest.hrvRmssdMs)}" else "--",
                    "ms · stress / recovery",
                    accent = c.sky,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    "Quality",
                    if (hasBpm) "$qualityPct%" else "0%",
                    if (hasBpm && !latest.artifactRejected) "usable" else "no contact / noise",
                    accent = when {
                        qualityPct >= 70 -> c.accent
                        qualityPct >= 40 -> c.amber
                        else -> c.coral
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val bpmSeries = history.takeLast(30).map { it.heartRateBpm }
        SoftCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BPM Trend", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                val live = bpmSeries.any { it > 0 }
                Text(
                    if (live) "Receiving samples" else "Waiting signal",
                    modifier = Modifier
                        .background(if (live) c.okBg else c.surfaceSoft, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (live) c.okFg else c.muted
                )
            }
            Spacer(Modifier.height(12.dp))
            BpmChart(bpmSeries)
        }

        Column {
            SectionHeader("IMU · MPU6050")
            MetricRow {
                MetricTile(
                    "Motion",
                    "%.2f".format(latest.accelEnergy),
                    "m/s² dynamic",
                    icon = Icons.Outlined.OpenWith,
                    iconTint = c.accent,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    "Gyro energy",
                    "%.2f".format(latest.gyroEnergy),
                    "rad/s rotation",
                    accent = c.sky,
                    icon = Icons.Outlined.Explore,
                    iconTint = c.sky,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            MetricRow {
                MetricTile(
                    "Jerk",
                    "%.1f".format(latest.jerk),
                    "impact proxy",
                    accent = c.coral,
                    icon = Icons.Outlined.Bolt,
                    iconTint = c.coral,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    "Temp",
                    "%.1f".format(latest.enclosureTempC),
                    "°C enclosure",
                    icon = Icons.Outlined.Thermostat,
                    iconTint = c.amber,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                AxisTile("X-AXIS", "%.2f".format(latest.accelX), Modifier.weight(1f))
                AxisTile("Y-AXIS", "%.2f".format(latest.accelY), Modifier.weight(1f))
                AxisTile("Z-AXIS", "%.2f".format(latest.accelZ), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AxisTile(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalPhColors.current
    Column(
        modifier
            .background(c.surface, RoundedCornerShape(16.dp))
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.muted)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text)
        Text("m/s²", fontSize = 10.sp, color = c.muted)
    }
}

@Composable
private fun BpmChart(values: List<Int>) {
    val c = LocalPhColors.current
    var lastGood = 0
    val series = values.map { v ->
        if (v > 0) {
            lastGood = v
            v.toFloat()
        } else if (lastGood > 0) {
            lastGood.toFloat()
        } else {
            null
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(176.dp)
            .background(c.surfaceSoft, SoftCardShape)
    ) {
        if (series.none { it != null }) return@Canvas
        val nums = series.filterNotNull()
        val max = (nums.maxOrNull() ?: 160f).coerceAtLeast(120f)
        val min = (nums.minOrNull() ?: 60f).coerceAtMost(80f)
        val range = (max - min).coerceAtLeast(1f)
        val path = Path()
        var started = false
        series.forEachIndexed { i, v ->
            if (v == null) {
                started = false
                return@forEachIndexed
            }
            val x = size.width * i / (series.size - 1).coerceAtLeast(1)
            val y = size.height - ((v - min) / range) * size.height * 0.92f - size.height * 0.04f
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(path, color = c.accent, style = Stroke(width = 5f, cap = StrokeCap.Round))
        values.forEachIndexed { i, raw ->
            if (raw <= 0) return@forEachIndexed
            val v = raw.toFloat()
            val x = size.width * i / (series.size - 1).coerceAtLeast(1)
            val y = size.height - ((v - min) / range) * size.height * 0.92f - size.height * 0.04f
            drawCircle(c.accent, radius = 4f, center = Offset(x, y))
        }
    }
}
