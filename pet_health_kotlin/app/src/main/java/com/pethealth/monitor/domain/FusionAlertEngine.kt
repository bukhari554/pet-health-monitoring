package com.pethealth.monitor.domain

import com.pethealth.monitor.data.ActivityClass
import com.pethealth.monitor.data.AlertReason
import com.pethealth.monitor.data.AlertSeverity
import com.pethealth.monitor.data.HealthAlert
import com.pethealth.monitor.data.SensorReading
import java.util.UUID

class FusionAlertEngine {
    private val lastFired = mutableMapOf<AlertReason, Long>()
    private val cooldownMs = 5 * 60 * 1000L

    fun evaluate(r: SensorReading): List<HealthAlert> {
        val out = mutableListOf<HealthAlert>()
        val now = r.timestampMs

        fun maybe(
            reason: AlertReason,
            severity: AlertSeverity,
            desc: String,
            context: String
        ) {
            val last = lastFired[reason]
            if (last != null && now - last < cooldownMs) return
            lastFired[reason] = now
            out += HealthAlert(
                id = UUID.randomUUID().toString(),
                petId = r.petId,
                reason = reason,
                severity = severity,
                description = desc,
                contextNote = context,
                timestampMs = now
            )
        }

        if (r.signalQuality < 0.15f && r.accelEnergy < 0.4f) {
            maybe(
                AlertReason.COLLAR_OFF,
                AlertSeverity.WARNING,
                "No reliable pulse and very low motion — collar may be off or loose.",
                "Activity: ${r.activity.label}, quality: ${(r.signalQuality * 100).toInt()}%"
            )
        }

        if (r.signalQuality in 0.15f..0.44f && !r.artifactRejected) {
            maybe(
                AlertReason.LOW_SIGNAL_QUALITY,
                AlertSeverity.INFO,
                "Pulse signal is weak. Reposition collar for better contact.",
                "Quality ${(r.signalQuality * 100).toInt()}%"
            )
        }

        if (!r.artifactRejected && r.signalQuality >= 0.4f) {
            if (r.isResting && r.heartRateBpm >= 130) {
                maybe(
                    AlertReason.HIGH_RESTING_HR,
                    AlertSeverity.CRITICAL,
                    "Heart rate ${r.heartRateBpm} bpm while ${r.activity.label.lowercase()}.",
                    "Play/walk pe yeh BPM normal ho sakta hai — resting pe nahi."
                )
            } else if (r.activity == ActivityClass.PLAYING && r.heartRateBpm >= 180) {
                maybe(
                    AlertReason.OVER_EXERTION,
                    AlertSeverity.WARNING,
                    "Very high HR (${r.heartRateBpm} bpm) during play — watch for overheating.",
                    "Activity: playing, jerk: ${"%.2f".format(r.jerk)}"
                )
            }

            if (r.heartRateBpm in 1..54) {
                maybe(
                    AlertReason.LOW_HR,
                    AlertSeverity.CRITICAL,
                    "Heart rate dropped to ${r.heartRateBpm} bpm.",
                    "Context: ${r.activity.label}"
                )
            }

            if (r.isResting && r.hrvRmssdMs > 0f && r.hrvRmssdMs < 18f) {
                maybe(
                    AlertReason.LOW_HRV,
                    AlertSeverity.WARNING,
                    "Low HRV (${r.hrvRmssdMs.toInt()} ms) at rest — stress/discomfort hint.",
                    "Early-warning only — not a diagnosis."
                )
            }
        }

        if (r.jerk > 18f || r.accelEnergy > 35f) {
            maybe(
                AlertReason.FALL_SUSPECTED,
                AlertSeverity.CRITICAL,
                "Sudden impact / orientation spike detected from MPU6050.",
                "Jerk ${"%.1f".format(r.jerk)}, energy ${"%.1f".format(r.accelEnergy)}" +
                    if (r.heartRateBpm > 0) ", HR ${r.heartRateBpm}" else ""
            )
        }

        if (r.enclosureTempC >= 34f && r.heartRateBpm >= 140 && !r.isResting) {
            maybe(
                AlertReason.HEAT_STRESS_PROXY,
                AlertSeverity.WARNING,
                "Elevated enclosure temp (${"%.1f".format(r.enclosureTempC)}°C) with high HR.",
                "MPU onboard temp is enclosure proxy — not core body temp."
            )
        }

        return out
    }
}
