package com.pethealth.monitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.pethealth.monitor.ui.components.SectionHeader
import com.pethealth.monitor.ui.theme.Coral
import com.pethealth.monitor.ui.theme.Forest
import com.pethealth.monitor.ui.theme.Ink
import com.pethealth.monitor.ui.theme.Leaf
import com.pethealth.monitor.ui.theme.Sky

@Composable
fun VitalsScreen(latest: SensorReading?, history: List<SensorReading>) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Vitals", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Pillar A · Pulse + MPU signal quality", fontSize = 13.sp, color = Ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(16.dp))

        if (latest == null) {
            Text("No live vitals yet")
            return
        }

        SectionHeader("Heart metrics", "Real BPM path ready for ESP32 stream")
        MetricRow {
            MetricTile(
                "BPM",
                if (latest.heartRateBpm > 0) "${latest.heartRateBpm}" else "--",
                latest.activity.label,
                accent = Coral,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                "Resting HR",
                "${latest.restingHeartRate ?: "--"}",
                "baseline",
                accent = Forest,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        MetricRow {
            MetricTile(
                "HRV RMSSD",
                if (latest.hrvRmssdMs > 0) "${"%.0f".format(latest.hrvRmssdMs)} ms" else "--",
                "stress / recovery hint",
                accent = Sky,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                "Quality",
                "${(latest.signalQuality * 100).toInt()}%",
                if (latest.artifactRejected) "motion artifact" else "usable",
                accent = Leaf,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("BPM trend", "Last ${history.size} samples")
        BpmChart(history.map { it.heartRateBpm })

        Spacer(Modifier.height(20.dp))
        SectionHeader("IMU (MPU6050)", "Accel / gyro / enclosure temp")
        MetricRow {
            MetricTile("Accel energy", "%.1f".format(latest.accelEnergy), "g-energy", modifier = Modifier.weight(1f))
            MetricTile("Gyro energy", "%.1f".format(latest.gyroEnergy), "rad-energy", accent = Sky, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        MetricRow {
            MetricTile("Jerk", "%.1f".format(latest.jerk), "impact proxy", accent = Coral, modifier = Modifier.weight(1f))
            MetricTile("Temp", "%.1f°C".format(latest.enclosureTempC), "enclosure proxy", accent = Forest, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        MetricRow {
            MetricTile("X", "%.2f".format(latest.accelX), "g", modifier = Modifier.weight(1f))
            MetricTile("Y", "%.2f".format(latest.accelY), "g", modifier = Modifier.weight(1f))
            MetricTile("Z", "%.2f".format(latest.accelZ), "g", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BpmChart(values: List<Int>) {
    val usable = values.map { if (it <= 0) null else it.toFloat() }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        if (usable.isEmpty()) return@Canvas
        val max = (usable.filterNotNull().maxOrNull() ?: 160f).coerceAtLeast(120f)
        val min = (usable.filterNotNull().minOrNull() ?: 60f).coerceAtMost(80f)
        val range = (max - min).coerceAtLeast(1f)
        val path = Path()
        var started = false
        usable.forEachIndexed { i, v ->
            if (v == null) {
                started = false
                return@forEachIndexed
            }
            val x = size.width * i / (usable.size - 1).coerceAtLeast(1)
            val y = size.height - ((v - min) / range) * size.height
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(path, color = Coral, style = Stroke(width = 5f, cap = StrokeCap.Round))
        usable.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val x = size.width * i / (usable.size - 1).coerceAtLeast(1)
            val y = size.height - ((v - min) / range) * size.height
            drawCircle(Coral, radius = 3.5f, center = Offset(x, y))
        }
    }
}
