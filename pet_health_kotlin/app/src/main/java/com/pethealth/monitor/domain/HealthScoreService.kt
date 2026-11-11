package com.pethealth.monitor.domain

import com.pethealth.monitor.data.HealthScore
import com.pethealth.monitor.data.SensorReading

class HealthScoreService {
    fun compute(r: SensorReading, openAlerts: Int): HealthScore {
        var score = 100
        val reasons = mutableListOf<String>()

        if (r.artifactRejected || r.signalQuality < 0.35f) {
            score -= 8
            reasons += "Pulse signal quality is low"
        }

        if (!r.artifactRejected && r.signalQuality >= 0.4f) {
            when {
                r.isResting && r.heartRateBpm >= 130 -> {
                    score -= 25
                    reasons += "High resting heart rate (${r.heartRateBpm} bpm)"
                }
                r.heartRateBpm >= 170 -> {
                    score -= 12
                    reasons += "Very high exertion HR (${r.heartRateBpm} bpm)"
                }
                r.heartRateBpm in 1..54 -> {
                    score -= 30
                    reasons += "Low heart rate (${r.heartRateBpm} bpm)"
                }
            }

            if (r.isResting && r.hrvRmssdMs > 0f && r.hrvRmssdMs < 18f) {
                score -= 10
                reasons += "Low resting HRV"
            }
        }

        if (r.sleepingMinutes < 360) {
            score -= 8
            reasons += "Sleep so far looks short (${r.sleepingMinutes} min tracked)"
        }

        if (r.jerk > 18f) {
            score -= 20
            reasons += "Possible fall/impact spike"
        }

        if (openAlerts > 0) {
            score -= (openAlerts * 5).coerceAtMost(20)
            reasons += "$openAlerts open alert(s)"
        }

        score = score.coerceIn(0, 100)
        val label = when {
            score >= 75 -> "Healthy"
            score >= 50 -> "At Risk"
            else -> "Unhealthy"
        }

        if (reasons.isEmpty()) {
            reasons += "Vitals and activity within expected ranges for context"
        }

        return HealthScore(score, label, reasons, r.timestampMs)
    }
}
