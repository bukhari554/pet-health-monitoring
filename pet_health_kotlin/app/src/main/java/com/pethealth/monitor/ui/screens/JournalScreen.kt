package com.pethealth.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pethealth.monitor.data.DailySummary
import com.pethealth.monitor.data.SensorReading
import com.pethealth.monitor.ui.components.MetricRow
import com.pethealth.monitor.ui.components.MetricTile
import com.pethealth.monitor.ui.components.SectionHeader
import com.pethealth.monitor.ui.theme.Amber
import com.pethealth.monitor.ui.theme.Forest
import com.pethealth.monitor.ui.theme.Ink
import com.pethealth.monitor.ui.theme.Sky
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JournalScreen(latest: SensorReading?, week: List<DailySummary>, today: DailySummary?) {
    val dayFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Journal", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Pillar C · behavior + sleep report", fontSize = 13.sp, color = Ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(16.dp))

        if (today != null) {
            SectionHeader("Today", "Vet-ready daily snapshot")
            MetricRow {
                MetricTile("Sleep", today.sleepLabel, "score ${today.sleepScore}", accent = Sky, modifier = Modifier.weight(1f))
                MetricTile("Active", "${today.activeMinutes}m", "move ${today.movementCount}", accent = Forest, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            MetricRow {
                MetricTile("Resting BPM", "%.0f".format(today.avgRestingBpm), "avg", accent = Amber, modifier = Modifier.weight(1f))
                MetricTile("HRV", "%.0f ms".format(today.avgHrv), "avg", accent = Sky, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            MetricRow {
                MetricTile("Laying", "${today.layingMinutes}m", modifier = Modifier.weight(1f))
                MetricTile("Standing", "${today.standingMinutes}m", modifier = Modifier.weight(1f))
                MetricTile("Awakenings", "${today.awakenings}", modifier = Modifier.weight(1f))
            }
        }

        if (latest != null) {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Live activity counters", latest.activity.label)
            MetricRow {
                MetricTile("Sleeping", "${latest.sleepingMinutes}m", modifier = Modifier.weight(1f))
                MetricTile("Active", "${latest.activeMinutes}m", accent = Forest, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("7-day history", "Routine + sleep continuity")
        week.asReversed().forEach { day ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(dayFmt.format(Date(day.dayStartMs)), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sleep ${day.sleepLabel} · Active ${day.activeMinutes}m · Score ${day.healthScore} · Alerts ${day.alertCount}",
                    fontSize = 12.sp,
                    color = Ink.copy(alpha = 0.7f)
                )
            }
        }
    }
}
