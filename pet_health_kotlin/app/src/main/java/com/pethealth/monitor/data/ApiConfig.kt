package com.pethealth.monitor.data

object ApiConfig {
    // Production Dokploy (finrado.com). Local LAN override: Settings → API base URL.
    const val DEFAULT_BASE_URL = "https://finrado.com"
    const val DEFAULT_MQTT_URL = "mqtt://31.97.187.83:1883"
    const val DEFAULT_MQTT_WS_URL = "ws://31.97.187.83:9001/mqtt"
    const val DEVICE_SERIAL = "PET-0001"
    const val DEVICE_TOKEN = "dev-device-token"

    const val BUDDY_ID = "11111111-1111-1111-1111-111111111111"
    const val MILO_ID = "22222222-2222-2222-2222-222222222222"
}
