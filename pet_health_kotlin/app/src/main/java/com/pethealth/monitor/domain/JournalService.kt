package com.pethealth.monitor.domain

import com.pethealth.monitor.data.ActivityClass
import com.pethealth.monitor.data.DailySummary
import com.pethealth.monitor.data.SensorReading
import java.util.Calendar

class JournalService {
    fun buildWeek(
        latest: SensorReading,
        openAlertsToday: Int,
        healthScore: Int
    ): List<DailySummary> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val list = mutableListOf<DailySummary>()

        for (i in 6 downTo 1) {
            val day = todayStart - i * 24L * 60 * 60 * 1000
            val seed = ((day / (24L * 60 * 60 * 1000)) % 97).toInt()
            list += DailySummary(
                dayStartMs = day,
                sleepMinutes = 380 + seed % 80,
                activeMinutes = 50 + seed % 40,
                layingMinutes = 90 + seed % 30,
                standingMinutes = 40 + seed % 25,
                awakenings = 1 + seed % 4,
                restlessMinutes = 10 + seed % 20,
                sleepScore = 70 + seed % 25,
                avgBpm = (85 + seed % 20).toFloat(),
                avgRestingBpm = (72 + seed % 12).toFloat(),
                avgHrv = (25 + seed % 15).toFloat(),
                movementCount = 200 + seed * 3,
                alertCount = seed % 3,
                healthScore = 65 + seed % 30
            )
        }

        val restless = latest.activity == ActivityClass.SLEEPING && latest.accelEnergy > 1.2f
        list += DailySummary(
            dayStartMs = todayStart,
            sleepMinutes = latest.sleepingMinutes,
            activeMinutes = latest.activeMinutes,
            layingMinutes = latest.layingMinutes,
            standingMinutes = latest.standingMinutes,
            awakenings = if (latest.sleepingMinutes > 300) 2 else 1,
            restlessMinutes = (latest.accelEnergy * 3).toInt().coerceIn(5, 40),
            sleepScore = sleepScore(latest.sleepingMinutes, restless),
            avgBpm = if (latest.heartRateBpm > 0) latest.heartRateBpm.toFloat() else 88f,
            avgRestingBpm = (latest.restingHeartRate ?: 78).toFloat(),
            avgHrv = if (latest.hrvRmssdMs > 0) latest.hrvRmssdMs else 30f,
            movementCount = latest.movementCount,
            alertCount = openAlertsToday,
            healthScore = healthScore
        )
        return list
    }

    private fun sleepScore(sleepMinutes: Int, restless: Boolean): Int {
        var score = when {
            sleepMinutes >= 480 -> 90
            sleepMinutes >= 400 -> 80
            sleepMinutes >= 320 -> 70
            sleepMinutes >= 240 -> 55
            else -> 40
        }
        if (restless) score -= 10
        return score.coerceIn(0, 100)
    }
}
