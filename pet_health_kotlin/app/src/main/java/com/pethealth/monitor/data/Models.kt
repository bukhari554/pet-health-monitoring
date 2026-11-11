package com.pethealth.monitor.data

enum class ActivityClass(val label: String) {
    SLEEPING("Sleeping"),
    LAYING("Laying"),
    STANDING("Standing"),
    WALKING("Walking"),
    PLAYING("Playing"),
    UNKNOWN("Unknown")
}

data class Pet(
    val id: String,
    val name: String,
    val type: String,
    val breed: String,
    val age: Int
)

data class SensorReading(
    val petId: String,
    val timestampMs: Long,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val enclosureTempC: Float,
    val accelEnergy: Float,
    val gyroEnergy: Float,
    val jerk: Float,
    val heartRateBpm: Int,
    val hrvRmssdMs: Float,
    val signalQuality: Float,
    val restingHeartRate: Int?,
    val artifactRejected: Boolean,
    val activity: ActivityClass,
    val movementCount: Int,
    val layingMinutes: Int,
    val standingMinutes: Int,
    val sleepingMinutes: Int,
    val activeMinutes: Int
) {
    val isResting: Boolean
        get() = activity == ActivityClass.SLEEPING || activity == ActivityClass.LAYING
}

enum class AlertSeverity { INFO, WARNING, CRITICAL }

enum class AlertReason(val title: String) {
    HIGH_RESTING_HR("High resting heart rate"),
    LOW_HR("Low heart rate"),
    LOW_HRV("Low HRV (stress hint)"),
    FALL_SUSPECTED("Possible fall / impact"),
    COLLAR_OFF("Collar off / no contact"),
    OVER_EXERTION("Over-exertion"),
    LOW_SIGNAL_QUALITY("Weak pulse signal"),
    HEAT_STRESS_PROXY("Heat-stress proxy")
}

data class HealthAlert(
    val id: String,
    val petId: String,
    val reason: AlertReason,
    val severity: AlertSeverity,
    val description: String,
    val contextNote: String,
    val timestampMs: Long,
    val isAcknowledged: Boolean = false
)

data class HealthScore(
    val score: Int,
    val label: String,
    val reasons: List<String>,
    val computedAtMs: Long
)

data class DailySummary(
    val dayStartMs: Long,
    val sleepMinutes: Int,
    val activeMinutes: Int,
    val layingMinutes: Int,
    val standingMinutes: Int,
    val awakenings: Int,
    val restlessMinutes: Int,
    val sleepScore: Int,
    val avgBpm: Float,
    val avgRestingBpm: Float,
    val avgHrv: Float,
    val movementCount: Int,
    val alertCount: Int,
    val healthScore: Int
) {
    val sleepLabel: String
        get() = "${sleepMinutes / 60}h ${sleepMinutes % 60}m"
}
