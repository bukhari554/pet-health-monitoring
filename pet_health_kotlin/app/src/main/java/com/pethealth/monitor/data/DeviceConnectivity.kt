package com.pethealth.monitor.data

data class DeviceConnectivity(
    val serial: String,
    val online: Boolean,
    val mqttUp: Boolean,
    val cloud: Boolean,
    val lastSeen: String?,
    val wifiRssi: Int?,
    val firmware: String?,
    val ageMs: Long?,
    val graceMs: Long,
    val keepaliveHintSec: Int,
    val mqttKeepaliveSec: Int
)
