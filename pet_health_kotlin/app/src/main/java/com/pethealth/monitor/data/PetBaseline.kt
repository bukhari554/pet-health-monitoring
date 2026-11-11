package com.pethealth.monitor.data

data class PetBaseline(
    val petId: String,
    val restingHrAvg: Double,
    val restingHrStd: Double,
    val sleepMinutesAvg: Double,
    val hrvAvg: Double,
    val samples: Int,
    val highRestingHrThreshold: Int,
    val lowHrvThreshold: Int
)
