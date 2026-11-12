#pragma once

// ==== Connectivity (GlucoTouch-style) ====
// Defaults used until app pushes WiFi/broker over BLE (NVS overrides).
#define WIFI_SSID "ESP"
#define WIFI_PASSWORD "12345678"

// REST API (HTTP heartbeat / fallback ingest) — live Dokploy
#define API_BASE "https://finrado.com"

// MQTT broker on Dokploy VPS (plain TCP — avoids TLS heap issues on ESP32)
#define MQTT_HOST "31.97.187.83"
#define MQTT_PORT 1883

// Device identity (MQTT username = serial, password = token)
#define DEVICE_SERIAL "PET-0001"
#define DEVICE_TOKEN "dev-device-token"
#define PET_ID "11111111-1111-1111-1111-111111111111"

#define PULSE_SENSOR_PIN 34

// 1 = Serial BPM validation only (no cloud)
#define BPM_VALIDATION_MODE 0

// GlucoTouch-like cadence
#define STATUS_INTERVAL_MS 15000
#define MQTT_KEEPALIVE_SEC 20
#define MQTT_HEAL_MS 20000
#define READING_INTERVAL_MS 2000
