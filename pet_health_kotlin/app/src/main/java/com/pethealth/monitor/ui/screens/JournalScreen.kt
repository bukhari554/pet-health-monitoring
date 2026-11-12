package com.pethealth.monitor.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pethealth.monitor.data.DailySummary
import com.pethealth.monitor.data.SensorReading
import com.pethealth.monitor.ui.components.MetricRow
import com.pethealth.monitor.ui.components.MetricTile
import com.pethealth.monitor.ui.components.PillarBadge
import com.pethealth.monitor.ui.components.SectionHeader
import com.pethealth.monitor.ui.components.SoftCardShape
import com.pethealth.monitor.ui.theme.LocalPhColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JournalScreen(
    latest: SensorReading?,
    week: List<DailySummary>,
    today: DailySummary?,
    onVetExport: () -> Unit = {}
) {
    val c = LocalPhColors.current
    val dayFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())

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
            verticalAlignment = Alignment.Top
        ) {
            Column {
                PillarBadge("Pillar C")
                Spacer(Modifier.height(4.dp))
                Text("Journal", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.text)
            }
            Row(
                Modifier
                    .background(c.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, c.border, RoundedCornerShape(16.dp))
                    .clickable(onClick = onVetExport)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.Share, null, tint = c.accent, modifier = Modifier.size(14.dp))
                Text("Vet Export", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.text)
            }
        }

        if (today != null) {
            Column {
                SectionHeader("Today")
                MetricRow {
                    MetricTile("Sleep", today.sleepLabel, "score ${today.sleepScore}", accent = c.sky, modifier = Modifier.weight(1f))
                    MetricTile("Active", "${today.activeMinutes}m", "move ${today.movementCount}", modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                MetricRow {
                    MetricTile("Resting BPM", "%.0f".format(today.avgRestingBpm), "avg", accent = c.amber, modifier = Modifier.weight(1f))
                    MetricTile("HRV", "%.0f".format(today.avgHrv), "ms avg", accent = c.sky, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MiniStat("Laying", "${today.layingMinutes}m", Modifier.weight(1f))
                    MiniStat("Standing", "${today.standingMinutes}m", Modifier.weight(1f))
                    MiniStat("Awakenings", "${today.awakenings}", Modifier.weight(1f))
                }
            }
        }

        if (latest != null) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Live Activity Counters")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
                        Text("Counting", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                    }
                }
                MetricRow {
                    MetricTile("Sleeping", "${latest.sleepingMinutes}m", modifier = Modifier.weight(1f))
                    MetricTile("Active", "${latest.activeMinutes}m", modifier = Modifier.weight(1f))
                }
            }
        }

        Column {
            SectionHeader("7-Day History")
            week.asReversed().forEach { day ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(c.surface, SoftCardShape)
                        .border(1.dp, c.border, SoftCardShape)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                dayFmt.format(Date(day.dayStartMs)),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = c.text
                            )
                            val good = day.healthScore >= 85
                            Text(
                                "Score ${day.healthScore}",
                                modifier = Modifier
                                    .background(if (good) c.okBg else c.warnBg, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (good) c.okFg else c.warnFg
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sleep ${day.sleepLabel} · Active ${day.activeMinutes}m · Alerts ${day.alertCount}",
                            fontSize = 12.sp,
                            color = c.muted
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = c.muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalPhColors.current
    Column(
        modifier
            .background(c.surface, RoundedCornerShape(16.dp))
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.muted)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text)
    }
}
